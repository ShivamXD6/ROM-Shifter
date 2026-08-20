package build.bytes.romshifter.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import build.bytes.romshifter.models.FlashAction
import build.bytes.romshifter.models.FlashZip
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale

object FlashManager {

    @SuppressLint("SdCardPath")
    fun getPathFromUri(context: Context, uri: Uri): String? {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            val path = split.getOrNull(1) ?: ""

            if ("primary".equals(type, ignoreCase = true)) {
                return "${Environment.getExternalStorageDirectory().absolutePath}/$path"
            }

            if ("raw".equals(type, ignoreCase = true)) {
                return path
            }

            val externalPath = "/storage/$type/$path"
            val exists = Shell.cmd("[ -e '$externalPath' ]").exec().isSuccess
            if (exists) return externalPath
        }

        try {
            val path = uri.path
            if (path != null && (path.startsWith("/storage/") || path.startsWith("/sdcard"))) {
                return path
            }
        } catch (_: Exception) {
        }

        return null
    }

    suspend fun processZips(
        uris: List<Uri>,
        currentZips: List<FlashZip>,
        append: Boolean,
        context: Context
    ): List<FlashZip> = coroutineScope {
        val zips = if (append) currentZips.toMutableList() else mutableListOf()

        val deferredZips = uris.map { uri ->
            async(Dispatchers.IO) {
                val path = getPathFromUri(context, uri) ?: return@async null
                if (zips.any { it.path == path }) return@async null

                val name = path.substringAfterLast("/")
                val nameLower = name.lowercase(Locale.ROOT)
                val contents =
                    Shell.cmd("unzip -l '$path'").exec().out.joinToString("\n")

                val hasModuleProp = contents.contains("module.prop", ignoreCase = true)
                val hasUpdateBinary =
                    contents.contains(
                        Regex(
                            "META-INF/.*update-binary|META-INF/.*updater-script",
                            RegexOption.IGNORE_CASE
                        )
                    )
                var isGapps = false
                var isAddon = false

                if (nameLower.contains(Regex("gapps|nikgapps|bitgapps|mindthegapps"))) {
                    isGapps = true; if (nameLower.contains("addon")) isAddon = true
                } else if (contents.contains(Regex("bitgapps|nikgapps|mindthegapps|busybox-arm|util_functions\\.sh"))) {
                    isGapps = true; isAddon = true
                }

                val category: String? = when {
                    isGapps -> if (isAddon) "Addon" else "GApps"
                    hasModuleProp -> null
                    contents.contains(Regex("firmware-update", RegexOption.IGNORE_CASE)) ||
                            contents.contains(
                                Regex(
                                    "abl\\.elf|xbl\\.elf|tz\\.mbn|devcfg\\.mbn",
                                    RegexOption.IGNORE_CASE
                                )
                            ) -> "Firmware"

                    contents.contains(
                        Regex(
                            "payload\\.bin|system\\.new\\.dat|system\\.transfer\\.list|apex_info\\.pb|care_map\\.pb|payload_properties\\.txt|META-INF/com/android/metadata",
                            RegexOption.IGNORE_CASE
                        )
                    ) -> "ROM"
                    contents.contains(
                        Regex(
                            "anykernel|zimage|image\\.gz",
                            RegexOption.IGNORE_CASE
                        )
                    ) ||
                            nameLower.contains(Regex("kernel|perf|stormbreaker|eas")) -> "Kernel"

                    contents.contains(
                        Regex(
                            "recovery\\.img|ramdisk-recovery\\.img|ramdisk-recovery\\.cpio|magiskboot|PBRP|orangefox|twrp",
                            RegexOption.IGNORE_CASE
                        )
                    ) -> "Recovery"
                    hasUpdateBinary -> "Other"
                    else -> null
                }

                if (category != null) FlashZip(name, path, category) else null
            }
        }

        zips.addAll(deferredZips.awaitAll().filterNotNull())

        val order = listOf("Firmware", "Recovery", "ROM", "GApps", "Addon", "Kernel", "Other")
        zips.sortedWith(compareBy<FlashZip> {
            val index = order.indexOf(it.category); if (index == -1) 99 else index
        }.thenBy { it.name })
    }

    fun checkLockscreen(): Boolean = !Shell.cmd("locksettings verify").exec().isSuccess

    @SuppressLint("SdCardPath")
    fun generateOrsAndProceed(actions: List<FlashAction>, rebootOption: String) {
        val script = StringBuilder()

        actions.forEach { action ->
            when (action) {
                is FlashAction.Wipe -> {
                    action.partitions.forEach { script.append("wipe $it\n") }
                }

                is FlashAction.InstallZip -> {
                    var path = action.zip.path
                    if (path.startsWith("/storage/emulated/0")) {
                        path = path.replace("/storage/emulated/0", "/sdcard")
                    }
                    script.append("install $path\n")
                }

                is FlashAction.FormatData -> {
                    script.append("format data\n")
                }
            }
        }

        val scriptContent = script.toString().replace("'", "'\\''")
        Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh --ors '$scriptContent' '$rebootOption'")
            .exec()
    }

    fun restartFlashWizard() {
        val locations = listOf("/cache/recovery", "/data/cache/recovery", "/metadata/recovery")
        val shellCommand =
            "mount -o rw,remount /cache 2>/dev/null; mount -o rw,remount /metadata 2>/dev/null; rm -f ${
                locations.joinToString(
                    " "
                ) { "$it/openrecoveryscript" }
            } 2>/dev/null"
        Shell.cmd(shellCommand).exec()
    }

    fun executeFlashNow() {
        Shell.cmd("sync; reboot recovery").exec()
    }

    fun getAllPartitions(): List<String> {
        return Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh --get-partitions")
            .exec().out
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .sorted()
    }

    fun getBackedUpImages(savedPath: String): List<String> {
        return Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh --get-images '$savedPath'")
            .exec().out
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun deleteLivePartitionImage(savedPath: String, imgName: String) {
        Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh --delete-image '$savedPath' '$imgName'")
            .exec()
    }

    fun runLiveOperation(action: String, partition: String, customPath: String?, savedPath: String) {
        if (action == "--live-backup") {
            Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh $action '$partition' '$savedPath'")
                .exec()
        } else {
            val imgPath = customPath ?: "$savedPath/Partitions/${partition}_backup.img"
            Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh $action '$partition' '$imgPath'")
                .exec()
        }
    }
}
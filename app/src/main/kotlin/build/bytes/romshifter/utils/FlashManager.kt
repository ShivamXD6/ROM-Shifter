package build.bytes.romshifter.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import build.bytes.romshifter.models.FlashZip
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale

object FlashManager {

    fun getPathFromUri(context: Context, uri: Uri): String? {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            if ("primary".equals(split[0], ignoreCase = true)) return "${Environment.getExternalStorageDirectory().absolutePath}/${split[1]}"
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
                    Shell.cmd("su -mm -c \"unzip -l '$path'\"").exec().out.joinToString("\n")

                val hasModuleProp = contents.contains("module.prop", ignoreCase = true)
                val hasUpdateBinary =
                    contents.contains(Regex("META-INF/.*update-binary", RegexOption.IGNORE_CASE))
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
                    contents.contains(Regex("firmware-update/|abl\\.elf|xbl\\.elf|tz\\.mbn")) -> "Firmware"
                    contents.contains(Regex("payload\\.bin|system\\.new\\.dat|system\\.transfer\\.list")) -> "ROM"
                    contents.contains(Regex("anykernel|zimage|image\\.gz")) || nameLower.contains(
                        Regex("kernel|perf|stormbreaker|eas")
                    ) -> "Kernel"

                    hasUpdateBinary -> "Other"
                    else -> null
                }

                if (category != null) FlashZip(name, path, category) else null
            }
        }

        zips.addAll(deferredZips.awaitAll().filterNotNull())
        
        val order = listOf("Firmware", "ROM", "GApps", "Addon", "Kernel", "Other")
        zips.sortedWith(compareBy<FlashZip> {
            val index = order.indexOf(it.category); if (index == -1) 99 else index
        }.thenBy { it.name })
    }

    fun checkLockscreen(): Boolean = !Shell.cmd("su -mm -c 'locksettings verify'").exec().isSuccess

    fun generateOrsAndProceed(wipePartitions: Set<String>, formatData: Boolean, zips: List<FlashZip>) {
        val script = java.lang.StringBuilder()

        wipePartitions.forEach { script.append("wipe $it\n") }

        zips.forEach { script.append("install ${it.path}\n") }

        if (formatData) { script.append("format data\n") }
        script.append("reboot system\n")

        val safeScript = script.toString().replace("'", "'\\''")

        val shellCommand = "su -mm -c \"mkdir -p /cache/recovery /data/cache/recovery 2>/dev/null; " +
                "echo '$safeScript' > /cache/recovery/openrecoveryscript 2>/dev/null; " +
                "echo '$safeScript' > /data/cache/recovery/openrecoveryscript 2>/dev/null; " +
                "chmod 666 /cache/recovery/openrecoveryscript /data/cache/recovery/openrecoveryscript 2>/dev/null\""
        Shell.cmd(shellCommand).exec()
    }

    fun restartFlashWizard() { Shell.cmd("su -mm -c \"rm -f /cache/recovery/openrecoveryscript /data/cache/recovery/openrecoveryscript 2>/dev/null\"").exec() }

    fun executeFlashNow() { Shell.cmd("su -mm -c \"sync; reboot recovery\"").exec() }

    fun getAllPartitions(): List<String> {
        val paths =
            listOf("/dev/block/by-name", "/dev/block/bootdevice/by-name", "/dev/block/mapper")
        val blocked = listOf(
            "system",
            "system_ext",
            "super",
            "vendor",
            "product",
            "odm",
            "userdata",
            "metadata",
            "persist",
            "control"
        )
        val allFound = mutableListOf<String>()
        for (path in paths) {
            val out = Shell.cmd("su -c \"ls -1p $path 2>/dev/null | grep -v /\"").exec().out
            allFound.addAll(out.map { it.trim() }
                .filter { it.isNotBlank() && !blocked.any { b -> it == b || it.startsWith("${b}_") } })
        }
        return allFound.distinct().sorted()
    }

    fun getBackedUpImages(savedPath: String): List<String> {
        return Shell.cmd("su -c \"ls -1p '$savedPath/Partitions/' 2>/dev/null | grep -v / | grep '\\.img$'\"")
            .exec().out
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun deleteLivePartitionImage(savedPath: String, imgName: String) {
        Shell.cmd("su -c \"rm -f '$savedPath/Partitions/$imgName'\"").exec()
    }

    fun runLiveOperation(action: String, partition: String, customPath: String?, savedPath: String) {
        if (action == "--live-backup") {
            Shell.cmd("su -mm -c \"sh /data/adb/Shifter/ROM-Shifter.sh $action '$partition' '$savedPath'\"")
                .exec()
        } else {
            val imgPath = customPath ?: "$savedPath/Partitions/${partition}_backup.img"
            Shell.cmd("su -mm -c \"sh /data/adb/Shifter/ROM-Shifter.sh $action '$partition' '$imgPath'\"")
                .exec()
        }
    }
}
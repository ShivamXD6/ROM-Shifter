package build.bytes.romshifter.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import build.bytes.romshifter.models.FlashZip
import com.topjohnwu.superuser.Shell
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

    fun processZips(uris: List<Uri>, currentZips: List<FlashZip>, append: Boolean, context: Context): List<FlashZip> {
        val zips = if (append) currentZips.toMutableList() else mutableListOf()
        for (uri in uris) {
            val path = getPathFromUri(context, uri) ?: continue
            if (zips.any { it.path == path }) continue
            val name = path.substringAfterLast("/")
            val nameLower = name.lowercase(Locale.ROOT)
            val contents = Shell.cmd("su -mm -c \"unzip -l '$path'\"").exec().out.joinToString("\n")

            val hasModuleProp = contents.contains("module.prop", ignoreCase = true)
            val hasUpdateBinary = contents.contains(Regex("META-INF/.*update-binary", RegexOption.IGNORE_CASE))
            var isGapps = false; var isAddon = false

            if (nameLower.contains(Regex("gapps|nikgapps|bitgapps|mindthegapps"))) { isGapps = true; if (nameLower.contains("addon")) isAddon = true }
            else if (contents.contains(Regex("bitgapps|nikgapps|mindthegapps|busybox-arm|util_functions\\.sh"))) { isGapps = true; isAddon = true }

            val category: String? = when {
                isGapps -> if (isAddon) "Addon" else "GApps"
                hasModuleProp -> null
                contents.contains(Regex("firmware-update/|abl\\.elf|xbl\\.elf|tz\\.mbn")) -> "Firmware"
                contents.contains(Regex("payload\\.bin|system\\.new\\.dat|system\\.transfer\\.list")) -> "ROM"
                contents.contains(Regex("anykernel|zimage|image\\.gz")) || nameLower.contains(Regex("kernel|perf|stormbreaker|eas")) -> "Kernel"
                hasUpdateBinary -> "Other"
                else -> null
            }

            if (category != null) zips.add(FlashZip(name, path, category))
        }
        val order = listOf("Firmware", "ROM", "GApps", "Addon", "Kernel", "Other")
        return zips.sortedWith(compareBy<FlashZip> { val index = order.indexOf(it.category); if (index == -1) 99 else index }.thenBy { it.name })
    }

    fun checkLockscreen(): Boolean = !Shell.cmd("su -mm -c 'locksettings verify'").exec().isSuccess

    fun generateOrsAndProceed(wipePartitions: Set<String>, formatData: Boolean, zips: List<FlashZip>) {
        val script = java.lang.StringBuilder()

        wipePartitions.forEach { script.append("wipe $it\n") }

        zips.forEach { script.append("install ${it.path}\n") }

        if (formatData) { script.append("format data\n") }
        script.append("reboot system\n")

        val safeScript = script.toString().replace("'", "'\\''")
        Shell.cmd("su -mm -c \"mkdir -p /cache/recovery && echo '$safeScript' > /cache/recovery/openrecoveryscript && chmod 666 /cache/recovery/openrecoveryscript\"").exec()
    }

    fun restartFlashWizard() { Shell.cmd("su -mm -c \"rm -f /cache/recovery/openrecoveryscript\"").exec() }
    fun executeFlashNow() { Shell.cmd("su -mm -c \"sync; reboot recovery\"").exec() }

    fun getAllPartitions(): List<String> {
        val paths = listOf("/dev/block/by-name", "/dev/block/bootdevice/by-name")
        val blockedPartitions = listOf("system", "system_ext", "vendor", "product", "odm", "super", "userdata", "metadata", "persist")
        for (path in paths) {
            val out = Shell.cmd("su -c ls -1 $path").exec().out
            if (out.isNotEmpty() && !out[0].contains("No such file")) {
                return out.asSequence().filter { it.isNotBlank() }.map { it.replace(Regex("_[ab]$"), "") }.filterNot { blockedPartitions.contains(it) }.distinct().sorted().toList()
            }
        }
        return emptyList()
    }

    fun getBackedUpImages(savedPath: String): List<String> {
        return Shell.cmd("su -c \"ls -1 '$savedPath/Live-Partition/' | grep '\\.img$'\"").exec().out.filter { it.isNotBlank() }
    }

    fun deleteLivePartitionImage(savedPath: String, imgName: String) {
        Shell.cmd("su -c \"rm -f '$savedPath/Live-Partition/$imgName'\"").exec()
    }

    fun runLiveOperation(action: String, partition: String, customPath: String?, savedPath: String) {
        val arg2 = if (action == "--live-restore") customPath ?: "$savedPath/Live-Partition/${partition}_backup.img" else ""
        Shell.cmd("su -mm -c \"sh /data/adb/#Shifter/ROM-Shifter.sh $action '$savedPath' '$partition' '$arg2'\"").exec()
    }
}
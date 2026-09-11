package build.bytes.romshifter.utils

import android.content.SharedPreferences
import android.os.Environment
import androidx.core.content.edit
import com.topjohnwu.superuser.Shell

object SettingsManager {

    fun getDefaultPath(): String {
        return "${Environment.getExternalStorageDirectory().absolutePath}/Shifter"
    }

    fun migrateFolder(oldPath: String, newPath: String, prefs: SharedPreferences) {
        if (oldPath != newPath && oldPath.isNotBlank() && newPath.isNotBlank()) {
            Shell.cmd("mkdir -p '$newPath' && touch '$newPath/.shifter_dir'").exec()
            Shell.cmd("mv '$oldPath'/* '$newPath'/ 2>/dev/null; rm -rf '$oldPath'").exec()
            prefs.edit { putString("base_path", newPath) }
        }
    }

    fun autoDetectFolder(prefs: SharedPreferences): String? {
        val out = Shell.cmd("find /storage /data/media/0 /mnt/media_rw -maxdepth 5 -type f -name '.shifter_dir' 2>/dev/null | head -n 1").exec().out.joinToString("").trim()
        if (out.isNotEmpty()) {
            val detectedPath = out.substringBeforeLast("/")
            prefs.edit { putString("base_path", detectedPath) }
            return detectedPath
        }
        return null
    }

    fun resolveDocumentPath(docId: String): String {
        val parts = docId.split(":")
        val volumeId = parts[0]
        val basePath = Environment.getExternalStorageDirectory().absolutePath

        val resolved = when {
            "primary".equals(volumeId, true) -> {
                val relativePath = parts.getOrNull(1) ?: ""
                "$basePath/$relativePath"
            }

            volumeId.matches(Regex("[0-9A-F]{4}-[0-9A-F]{4}")) -> {
                val relativePath = parts.getOrNull(1) ?: ""
                "/storage/$volumeId/$relativePath"
            }

            parts.any { it.startsWith("/") } -> {
                parts.find { it.startsWith("/") }!!
            }

            else -> {
                if (parts.size > 1) "/storage/$volumeId/${parts[1]}" else "/storage/$volumeId"
            }
        }

        val cleanPath = resolved.replace("//", "/")
        return if (cleanPath.endsWith("Shifter")) cleanPath
        else if (cleanPath.endsWith("/")) "${cleanPath}Shifter"
        else "$cleanPath/Shifter"
    }

    fun getStorageRoot(path: String): String {
        val basePath = Environment.getExternalStorageDirectory().absolutePath
        return when {
            path.startsWith(basePath) || path.startsWith("/storage/emulated") -> "internal"
            path.startsWith("/data") -> "data"
            path.startsWith("/storage/") -> path.split("/").getOrNull(2) ?: "other"
            else -> "other"
        }
    }
}

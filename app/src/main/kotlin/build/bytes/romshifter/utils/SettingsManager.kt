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
}
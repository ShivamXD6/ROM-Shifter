package build.bytes.romshifter.utils

import android.content.Context
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BackendInstaller {
    suspend fun installEngine(context: Context): Boolean = withContext(Dispatchers.IO) {
        val targetDir = "/data/adb/#Shifter"
        val scriptName = "ROM-Shifter.sh"
        val cacheDir = context.cacheDir

        val nativeLibFile = File(context.applicationInfo.nativeLibraryDir, "libzapdos.so")

        if (!nativeLibFile.exists()) {
            return@withContext false
        }

        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

            val prefs = context.getSharedPreferences("shifter_backend_prefs", Context.MODE_PRIVATE)
            val savedVersionCode = prefs.getLong("installed_version", -1L)

            val check = Shell.cmd("[ -x '$targetDir/zapdos' ] && [ -x '$targetDir/$scriptName' ] && echo YES")
                .exec().out.joinToString("").trim()

            if (check == "YES" && savedVersionCode == currentVersionCode) {
                return@withContext true
            }

            val outScript = File(cacheDir, scriptName)
            context.assets.open(scriptName).use { inputStream ->
                outScript.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val commands = arrayOf(
                "rm -rf '$targetDir'",
                "mkdir -p '$targetDir'",
                "cp '${outScript.absolutePath}' '$targetDir/$scriptName'",
                "cp '${nativeLibFile.absolutePath}' '$targetDir/zapdos'",
                "chmod +x '$targetDir/$scriptName'",
                "chmod +x '$targetDir/zapdos'"
            )

            val result = Shell.cmd(*commands).exec()

            if (result.isSuccess) {
                prefs.edit { putLong("installed_version", currentVersionCode) }
                return@withContext true
            } else {
                return@withContext false
            }

        } catch (_: Exception) {
            return@withContext false
        } finally {
            File(cacheDir, scriptName).delete()
        }
    }
}
package build.bytes.romshifter.utils

import android.content.Context
import androidx.core.content.edit
import build.bytes.romshifter.BuildConfig
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BackendInstaller {

    private fun getFileMd5(file: File): String? {
        if (!file.exists()) return null
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    md.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("SameParameterValue")
    private fun getAssetMd5(context: Context, assetName: String): String? {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            context.assets.open(assetName).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    md.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun installEngine(context: Context): Boolean = withContext(Dispatchers.IO) {
        val targetDir = "/data/adb/Shifter"
        val scriptName = "ROM-Shifter.sh"
        val zapdosName = "zapdos"
        val cacheDir = context.cacheDir

        val deviceLibFile = File(context.applicationInfo.nativeLibraryDir, "libzapdos.so")
        if (!deviceLibFile.exists()) return@withContext false

        try {
            val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
            val prefs = context.getSharedPreferences("shifter_backend_prefs", Context.MODE_PRIVATE)
            val savedVersionCode = prefs.getLong("installed_version", -1L)

            val scriptFile = File(targetDir, scriptName)
            val zapdosFile = File(targetDir, zapdosName)

            val filesExist =
                Shell.cmd("[ -x '$scriptFile' ] && [ -x '$zapdosFile' ]").exec().isSuccess

            val assetMd5 = getAssetMd5(context, scriptName)
            val diskMd5 = getFileMd5(scriptFile)
            val scriptIntact = assetMd5 != null && assetMd5 == diskMd5

            val zapdosIntact = deviceLibFile.length() == zapdosFile.length()

            if (filesExist && scriptIntact && zapdosIntact && savedVersionCode == currentVersionCode) {
                return@withContext true
            }

            val outScript = File(cacheDir, scriptName)
            context.assets.open(scriptName).use { input ->
                outScript.outputStream().use { output -> input.copyTo(output) }
            }

            val commands = arrayOf(
                "mkdir -p '$targetDir'",
                "cp '${outScript.absolutePath}' '$scriptFile'",
                "cp '${deviceLibFile.absolutePath}' '$zapdosFile'",
                "chmod 755 '$targetDir'",
                "chmod 755 '$scriptFile'",
                "chmod 755 '$zapdosFile'",
                "chown -R root:root '$targetDir' 2>/dev/null"
            )

            val result = Shell.cmd(*commands).exec()

            if (result.isSuccess) {
                prefs.edit { putLong("installed_version", currentVersionCode) }
                return@withContext true
            }
        } catch (_: Exception) {
        } finally {
            File(cacheDir, scriptName).delete()
        }
        return@withContext false
    }
}

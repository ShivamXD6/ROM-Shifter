package build.bytes.romshifter.utils

import android.content.Context
import androidx.core.content.edit
import build.bytes.romshifter.BuildConfig
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object BackendInstaller {

    private fun java.io.InputStream.md5(): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            var bytesRead = this.read(buffer)
            while (bytesRead != -1) {
                md.update(buffer, 0, bytesRead)
                bytesRead = this.read(buffer)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun getFileMd5(file: File): String? {
        if (!file.exists()) return null
        return file.inputStream().use { it.md5() }
    }

    suspend fun backupSelf(context: Context, shifterPath: String) = withContext(Dispatchers.IO) {
        try {
            val sourceApk = File(context.applicationInfo.sourceDir)
            if (!sourceApk.exists()) return@withContext

            val prefs = context.getSharedPreferences("shifter_backup_prefs", Context.MODE_PRIVATE)
            val savedHash = prefs.getString("self_apk_hash", "")
            val currentHash = getFileMd5(sourceApk) ?: ""

            if (currentHash.isNotEmpty() && currentHash != savedHash) {
                val targetDir = File(shifterPath).apply { mkdirs() }
                val targetApk = File(targetDir, "ROM-Shifter.apk")

                Shell.cmd("cp \"${sourceApk.absolutePath}\" \"${targetApk.absolutePath}\" && chmod 644 \"${targetApk.absolutePath}\"")
                    .exec()

                prefs.edit { putString("self_apk_hash", currentHash) }
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("SameParameterValue")
    private fun getAssetMd5(context: Context, assetName: String): String? {
        return try {
            context.assets.open(assetName).use { it.md5() }
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
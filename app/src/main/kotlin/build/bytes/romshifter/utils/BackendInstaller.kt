package build.bytes.romshifter.utils

import android.content.Context
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
            val check = Shell.cmd("[ -x '$targetDir/zapdos' ] && [ -x '$targetDir/$scriptName' ] && echo YES")
                .exec().out.joinToString("").trim()

            if (check == "YES") return@withContext true

            val outScript = File(cacheDir, scriptName)
            context.assets.open(scriptName).use { inputStream ->
                outScript.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val commands = arrayOf(
                "mkdir -p '$targetDir'",
                "cp '${outScript.absolutePath}' '$targetDir/$scriptName'",
                "cp '${nativeLibFile.absolutePath}' '$targetDir/zapdos'",
                "chmod +x '$targetDir/$scriptName'",
                "chmod +x '$targetDir/zapdos'"
            )

            val result = Shell.cmd(*commands).exec()
            return@withContext result.isSuccess

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            File(cacheDir, scriptName).delete()
        }
    }
}
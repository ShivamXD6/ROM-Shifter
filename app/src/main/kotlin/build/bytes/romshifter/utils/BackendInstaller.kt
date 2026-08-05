package build.bytes.romshifter.utils

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BackendInstaller {
    suspend fun installEngine(context: Context): Boolean = withContext(Dispatchers.IO) {
        val targetDir = "/data/adb/#Shifter"
        val filesToCopy = listOf("ROM-Shifter.sh", "zapdos")
        val cacheDir = context.cacheDir

        try {
            for (fileName in filesToCopy) {
                val outFile = File(cacheDir, fileName)
                context.assets.open(fileName).use { inputStream ->
                    outFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            val cmdList = mutableListOf("mkdir -p '$targetDir'")
            for (fileName in filesToCopy) {
                val cachedFilePath = File(cacheDir, fileName).absolutePath
                cmdList.add("cp '$cachedFilePath' '$targetDir/$fileName'")
                cmdList.add("chmod +x '$targetDir/$fileName'")
            }

            val fullCmd = cmdList.joinToString(" && ")
            val result = Shell.cmd("su -c \"$fullCmd\"").exec()

            return@withContext result.isSuccess

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            filesToCopy.forEach { File(cacheDir, it).delete() }
        }
    }
}
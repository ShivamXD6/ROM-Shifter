package build.bytes.romshifter.utils

import build.bytes.romshifter.models.ShifterEvent
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object ShellEngine {

    /**
     * Executes a root shell command and emits parsed ShifterEvents in real-time.
     */
    fun executeShifterCommand(command: String): Flow<ShifterEvent> = callbackFlow {

        // CallbackList intercepts each line of stdout as it happens
        val outputList = object : CallbackList<String>() {
            override fun onAddElement(line: String) {
                // Parse the line and send it to the Flow
                trySend(parseLine(line))
            }
        }

        // Submit the command asynchronously
        Shell.cmd(command)
            .to(outputList, outputList) // Route both stdout and stderr to our list
            .submit {
                // This block runs when the shell command finishes completely
                close()
            }

        // Suspend until the Flow collector is cancelled or the shell job finishes
        awaitClose { }
    }

    /**
     * Parses the pipe-delimited strings from the bash script into Kotlin Data Classes
     */
    private fun parseLine(line: String): ShifterEvent {
        if (!line.contains("|")) return ShifterEvent.RawLog(line)

        // Convert "ACTION:BACKUP_START|PKG:com.whatsapp" into a Map
        val parts = line.split("|").associate { segment ->
            val kv = segment.split(":", limit = 2)
            if (kv.size == 2) kv[0] to kv[1] else segment to ""
        }

        val action = parts["ACTION"]
        val info = parts["INFO"]

        return when {
            action == "BACKUP_START" -> ShifterEvent.BackupProgress(
                pkg = parts["PKG"] ?: "",
                label = parts["LABEL"] ?: "",
                current = parts["CUR"]?.toIntOrNull() ?: 0,
                total = parts["TOT"]?.toIntOrNull() ?: 0,
                percent = parts["PCT"]?.toIntOrNull() ?: 0,
                size = parts["SIZE"] ?: ""
            )
            info == "STEP" -> ShifterEvent.InfoStep(
                msg = parts["MSG"] ?: ""
            )
            action == "GLOBAL_DONE" -> ShifterEvent.GlobalDone(
                totalKb = parts["TOTAL"] ?: "0",
                timeSec = parts["TIME"] ?: "0"
            )
            action == "FETCH_DONE" -> ShifterEvent.FetchDone(
                file = parts["FILE"] ?: ""
            )
            // Catch-all for ERROR:TAMPER, ERROR:ROOT, etc.
            else -> ShifterEvent.RawLog(line)
        }
    }
}
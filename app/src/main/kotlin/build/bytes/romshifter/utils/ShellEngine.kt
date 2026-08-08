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

        val outputList = object : CallbackList<String>() {
            override fun onAddElement(line: String) {
                trySend(parseLine(line))
            }
        }

        Shell.cmd(command)
            .to(outputList, outputList)
            .submit {
                close()
            }

        awaitClose { }
    }

    private fun parseLine(line: String): ShifterEvent {
        if (!line.contains("|")) return ShifterEvent.RawLog(line)

        val parts = line.split("|").associate { segment ->
            val kv = segment.split(":", limit = 2)
            if (kv.size == 2) kv[0] to kv[1] else segment to ""
        }

        val action = parts["ACTION"]
        val info = parts["INFO"]

        return when {
            action == "BACKUP_START" || action == "RESTORE_START" -> ShifterEvent.BackupProgress(
                pkg = parts["PKG"] ?: "",
                label = parts["LABEL"] ?: "",
                current = parts["CUR"]?.toIntOrNull() ?: 0,
                total = parts["TOT"]?.toIntOrNull() ?: 0,
                percent = parts["PCT"]?.toIntOrNull() ?: 0,
                size = parts["SIZE"] ?: "",
            )
            action == "BACKUP_DONE" || action == "RESTORE_DONE" -> ShifterEvent.BackupDone(
                pkg = parts["PKG"] ?: ""
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
            else -> ShifterEvent.RawLog(line)
        }
    }
}
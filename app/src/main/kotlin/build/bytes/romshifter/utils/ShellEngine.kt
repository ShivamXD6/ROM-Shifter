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
        if (!line.startsWith("ACTION:") && !line.contains("|INFO:STEP|")) return ShifterEvent.RawLog(
            line
        )

        val partsMap = mutableMapOf<String, String>()
        var start = 0
        while (start < line.length) {
            val end = line.indexOf('|', start).let { if (it == -1) line.length else it }
            val segment = line.substring(start, end)
            val colonIdx = segment.indexOf(':')
            if (colonIdx != -1) {
                val key = segment.substring(0, colonIdx)
                val value = segment.substring(colonIdx + 1)
                partsMap[key] = value
            }
            start = end + 1
        }

        val action = partsMap["ACTION"]
        val info = partsMap["INFO"]

        return when {
            action == "BACKUP_START" || action == "RESTORE_START" -> ShifterEvent.BackupProgress(
                pkg = partsMap["PKG"] ?: "",
                label = partsMap["LABEL"] ?: "",
                current = partsMap["CUR"]?.toIntOrNull() ?: 0,
                total = partsMap["TOT"]?.toIntOrNull() ?: 0,
                percent = partsMap["PCT"]?.toIntOrNull() ?: 0,
                size = partsMap["SIZE"] ?: "",
            )
            info == "STEP" -> ShifterEvent.InfoStep(
                msg = partsMap["MSG"] ?: ""
            )
            action == "GLOBAL_DONE" -> ShifterEvent.GlobalDone(
                totalKb = partsMap["TOTAL"] ?: "0",
                timeSec = partsMap["TIME"] ?: "0"
            )
            action == "FETCH_DONE" -> ShifterEvent.FetchDone(
                file = partsMap["FILE"] ?: ""
            )
            else -> ShifterEvent.RawLog(line)
        }
    }
}
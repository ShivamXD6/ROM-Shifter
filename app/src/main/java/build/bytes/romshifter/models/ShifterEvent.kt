package build.bytes.romshifter.models

sealed class ShifterEvent {
    // For: ACTION:BACKUP_START|PKG:...|LABEL:...|VER:...|CUR:...|TOT:...|PCT:...|SIZE:...
    data class BackupProgress(
        val pkg: String,
        val label: String,
        val current: Int,
        val total: Int,
        val percent: Int,
        val size: String
    ) : ShifterEvent()

    // For: INFO:STEP|MSG:...
    data class InfoStep(val msg: String) : ShifterEvent()

    // For: ACTION:GLOBAL_DONE|TOTAL:...|TIME:...
    data class GlobalDone(val totalKb: String, val timeSec: String) : ShifterEvent()

    // For: ACTION:FETCH_DONE|FILE:...
    data class FetchDone(val file: String) : ShifterEvent()

    // Fallback for errors, random shell output, or tamper warnings
    data class RawLog(val line: String) : ShifterEvent()
}
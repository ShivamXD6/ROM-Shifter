package build.bytes.romshifter.models

sealed class ShifterEvent {
    data class BackupProgress(
        val pkg: String,
        val label: String,
        val current: Int,
        val total: Int,
        val percent: Int,
        val size: String
    ) : ShifterEvent()

    data class InfoStep(val msg: String) : ShifterEvent()

    data class GlobalDone(val totalKb: String, val timeSec: String) : ShifterEvent()

    data class FetchDone(val file: String) : ShifterEvent()

    data class RawLog(val line: String) : ShifterEvent()
}
package build.bytes.romshifter.models

import androidx.compose.runtime.Stable

@Stable
data class BackupItem(
    val packageName: String,
    val label: String,
    val progress: Float = 0f,
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val status: String = "Pending",
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false
)

data class BackupUiState(
    val isRunning: Boolean = false,
    val overallProgress: Float = 0f,
    val items: List<BackupItem> = emptyList(),
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val totalBytesProcessed: Long = 0L,
    val totalBytesBatch: Long = 0L,
    val currentAction: String = "Ready"
)

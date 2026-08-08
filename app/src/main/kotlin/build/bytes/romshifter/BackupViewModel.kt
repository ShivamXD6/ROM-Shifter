package build.bytes.romshifter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import build.bytes.romshifter.models.*
import build.bytes.romshifter.utils.ShellEngine
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState = _uiState.asStateFlow()

    private val semaphore = Semaphore(4)
    private val totalBytesProcessed = AtomicLong(0L)

    fun startBackup(selectedApps: List<AppInfo>, comps: Set<Int>, currentPath: String) {
        if (_uiState.value.isRunning) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRunning = true, currentAction = "Calculating sizes...", overallProgress = 0f, completedCount = 0) }
            totalBytesProcessed.set(0L)

            // Step 1: Pre-calculate sizes for all selected apps to support byte-based progress
            val appsWithSizes = calculateAppSizes(selectedApps, comps)
            val totalBatchBytes = appsWithSizes.sumOf { it.totalBytes }

            if (totalBatchBytes == 0L && selectedApps.isNotEmpty()) {
                // Fallback to app-count based if sizes are unavailable but apps are selected
                runAppCountBasedBackup(selectedApps, comps, currentPath)
                return@launch
            } else if (selectedApps.isEmpty()) {
                _uiState.update { it.copy(isRunning = false, currentAction = "No apps selected") }
                return@launch
            }

            val initialItems = appsWithSizes.map { app ->
                BackupItem(
                    packageName = app.appInfo.packageName,
                    label = app.appInfo.label,
                    totalBytes = app.totalBytes,
                    status = "Pending"
                )
            }

            _uiState.update {
                it.copy(
                    items = initialItems,
                    totalCount = appsWithSizes.size,
                    totalBytesBatch = totalBatchBytes,
                    currentAction = "Backing up apps..."
                )
            }

            // Step 2: Launch parallel jobs with Semaphore
            appsWithSizes.forEach { appWithSize ->
                launch {
                    semaphore.withPermit {
                        runSingleBackup(appWithSize, comps, currentPath, totalBatchBytes)
                    }
                }
            }
        }
    }

    private suspend fun runSingleBackup(
        appWithSize: AppWithSize,
        comps: Set<Int>,
        currentPath: String,
        totalBatchBytes: Long
    ) = withContext(Dispatchers.IO) {
        val app = appWithSize.appInfo
        val pkg = app.packageName
        val label = app.label
        val version = app.version
        val type = if (app.isSystem) "System" else "User"
        val compsString = comps.sorted().joinToString(" ")

        val cacheDir = getApplication<Application>().cacheDir
        val targetFile = File(cacheDir, "targets_$pkg.txt")
        targetFile.writeText("$pkg|$label|$version|$type\n")

        val targetPath = targetFile.absolutePath
        val command = "su -mm -c \"SHIFTER_TARGETS='$targetPath' sh /data/adb/#Shifter/ROM-Shifter.sh --backup '$compsString' '$currentPath'\""

        var lastProcessedForThisApp = 0L

        ShellEngine.executeShifterCommand(command).collect { event ->
            when (event) {
                is ShifterEvent.BackupProgress -> {
                    val appProgress = event.percent / 100f
                    val currentAppProcessedBytes = (appWithSize.totalBytes * appProgress).toLong()
                    val delta = currentAppProcessedBytes - lastProcessedForThisApp
                    
                    if (delta > 0) {
                        totalBytesProcessed.addAndGet(delta)
                        lastProcessedForThisApp = currentAppProcessedBytes
                        updateGlobalProgress(totalBatchBytes)
                    }

                    _uiState.update { state ->
                        state.copy(items = state.items.map {
                            if (it.packageName == pkg) it.copy(
                                progress = appProgress,
                                processedBytes = currentAppProcessedBytes,
                                status = "Backing up..."
                            ) else it
                        })
                    }
                }
                is ShifterEvent.BackupDone -> {
                    val delta = appWithSize.totalBytes - lastProcessedForThisApp
                    if (delta > 0) {
                        totalBytesProcessed.addAndGet(delta)
                    }
                    
                    _uiState.update { state ->
                        val newCompletedCount = state.completedCount + 1
                        val isAllDone = newCompletedCount == state.totalCount
                        
                        state.copy(
                            completedCount = newCompletedCount,
                            isRunning = !isAllDone,
                            currentAction = if (isAllDone) "Backup Batch Complete!" else state.currentAction,
                            items = state.items.map {
                                if (it.packageName == pkg) it.copy(
                                    progress = 1f,
                                    processedBytes = appWithSize.totalBytes,
                                    status = "Finished",
                                    isCompleted = true
                                ) else it
                            }
                        )
                    }
                    updateGlobalProgress(totalBatchBytes)
                }
                is ShifterEvent.RawLog -> {
                    if (event.line.contains("error", ignoreCase = true)) {
                         _uiState.update { state ->
                            state.copy(items = state.items.map {
                                if (it.packageName == pkg) it.copy(status = "Failed", isFailed = true) else it
                            })
                        }
                    }
                }
                else -> {}
            }
        }
        targetFile.delete()
    }

    private fun updateGlobalProgress(totalBatchBytes: Long) {
        val currentTotal = totalBytesProcessed.get()
        val overall = (currentTotal.toFloat() / totalBatchBytes).coerceIn(0f, 1f)
        _uiState.update { it.copy(overallProgress = overall, totalBytesProcessed = currentTotal) }
    }

    private suspend fun calculateAppSizes(apps: List<AppInfo>, comps: Set<Int>): List<AppWithSize> = withContext(Dispatchers.IO) {
        apps.map { app ->
            val pkg = app.packageName
            val paths = mutableListOf<String>()
            if (comps.contains(1)) {
                 val apkPath = Shell.cmd("pm path $pkg").exec().out.firstOrNull()?.removePrefix("package:")?.trim()
                 if (apkPath != null) paths.add(apkPath)
            }
            if (comps.contains(2)) {
                paths.add("/data/data/$pkg")
                paths.add("/data/user_de/0/$pkg")
            }
            if (comps.contains(3)) paths.add("/data/media/0/Android/data/$pkg")
            if (comps.contains(4)) paths.add("/data/media/0/Android/media/$pkg")
            if (comps.contains(5)) paths.add("/data/media/0/Android/obb/$pkg")

            val pathArgs = paths.joinToString(" ") { "'$it'" }
            val sizeKb = if (paths.isNotEmpty()) {
                val result = Shell.cmd("su -mm -c \"du -sk $pathArgs 2>/dev/null | awk '{s+=\\\$1} END {print s}'\"").exec().out.firstOrNull()?.trim()
                result?.toLongOrNull() ?: 0L
            } else 0L

            AppWithSize(app, sizeKb * 1024) 
        }
    }

    private fun runAppCountBasedBackup(apps: List<AppInfo>, comps: Set<Int>, path: String) {
        // Implementation for fallback or Option A
    }

    data class AppWithSize(val appInfo: AppInfo, val totalBytes: Long)
}

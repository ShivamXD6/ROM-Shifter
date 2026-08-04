package build.bytes.romshifter

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import build.bytes.romshifter.models.AppInfo
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.MigratorMode
import build.bytes.romshifter.utils.ExtrasManager
import build.bytes.romshifter.models.ShifterEvent
import build.bytes.romshifter.utils.BackendInstaller
import build.bytes.romshifter.utils.FlashManager
import build.bytes.romshifter.utils.MigratorManager
import build.bytes.romshifter.utils.NativeTelephonyManager
import build.bytes.romshifter.utils.SettingsManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()
    private val prefs = application.getSharedPreferences("shifter_prefs", Context.MODE_PRIVATE)

    private val _savedPath = MutableStateFlow(
        prefs.getString("base_path", SettingsManager.getDefaultPath())
            ?: SettingsManager.getDefaultPath()
    )
    val savedPath: StateFlow<String> = _savedPath.asStateFlow()
    val isFirstRun = MutableStateFlow(prefs.getBoolean("is_first_run", true))

    val isPrivilegedSystemize = MutableStateFlow(false)

    // Split notification IDs to guarantee the final state rings!
    private val notificationManager = NotificationManagerCompat.from(application)
    private val CHANNEL_PROGRESS_ID = "rom_shifter_progress_v2"
    private val CHANNEL_ALERT_ID = "rom_shifter_alerts_v2"
    private val NOTIFICATION_ID = 1001

    init {
        createNotificationChannel()
        viewModelScope.launch(Dispatchers.IO) {
            val isRooted = Shell.getShell().isRoot
            _uiState.value = _uiState.value.copy(hasRoot = isRooted)
            if (isRooted) {
                val checkScript =
                    Shell.cmd("su -c '[ -f /data/adb/#Shifter/ROM-Shifter.sh ] && echo YES'")
                        .exec().out.joinToString("")
                if (checkScript != "YES" || !prefs.getBoolean("is_engine_installed", false)) {
                    val success = BackendInstaller.installEngine(application)
                    if (success) prefs.edit { putBoolean("is_engine_installed", true) }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                "Task Progress",
                NotificationManager.IMPORTANCE_LOW
            )
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Task Completed",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(progressChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun updateProgressNotification(
        title: String,
        content: String,
        progress: Int,
        max: Int = 100
    ) {
        if (ContextCompat.checkSelfPermission(
                getApplication(),
                "android.permission.POST_NOTIFICATIONS"
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {

            // This displays the exact percentage in the notification body!
            val displayContent = if (progress in 0..100) "$content  •  $progress%" else content

            val builder = NotificationCompat.Builder(getApplication(), CHANNEL_PROGRESS_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(displayContent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)

            if (progress in 0..100) builder.setProgress(max, progress, false)
            else builder.setProgress(0, 0, true)

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    // High priority ringing alert for completion
    private fun showCompletionNotification(title: String, content: String) {
        if (ContextCompat.checkSelfPermission(
                getApplication(),
                "android.permission.POST_NOTIFICATIONS"
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            val builder = NotificationCompat.Builder(getApplication(), CHANNEL_ALERT_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL) // Forces ring and vibration!

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun finishOnboarding() {
        prefs.edit { putBoolean("is_first_run", false) }
        isFirstRun.value = false
    }

    fun openFlashWizard() {
        _uiState.value = _uiState.value.copy(flashWizardStep = 1, flashZips = emptyList())
    }

    fun closeFlashWizard() {
        _uiState.value = _uiState.value.copy(flashWizardStep = 0)
    }

    fun removeZip(index: Int) {
        val l =
            _uiState.value.flashZips.toMutableList(); if (index in l.indices) l.removeAt(index); _uiState.value =
            _uiState.value.copy(flashZips = l)
    }

    fun moveZipUp(index: Int) {
        val l = _uiState.value.flashZips.toMutableList(); if (index > 0) {
            val i = l.removeAt(index); l.add(index - 1, i)
        }; _uiState.value = _uiState.value.copy(flashZips = l)
    }

    fun moveZipDown(index: Int) {
        val l = _uiState.value.flashZips.toMutableList(); if (index < l.size - 1) {
            val i = l.removeAt(index); l.add(index + 1, i)
        }; _uiState.value = _uiState.value.copy(flashZips = l)
    }

    fun processSelectedZips(uris: List<Uri>, append: Boolean = false) {
        _uiState.value = _uiState.value.copy(isProcessingZips = true)
        viewModelScope.launch(Dispatchers.IO) {
            val zips =
                FlashManager.processZips(uris, _uiState.value.flashZips, append, getApplication())
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    flashZips = zips,
                    isProcessingZips = false,
                    flashWizardStep = 2
                )
            }
        }
    }

    fun checkLockscreenAndProceed() {
        viewModelScope.launch(Dispatchers.IO) {
            val isLocked = FlashManager.checkLockscreen()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(hasLockscreen = isLocked, flashWizardStep = 3)
            }
        }
    }

    fun toggleFlashWipePartition(partition: String) {
        val current = _uiState.value.flashWipePartitions.toMutableSet()
        if (current.contains(partition)) current.remove(partition) else current.add(partition)
        _uiState.value = _uiState.value.copy(flashWipePartitions = current)
    }

    fun setFlashFormatData(format: Boolean) {
        _uiState.value = _uiState.value.copy(flashFormatData = format)
    }

    fun generateOrsAndProceed() {
        viewModelScope.launch(Dispatchers.IO) {
            FlashManager.generateOrsAndProceed(
                _uiState.value.flashWipePartitions,
                _uiState.value.flashFormatData,
                _uiState.value.flashZips
            )
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(flashWizardStep = 4)
            }
        }
    }

    fun restartFlashWizard() {
        viewModelScope.launch(Dispatchers.IO) {
            FlashManager.restartFlashWizard(); withContext(
            Dispatchers.Main
        ) {
            _uiState.value = _uiState.value.copy(
                flashWizardStep = 1,
                flashZips = emptyList(),
                currentAction = "Operation Completed"
            )
        }
        }
    }

    fun executeFlashNow() {
        _uiState.value = _uiState.value.copy(
            currentAction = "Rebooting to Recovery...",
            flashWizardStep = 4
        ); viewModelScope.launch(Dispatchers.IO) { FlashManager.executeFlashNow() }
    }

    fun getAllPartitions(): List<String> {
        val parts = FlashManager.getAllPartitions().toMutableList()
        if (!parts.contains("data")) parts.add("data")
        return parts.sorted()
    }

    fun getBackedUpImages(): List<String> = FlashManager.getBackedUpImages(_savedPath.value)
    fun deleteLivePartitionImage(imgName: String) =
        FlashManager.deleteLivePartitionImage(_savedPath.value, imgName)

    fun runLiveOperation(
        action: String,
        partition: String,
        customPath: String? = null,
        onComplete: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            FlashManager.runLiveOperation(action, partition, customPath, _savedPath.value)
            val msg =
                if (action == "--live-backup") "Backed up $partition successfully!" else "Flashed $partition successfully!"
            withContext(Dispatchers.Main) { onComplete(msg) }
        }
    }

    fun migrateFolder(newPath: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            SettingsManager.migrateFolder(_savedPath.value.trimEnd('/'), newPath, prefs)
            Shell.cmd("su -c 'mkdir -p \"$newPath\" && touch \"$newPath/.shifter_dir\" && touch \"$newPath/.nomedia\"'")
                .exec()
            _savedPath.value = newPath
            withContext(Dispatchers.Main) { onSuccess() }
        }
    }

    fun autoDetectShifterFolder(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val searchCmd =
                "su -mm -c 'find /data/media/0 /mnt/media_rw -maxdepth 5 -type f -name \".shifter_dir\" 2>/dev/null | head -n 1'"
            val markerFile = Shell.cmd(searchCmd).exec().out.joinToString("").trim()
            var detected =
                if (markerFile.isNotEmpty()) markerFile.removeSuffix("/.shifter_dir") else ""

            if (detected.startsWith("/data/media/0")) {
                detected = detected.replaceFirst("/data/media/0", "/storage/emulated/0")
            } else if (detected.startsWith("/mnt/media_rw/")) {
                detected = detected.replaceFirst("/mnt/media_rw/", "/storage/")
            }

            if (detected.isEmpty()) {
                detected = SettingsManager.autoDetectFolder(prefs) ?: ""
            }

            withContext(Dispatchers.Main) {
                if (detected.isNotEmpty()) {
                    _savedPath.value = detected
                    Toast.makeText(
                        getApplication(),
                        "Auto-detected folder at: $detected",
                        Toast.LENGTH_SHORT
                    ).show()
                    onResult(true)
                } else {
                    Toast.makeText(
                        getApplication(),
                        "No existing #Shifter folder found. Please select manually.",
                        Toast.LENGTH_SHORT
                    ).show()
                    onResult(false)
                }
            }
        }
    }

    fun resetApp(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val pkg = context.packageName
            Shell.cmd("su -c '(sleep 2 && pm clear $pkg && am start -n $pkg/build.bytes.romshifter.MainActivity) &'")
                .exec()
            withContext(Dispatchers.Main) { kotlin.system.exitProcess(0) }
        }
    }

    fun runNativeDataOperation(
        context: Context,
        isBackup: Boolean,
        doSms: Boolean,
        doCall: Boolean,
        doContacts: Boolean
    ) {
        val title = if (isBackup) "Backing up Native Data" else "Restoring Native Data"
        _uiState.value = _uiState.value.copy(
            isRunning = true,
            currentAction = title,
            currentStep = "Processing via ContentResolver...",
            progress = 0
        )
        updateProgressNotification(title, "Starting Process...", 0)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                NativeTelephonyManager.runOperation(
                    context,
                    isBackup,
                    doSms,
                    doCall,
                    doContacts,
                    _savedPath.value
                ) { step, prog ->
                    _uiState.value = _uiState.value.copy(currentStep = step, progress = prog)
                    updateProgressNotification(title, step, prog)
                }
                withContext(Dispatchers.Main) {
                    val finalMsg = if (isBackup) "Backup Complete!" else "Restore Complete!"
                    showCompletionNotification(finalMsg, "Task finished successfully.")
                    _uiState.value = _uiState.value.copy(
                        isRunning = false,
                        currentAction = finalMsg,
                        currentStep = "Done.",
                        progress = 100
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    cancelNotification()
                    _uiState.value = _uiState.value.copy(
                        isRunning = false,
                        currentAction = "Error occurred",
                        currentStep = e.message ?: ""
                    )
                }
            }
        }
    }

    fun deleteNativeBackups(
        context: Context,
        doSms: Boolean,
        doCall: Boolean,
        doContacts: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = _savedPath.value
            if (doSms) Shell.cmd("su -c \"rm -f '$path/Native/'*sms*\"").exec()
            if (doCall) Shell.cmd("su -c \"rm -f '$path/Native/'*call*\"").exec()
            if (doContacts) Shell.cmd("su -c \"rm -f '$path/Native/'*contact*\"").exec()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Selected Native Backups Deleted", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    suspend fun isMagisk(): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("su -c '[ -d /data/adb/magisk ] && echo YES'").exec().out.joinToString("")
            .trim() == "YES"
    }

    suspend fun canSystemize(): Boolean = withContext(Dispatchers.IO) {
        if (isMagisk()) return@withContext true
        val check =
            Shell.cmd("su -c '[ -d /data/adb/modules/meta-overlayfs ] || [ -d /data/adb/metamodule ] && echo YES'")
                .exec().out.joinToString("").trim()
        return@withContext check == "YES"
    }

    fun toggleSystemApps() {
        val newState = !_uiState.value.showSystemApps
        _uiState.value = _uiState.value.copy(showSystemApps = newState)
        if (newState && !_uiState.value.systemAppsFetched && (_uiState.value.migratorMode == MigratorMode.BACKUP_APPS || _uiState.value.migratorMode == MigratorMode.RESTORE_APPS || _uiState.value.migratorMode == MigratorMode.SYSTEMIZE)) {
            fetchAppsList(
                if (_uiState.value.migratorMode == MigratorMode.RESTORE_APPS) "RestoreSystem" else "System",
                append = true
            )
        }
    }

    fun setPrivilegedSystemize(enabled: Boolean) {
        isPrivilegedSystemize.value = enabled
    }

    fun toggleRestoreDebloatMode() {
        val newState = !_uiState.value.isRestoreDebloatMode
        _uiState.value = _uiState.value.copy(isRestoreDebloatMode = newState)
        if (newState) fetchAppsList("Uninstalled") else fetchAppsList("AllInstalled")
    }

    fun setMigratorMode(mode: MigratorMode) {
        val showSysApps =
            (mode == MigratorMode.DEBLOAT || mode == MigratorMode.RESTORE_APPS || mode == MigratorMode.MANAGE)
        _uiState.value = _uiState.value.copy(
            migratorMode = mode,
            appList = emptyList(),
            progress = 0,
            searchQuery = "",
            currentAction = "Operation Completed",
            currentStep = "",
            logs = emptyList(),
            showUserApps = true,
            showSystemApps = showSysApps,
            systemAppsFetched = false,
            isRestoreDebloatMode = false,
            globalComponents = setOf(1, 2, 3, 4, 5, 6)
        )
        if (mode == MigratorMode.SYSTEMIZE) isPrivilegedSystemize.value = false

        when (mode) {
            MigratorMode.BACKUP_APPS -> fetchAppsList("User")
            MigratorMode.RESTORE_APPS -> {
                MigratorManager.clearCache(); fetchAppsList("AllBackups")
            }

            MigratorMode.DEBLOAT -> fetchAppsList("AllInstalled")
            MigratorMode.MANAGE -> {
                MigratorManager.clearCache(); fetchAppsList("AllBackups")
            }

            MigratorMode.SYSTEMIZE -> fetchAppsList("User")
            else -> {}
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setForceRemove(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(forceRemoveEnabled = enabled)
    }

    fun toggleShowUserApps(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showUserApps = enabled)
    }

    fun toggleGlobalComponent(id: Int) {
        val current = _uiState.value.globalComponents.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _uiState.value = _uiState.value.copy(globalComponents = current)
    }

    fun toggleAppSelection(packageName: String) {
        val currentList = _uiState.value.appList.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            currentList[index] =
                currentList[index].copy(isSelected = !currentList[index].isSelected)
            _uiState.value = _uiState.value.copy(appList = currentList)
        }
    }

    fun selectAllVisibleApps(select: Boolean, visibleApps: List<AppInfo>) {
        val visiblePackageNames = visibleApps.map { it.packageName }.toSet()
        val updatedList = _uiState.value.appList.map {
            if (visiblePackageNames.contains(it.packageName)) it.copy(isSelected = select) else it
        }
        _uiState.value = _uiState.value.copy(appList = updatedList)
    }

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(
            logs = emptyList(),
            currentAction = "Operation Completed",
            currentStep = "",
            progress = 0
        )
    }

    private fun fetchAppsList(type: String, append: Boolean = false) {
        if (!append) _uiState.value =
            _uiState.value.copy(isFetchingApps = true, currentAction = "Fetching apps list...")

        viewModelScope.launch(Dispatchers.IO) {
            if (type == "AllInstalled") {
                val userApps = MigratorManager.fetchAppsList(
                    getApplication(),
                    _savedPath.value,
                    "User",
                    false,
                    emptyList()
                )
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        appList = userApps,
                        isFetchingApps = true,
                        currentAction = "Loading system apps in background..."
                    )
                }

                val sysApps = MigratorManager.fetchAppsList(
                    getApplication(),
                    _savedPath.value,
                    "System",
                    false,
                    emptyList()
                )
                withContext(Dispatchers.Main) {
                    val combined = (userApps + sysApps).sortedBy { it.label.lowercase() }
                    _uiState.value = _uiState.value.copy(
                        appList = combined,
                        isFetchingApps = false,
                        currentAction = "Operation Completed",
                        systemAppsFetched = true
                    )
                }
            } else {
                val apps = MigratorManager.fetchAppsList(
                    getApplication(),
                    _savedPath.value,
                    type,
                    append,
                    _uiState.value.appList
                )
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        appList = apps,
                        isFetchingApps = false,
                        currentAction = if (apps.isEmpty()) "No apps found." else "Operation Completed"
                    )
                    if (type == "System" || type == "RestoreSystem" || type == "AllBackups") {
                        _uiState.value = _uiState.value.copy(systemAppsFetched = true)
                    }
                }
            }
        }
    }

    fun runDynamicOperation() {
        val state = _uiState.value
        val selectedApps =
            state.appList.filter { it.isSelected && (it.isSystem && state.showSystemApps || !it.isSystem && state.showUserApps || state.isRestoreDebloatMode) }
        if (selectedApps.isEmpty()) return

        _uiState.value = state.copy(
            isRunning = true,
            currentAction = "Initializing Process...",
            currentStep = "Requesting root shell...",
            logs = listOf("Started operations...")
        )

        viewModelScope.launch {
            // Extract the callbacks so we can pass them cleanly to either manager
            val updateLog: (String) -> Unit = { log ->
                val currentLogs = _uiState.value.logs.toMutableList()
                currentLogs.add(log)
                _uiState.value = _uiState.value.copy(logs = currentLogs.takeLast(100))
            }

            val updateProgress: (String, String, Int) -> Unit = { action, step, prog ->
                val safeAction = action.ifEmpty { "ROM Shifter" }
                // Update silent ongoing progress bar
                updateProgressNotification(safeAction, step, prog)

                val newState = _uiState.value.copy()
                if (action.isNotEmpty()) _uiState.value = newState.copy(currentAction = action)
                if (step.isNotEmpty()) _uiState.value = newState.copy(currentStep = step)
                if (prog >= 0) _uiState.value = newState.copy(progress = prog)
            }

            val onComplete: (String, String) -> Unit = { action, step ->
                showCompletionNotification(action, step)

                when (state.migratorMode) {
                    MigratorMode.DEBLOAT -> {
                        MigratorManager.clearCache()
                        if (state.isRestoreDebloatMode) fetchAppsList("Uninstalled") else fetchAppsList(
                            "AllInstalled"
                        )
                    }

                    MigratorMode.MANAGE -> {
                        MigratorManager.clearCache()
                        fetchAppsList("AllBackups")
                    }

                    else -> {}
                }
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    currentAction = action,
                    currentStep = step,
                    progress = 100
                )
            }

            // Route the operation to the correct manager!
            when (state.migratorMode) {
                MigratorMode.DEBLOAT -> {
                    ExtrasManager.runDebloatOperation(
                        context = getApplication(),
                        selectedApps = selectedApps,
                        isRestore = state.isRestoreDebloatMode,
                        forceRemove = state.forceRemoveEnabled,
                        updateLog = updateLog,
                        updateProgress = updateProgress,
                        onComplete = onComplete
                    )
                }

                MigratorMode.SYSTEMIZE -> {
                    ExtrasManager.runSystemizeOperation(
                        context = getApplication(),
                        selectedApps = selectedApps,
                        isPrivileged = isPrivilegedSystemize.value,
                        updateLog = updateLog,
                        updateProgress = updateProgress,
                        onComplete = onComplete
                    )
                }

                else -> {
                    // Backup, Restore, and Manage routes here
                    MigratorManager.runDynamicOperation(
                        context = getApplication(),
                        state = state,
                        selectedApps = selectedApps,
                        currentPath = _savedPath.value,
                        isPrivilegedSystemize = isPrivilegedSystemize.value,
                        updateLog = updateLog,
                        updateProgress = updateProgress,
                        onComplete = onComplete
                    )
                }
            }
        }
    }
}
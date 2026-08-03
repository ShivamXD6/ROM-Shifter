package build.bytes.romshifter

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import build.bytes.romshifter.models.AppInfo
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.MigratorMode
import build.bytes.romshifter.models.ShifterEvent
import build.bytes.romshifter.utils.BackendInstaller
import build.bytes.romshifter.utils.ExtrasManager
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

    private val _savedPath = MutableStateFlow(prefs.getString("base_path", SettingsManager.getDefaultPath()) ?: SettingsManager.getDefaultPath())
    val savedPath: StateFlow<String> = _savedPath.asStateFlow()
    val isFirstRun = MutableStateFlow(prefs.getBoolean("is_first_run", true))

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val isRooted = Shell.getShell().isRoot
            _uiState.value = _uiState.value.copy(hasRoot = isRooted)
            if (isRooted) {
                val checkScript = Shell.cmd("su -c '[ -f /data/adb/#Shifter/ROM-Shifter.sh ] && echo YES'").exec().out.joinToString("")
                if (checkScript != "YES" || !prefs.getBoolean("is_engine_installed", false)) {
                    val success = BackendInstaller.installEngine(application)
                    if (success) prefs.edit { putBoolean("is_engine_installed", true) }
                }
            }
        }
    }

    fun finishOnboarding() {
        prefs.edit { putBoolean("is_first_run", false) }
        isFirstRun.value = false
    }

    fun openFlashWizard() { _uiState.value = _uiState.value.copy(flashWizardStep = 1, flashZips = emptyList()) }
    fun closeFlashWizard() { _uiState.value = _uiState.value.copy(flashWizardStep = 0) }
    fun setFlashWipeMode(mode: Int) { _uiState.value = _uiState.value.copy(flashWipeMode = mode) }
    fun removeZip(index: Int) { val l = _uiState.value.flashZips.toMutableList(); if (index in l.indices) l.removeAt(index); _uiState.value = _uiState.value.copy(flashZips = l) }
    fun moveZipUp(index: Int) { val l = _uiState.value.flashZips.toMutableList(); if (index > 0) { val i = l.removeAt(index); l.add(index - 1, i) }; _uiState.value = _uiState.value.copy(flashZips = l) }
    fun moveZipDown(index: Int) { val l = _uiState.value.flashZips.toMutableList(); if (index < l.size - 1) { val i = l.removeAt(index); l.add(index + 1, i) }; _uiState.value = _uiState.value.copy(flashZips = l) }

    fun processSelectedZips(uris: List<Uri>, append: Boolean = false) {
        _uiState.value = _uiState.value.copy(isProcessingZips = true)
        viewModelScope.launch(Dispatchers.IO) {
            val zips = FlashManager.processZips(uris, _uiState.value.flashZips, append, getApplication())
            withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(flashZips = zips, isProcessingZips = false, flashWizardStep = 2) }
        }
    }
    fun checkLockscreenAndProceed() {
        viewModelScope.launch(Dispatchers.IO) {
            val isLocked = FlashManager.checkLockscreen()
            withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(hasLockscreen = isLocked, flashWizardStep = 3) }
        }
    }
    fun generateOrsAndProceed() {
        viewModelScope.launch(Dispatchers.IO) {
            FlashManager.generateOrsAndProceed(_uiState.value.flashWipeMode, _uiState.value.flashZips)
            withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(flashWizardStep = 4) }
        }
    }
    fun restartFlashWizard() { viewModelScope.launch(Dispatchers.IO) { FlashManager.restartFlashWizard(); withContext(Dispatchers.Main) { _uiState.value = _uiState.value.copy(flashWizardStep = 1, flashZips = emptyList(), currentAction = "Ready to Shift") } } }
    fun executeFlashNow() { _uiState.value = _uiState.value.copy(currentAction = "Rebooting to Recovery...", flashWizardStep = 4); viewModelScope.launch(Dispatchers.IO) { FlashManager.executeFlashNow() } }
    fun getAllPartitions(): List<String> = FlashManager.getAllPartitions()
    fun getBackedUpImages(): List<String> = FlashManager.getBackedUpImages(_savedPath.value)
    fun deleteLivePartitionImage(imgName: String) = FlashManager.deleteLivePartitionImage(_savedPath.value, imgName)
    fun runLiveOperation(action: String, partition: String, customPath: String? = null, onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            FlashManager.runLiveOperation(action, partition, customPath, _savedPath.value)
            val msg = if (action == "--live-backup") "Backed up $partition successfully!" else "Flashed $partition successfully!"
            withContext(Dispatchers.Main) { onComplete(msg) }
        }
    }

    fun migrateFolder(newPath: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            SettingsManager.migrateFolder(_savedPath.value.trimEnd('/'), newPath, prefs)
            Shell.cmd("su -c 'mkdir -p \"$newPath\" && touch \"$newPath/.shifter_dir\"'").exec()
            _savedPath.value = newPath
            withContext(Dispatchers.Main) { onSuccess() }
        }
    }

    fun autoDetectShifterFolder(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val searchCmd = "su -mm -c 'find /storage /data/media/0 /mnt/media_rw -maxdepth 5 -type f -name \".shifter_dir\" 2>/dev/null | head -n 1'"
            val markerFile = Shell.cmd(searchCmd).exec().out.joinToString("").trim()
            var detected = if (markerFile.isNotEmpty()) markerFile.removeSuffix("/.shifter_dir") else ""

            if (detected.isEmpty()) {
                detected = SettingsManager.autoDetectFolder(prefs) ?: ""
            }

            withContext(Dispatchers.Main) {
                if (detected.isNotEmpty()) {
                    _savedPath.value = detected
                    Toast.makeText(getApplication(), "Auto-detected folder at: $detected", Toast.LENGTH_SHORT).show()
                    onResult(true)
                } else {
                    Toast.makeText(getApplication(), "No existing #Shifter folder found. Please select manually.", Toast.LENGTH_SHORT).show()
                    onResult(false)
                }
            }
        }
    }

    fun resetApp(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val pkg = context.packageName
            Shell.cmd("su -c '(sleep 2 && pm clear $pkg && am start -n $pkg/build.bytes.romshifter.MainActivity) &'").exec()
            withContext(Dispatchers.Main) { kotlin.system.exitProcess(0) }
        }
    }

    fun exportLogs() = SettingsManager.exportLogs(_uiState.value.logs, _savedPath.value, getApplication<Application>().cacheDir)

    private fun autoHideProgress() {
        viewModelScope.launch {
            delay(3000)
            if (!_uiState.value.isRunning) {
                _uiState.value = _uiState.value.copy(currentAction = "Ready to Shift", currentStep = "", progress = 0)
            }
        }
    }

    fun runNativeDataOperation(context: Context, isBackup: Boolean, doSms: Boolean, doCall: Boolean, doContacts: Boolean) {
        _uiState.value = _uiState.value.copy(isRunning = true, currentAction = if (isBackup) "Backing up Native Data..." else "Restoring Native Data...", currentStep = "Processing via Android ContentResolver...", progress = 0)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                NativeTelephonyManager.runOperation(context, isBackup, doSms, doCall, doContacts, _savedPath.value) { step, prog -> _uiState.value = _uiState.value.copy(currentStep = step, progress = prog) }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isRunning = false, currentAction = "Operation Complete!", currentStep = "Done.", progress = 100)
                    autoHideProgress()
                }
            } catch(e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isRunning = false, currentAction = "Error occurred", currentStep = e.message ?: "")
                    autoHideProgress()
                }
            }
        }
    }

    fun clearNativeDataBackups(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("su -c \"rm -rf '${_savedPath.value}/Native'\"").exec()
            withContext(Dispatchers.Main) { Toast.makeText(context, "Native Backups Cleared", Toast.LENGTH_SHORT).show() }
        }
    }

    fun runRomDataOperation(isBackup: Boolean, settings: Boolean, callRing: Boolean, smsRing: Boolean, wall: Boolean) {
        _uiState.value = _uiState.value.copy(isRunning = true, currentAction = if (isBackup) "Backing up ROM Data..." else "Restoring ROM Data...", currentStep = "Requesting root shell...", logs = listOf("Starting ROM Data Migrator..."))
        viewModelScope.launch(Dispatchers.IO) {
            ExtrasManager.runRomDataOperation(isBackup, settings, callRing, smsRing, wall, _savedPath.value) { event ->
                val currentLogs = _uiState.value.logs.toMutableList()
                when (event) {
                    is ShifterEvent.InfoStep -> { currentLogs.add("-> ${event.msg}"); _uiState.value = _uiState.value.copy(currentStep = event.msg, logs = currentLogs.takeLast(100)) }
                    is ShifterEvent.GlobalDone -> {
                        currentLogs.add("ROM operation successful!")
                        _uiState.value = _uiState.value.copy(isRunning = false, currentAction = "Operation Completed!", progress = 100, currentStep = "Saved successfully.")
                        autoHideProgress()
                    }
                    else -> {}
                }
            }
        }
    }

    suspend fun isMagisk(): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("su -c '[ -d /data/adb/magisk ] && echo YES'").exec().out.joinToString("").trim() == "YES"
    }

    fun checkAndInstallMetaModule() {
        _uiState.value = _uiState.value.copy(isRunning = true, currentAction = "Installing Meta-OverlayFS...", currentStep = "Downloading module...", progress = 50, logs = listOf("Initiating install..."))
        viewModelScope.launch(Dispatchers.IO) {
            ExtrasManager.checkAndInstallMetaModule { event ->
                val currentLogs = _uiState.value.logs.toMutableList()
                when (event) {
                    is ShifterEvent.InfoStep -> { currentLogs.add("-> ${event.msg}"); _uiState.value = _uiState.value.copy(currentStep = event.msg, logs = currentLogs.takeLast(100)) }
                    // Crucial fix: Display the raw curl download logs live on the UI
                    is ShifterEvent.RawLog -> { currentLogs.add(event.line); _uiState.value = _uiState.value.copy(logs = currentLogs.takeLast(100)) }
                    is ShifterEvent.GlobalDone -> {
                        _uiState.value = _uiState.value.copy(isRunning = false, currentAction = "Module Installed", progress = 100, currentStep = "Please Reboot.")
                        autoHideProgress()
                    }
                    else -> {}
                }
            }
        }
    }

    suspend fun isMetaModuleInstalled(): Boolean = ExtrasManager.isMetaModuleInstalled()

    fun toggleSystemApps() {
        val newState = !_uiState.value.showSystemApps
        _uiState.value = _uiState.value.copy(showSystemApps = newState)
        if (newState && !_uiState.value.systemAppsFetched && (_uiState.value.migratorMode == MigratorMode.BACKUP_APPS || _uiState.value.migratorMode == MigratorMode.RESTORE_APPS || _uiState.value.migratorMode == MigratorMode.SYSTEMIZE)) {
            fetchAppsList(if (_uiState.value.migratorMode == MigratorMode.RESTORE_APPS) "RestoreSystem" else "System", append = true)
        }
    }

    fun toggleRestoreDebloatMode() {
        val newState = !_uiState.value.isRestoreDebloatMode
        _uiState.value = _uiState.value.copy(isRestoreDebloatMode = newState)
        if (newState) fetchAppsList("Uninstalled") else fetchAppsList("AllInstalled")
    }

    fun setMigratorMode(mode: MigratorMode) {
        val showSysApps = (mode == MigratorMode.DEBLOAT || mode == MigratorMode.RESTORE_APPS)
        _uiState.value = _uiState.value.copy(
            migratorMode = mode, appList = emptyList(), progress = 0,
            searchQuery = "", currentAction = "Ready to Shift", currentStep = "", logs = emptyList(),
            showUserApps = true, showSystemApps = showSysApps, systemAppsFetched = false,
            isRestoreDebloatMode = false, globalComponents = setOf(1, 2, 3, 4, 5, 6)
        )
        when (mode) {
            MigratorMode.BACKUP_APPS -> fetchAppsList("User")
            MigratorMode.RESTORE_APPS -> { MigratorManager.clearCache(); fetchAppsList("AllBackups") }
            MigratorMode.DEBLOAT -> fetchAppsList("AllInstalled")
            MigratorMode.MANAGE -> { MigratorManager.clearCache(); fetchAppsList("AllBackups") }
            MigratorMode.SYSTEMIZE -> fetchAppsList("User")
            else -> {}
        }
    }

    fun updateSearchQuery(query: String) { _uiState.value = _uiState.value.copy(searchQuery = query) }
    fun setForceRemove(enabled: Boolean) { _uiState.value = _uiState.value.copy(forceRemoveEnabled = enabled) }
    fun toggleShowUserApps(enabled: Boolean) { _uiState.value = _uiState.value.copy(showUserApps = enabled) }
    fun toggleGlobalComponent(id: Int) {
        val current = _uiState.value.globalComponents.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _uiState.value = _uiState.value.copy(globalComponents = current)
    }

    fun toggleAppSelection(packageName: String) {
        val currentList = _uiState.value.appList.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            currentList[index] = currentList[index].copy(isSelected = !currentList[index].isSelected)
            _uiState.value = _uiState.value.copy(appList = currentList)
        }
    }

    fun selectAllVisibleApps(select: Boolean, visibleApps: List<AppInfo>) {
        val visiblePackageNames = visibleApps.map { it.packageName }.toSet()
        val updatedList = _uiState.value.appList.map { if (visiblePackageNames.contains(it.packageName)) it.copy(isSelected = select) else it }
        _uiState.value = _uiState.value.copy(appList = updatedList)
    }

    private fun fetchAppsList(type: String, append: Boolean = false) {
        if (!append) _uiState.value = _uiState.value.copy(isFetchingApps = true, currentAction = "Fetching apps list...")

        viewModelScope.launch(Dispatchers.IO) {
            if (type == "AllInstalled") {
                val userApps = MigratorManager.fetchAppsList(getApplication(), _savedPath.value, "User", false, emptyList())
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(appList = userApps, isFetchingApps = true, currentAction = "Loading system apps in background...")
                }

                val sysApps = MigratorManager.fetchAppsList(getApplication(), _savedPath.value, "System", false, emptyList())
                withContext(Dispatchers.Main) {
                    val combined = (userApps + sysApps).sortedBy { it.label.lowercase() }
                    _uiState.value = _uiState.value.copy(appList = combined, isFetchingApps = false, currentAction = "Ready to Shift", systemAppsFetched = true)
                }
            } else {
                val apps = MigratorManager.fetchAppsList(getApplication(), _savedPath.value, type, append, _uiState.value.appList)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(appList = apps, isFetchingApps = false, currentAction = if (apps.isEmpty()) "No apps found." else "Ready to Shift")
                    if (type == "System" || type == "RestoreSystem" || type == "AllBackups") {
                        _uiState.value = _uiState.value.copy(systemAppsFetched = true)
                    }
                }
            }
        }
    }

    fun runDynamicOperation() {
        val state = _uiState.value
        val selectedApps = state.appList.filter { it.isSelected && (it.isSystem && state.showSystemApps || !it.isSystem && state.showUserApps || state.isRestoreDebloatMode) }
        if (selectedApps.isEmpty()) return

        _uiState.value = state.copy(isRunning = true, currentAction = "Initializing Process...", currentStep = "Requesting root shell...", logs = listOf("Started operations..."))

        viewModelScope.launch {
            MigratorManager.runDynamicOperation(
                context = getApplication(), state = state, selectedApps = selectedApps, currentPath = _savedPath.value,
                updateLog = { log ->
                    val currentLogs = _uiState.value.logs.toMutableList()
                    currentLogs.add(log)
                    _uiState.value = _uiState.value.copy(logs = currentLogs.takeLast(100))
                },
                updateProgress = { action, step, prog ->
                    val newState = _uiState.value.copy()
                    if (action.isNotEmpty()) _uiState.value = newState.copy(currentAction = action)
                    if (step.isNotEmpty()) _uiState.value = newState.copy(currentStep = step)
                    if (prog >= 0) _uiState.value = newState.copy(progress = prog)
                },
                onComplete = { action, step ->
                    when (state.migratorMode) {
                        MigratorMode.DEBLOAT -> if (state.isRestoreDebloatMode) fetchAppsList("Uninstalled") else fetchAppsList("AllInstalled")
                        MigratorMode.MANAGE -> { MigratorManager.clearCache(); fetchAppsList("AllBackups") }
                        else -> {}
                    }
                    _uiState.value = _uiState.value.copy(isRunning = false, currentAction = action, currentStep = step, progress = 100)
                    autoHideProgress()
                }
            )
        }
    }
}
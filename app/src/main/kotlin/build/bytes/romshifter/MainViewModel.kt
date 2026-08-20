package build.bytes.romshifter

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import build.bytes.romshifter.models.AppInfo
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.FlashAction
import build.bytes.romshifter.models.MigratorMode
import build.bytes.romshifter.utils.BackendInstaller
import build.bytes.romshifter.utils.FlashManager
import build.bytes.romshifter.utils.MigratorManager
import build.bytes.romshifter.utils.NativeManager
import build.bytes.romshifter.utils.SettingsManager
import build.bytes.romshifter.utils.ToolsManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    private val _appList = MutableStateFlow<List<AppInfo>>(emptyList())
    val appList: StateFlow<List<AppInfo>> = _appList.asStateFlow()

    private val _flashActions = MutableStateFlow<List<FlashAction>>(emptyList())
    val flashActions: StateFlow<List<FlashAction>> = _flashActions.asStateFlow()

    private val prefs = application.getSharedPreferences("shifter_prefs", Context.MODE_PRIVATE)

    private val _savedPath = MutableStateFlow(
        prefs.getString("base_path", SettingsManager.getDefaultPath())
            ?: SettingsManager.getDefaultPath()
    )
    val savedPath: StateFlow<String> = _savedPath.asStateFlow()
    val isFirstRun = MutableStateFlow(prefs.getBoolean("is_first_run", true))

    private val _themeMode = MutableStateFlow(prefs.getInt("theme_mode", 0))
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    fun setTheme(mode: Int) {
        _themeMode.value = mode
        prefs.edit { putInt("theme_mode", mode) }
    }

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean("dynamic_color", true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs.edit { putBoolean("dynamic_color", enabled) }
    }

    private val _currentTab = MutableStateFlow(prefs.getInt("last_selected_tab", 1))
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    fun setTab(index: Int) {
        _currentTab.value = index
        prefs.edit { putInt("last_selected_tab", index) }
    }

    private val _updateChannel = MutableStateFlow(prefs.getInt("update_channel", 1))
    val updateChannel: StateFlow<Int> = _updateChannel.asStateFlow()

    private val _updateStatus = MutableStateFlow("")
    val updateStatus: StateFlow<String> = _updateStatus.asStateFlow()

    data class UpdateInfo(val version: String, val changelog: String, val downloadUrl: String)

    val showUpdateDialog = MutableStateFlow(false)
    val updateInfo = MutableStateFlow<UpdateInfo?>(null)

    private val _fullChangelogs = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val fullChangelogs: StateFlow<List<Pair<String, String>>> = _fullChangelogs.asStateFlow()

    private val _isFetchingChangelogs = MutableStateFlow(false)
    val isFetchingChangelogs: StateFlow<Boolean> = _isFetchingChangelogs.asStateFlow()

    fun setUpdateChannel(channel: Int) {
        _updateChannel.value = channel
        prefs.edit { putInt("update_channel", channel) }
    }

    fun checkForUpdates(isSilent: Boolean = false) {
        val context: Application = getApplication()
        val currentTime = System.currentTimeMillis()

        val lastCheck = prefs.getLong(PREF_LAST_UPDATE_CHECK, 0L)
        val lastError = prefs.getLong("last_update_error_time", 0L)

        if (isSilent) {
            if (currentTime - lastCheck < 24 * 60 * 60 * 1000L) return
            if (currentTime - lastError < 60 * 60 * 1000L) return
        }

        if (!isSilent) _updateStatus.value = "Checking for updates..."

        viewModelScope.launch(Dispatchers.IO) {
            var retryCount = 0
            var success = false

            while (retryCount < 2 && !success) {
                try {
                    val url =
                        java.net.URL("https://api.github.com/repos/ShivamXD6/ROM-Shifter/releases")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty(
                        "Accept",
                        "application/json, application/vnd.github.v3+json"
                    )
                    connection.setRequestProperty(
                        "User-Agent",
                        "ROMShifter-App-Updater"
                    )

                    val cachedEtag = prefs.getString(PREF_GITHUB_ETAG, "")
                    if (!cachedEtag.isNullOrEmpty()) {
                        connection.setRequestProperty("If-None-Match", cachedEtag)
                    }

                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.instanceFollowRedirects = true

                    val responseCode = connection.responseCode

                    val responseJson = when (responseCode) {
                        200 -> {
                            val etag = connection.getHeaderField("ETag")
                            val body = connection.inputStream.bufferedReader().use { it.readText() }
                            if (!etag.isNullOrEmpty()) {
                                prefs.edit {
                                    putString(PREF_GITHUB_ETAG, etag)
                                    putString(PREF_RELEASES_CACHE, body)
                                }
                            }
                            body
                        }

                        304 -> {
                            prefs.getString(PREF_RELEASES_CACHE, "[]") ?: "[]"
                        }

                        403 -> {
                            val errorMsg =
                                connection.errorStream?.bufferedReader()?.use { it.readText() }
                                    ?: ""
                            if (errorMsg.contains("rate limit", ignoreCase = true)) {
                                prefs.edit { putLong("last_update_error_time", currentTime) }
                                withContext(Dispatchers.Main) {
                                    if (!isSilent) Toast.makeText(
                                        context,
                                        "Rate limit hit, using cached info.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                prefs.getString(PREF_RELEASES_CACHE, null)
                            } else {
                                null
                            }
                        }

                        else -> null
                    }

                    if (responseJson != null) {
                        prefs.edit {
                            putLong(PREF_LAST_UPDATE_CHECK, currentTime)
                            remove("last_update_error_time")
                        }

                        val releases = org.json.JSONArray(responseJson)
                        var targetRelease: org.json.JSONObject? = null

                        for (i in 0 until releases.length()) {
                            val release = releases.getJSONObject(i)
                            val isPreRelease = release.getBoolean("prerelease")
                            if (_updateChannel.value == 0 && isPreRelease) continue
                            targetRelease = release
                            break
                        }

                        if (targetRelease != null) {
                            val tagName = targetRelease.getString("tag_name")
                            val htmlUrl = targetRelease.getString("html_url")
                            val body = targetRelease.optString("body", "No release notes provided.")
                            val assets = targetRelease.optJSONArray("assets")
                            val downloadUrl = if (assets != null && assets.length() > 0) {
                                assets.getJSONObject(0).getString("browser_download_url")
                            } else {
                                htmlUrl
                            }

                            val currentVersionCode = try {
                                val packageInfo =
                                    context.packageManager.getPackageInfo(context.packageName, 0)
                                PackageInfoCompat.getLongVersionCode(packageInfo)
                            } catch (_: Exception) {
                                -1L
                            }

                            val cleanLatest =
                                tagName.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L

                            withContext(Dispatchers.Main) {
                                if (cleanLatest > currentVersionCode && currentVersionCode != -1L) {
                                    _updateStatus.value = "New version available: $tagName"
                                    updateInfo.value = UpdateInfo(tagName, body, downloadUrl)
                                    showUpdateDialog.value = true
                                } else {
                                    _updateStatus.value = "App is up to date!"
                                    if (!isSilent) Toast.makeText(
                                        context,
                                        "You are on the latest version.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            success = true
                        }
                    } else if (responseCode == 504 || responseCode == 502 || responseCode == 503) {
                        retryCount++
                        if (retryCount < 2) kotlinx.coroutines.delay(2000.milliseconds)
                    } else {
                        val errorMsg = try {
                            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        } catch (_: Exception) {
                            ""
                        }
                        withContext(Dispatchers.Main) {
                            if (!isSilent) {
                                _updateStatus.value = "Update check failed ($responseCode)"
                                if (errorMsg.contains("rate limit", ignoreCase = true)) {
                                    Toast.makeText(
                                        context,
                                        "GitHub API Rate limit exceeded.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                        success = true
                    }
                    connection.disconnect()
                } catch (_: Exception) {
                    retryCount++
                    if (retryCount >= 2) {
                        withContext(Dispatchers.Main) {
                            if (!isSilent) _updateStatus.value =
                                "Network error while checking updates"
                        }
                    } else {
                        kotlinx.coroutines.delay(2000.milliseconds)
                    }
                }
            }
        }
    }

    fun fetchAllChangelogs() {
        _isFetchingChangelogs.value = true

        val cachedJson = prefs.getString(PREF_RELEASES_CACHE, "")
        if (!cachedJson.isNullOrEmpty()) {
            try {
                val releases = org.json.JSONArray(cachedJson)
                val changelogs = mutableListOf<Pair<String, String>>()
                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    changelogs.add(
                        release.getString("tag_name") to release.optString(
                            "body",
                            "No release notes provided."
                        )
                    )
                }
                _fullChangelogs.value = changelogs
            } catch (_: Exception) {
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            var retryCount = 0
            var success = false

            while (retryCount < 2 && !success) {
                try {
                    val url =
                        java.net.URL("https://api.github.com/repos/ShivamXD6/ROM-Shifter/releases")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty(
                        "Accept",
                        "application/json, application/vnd.github.v3+json"
                    )
                    connection.setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                    )

                    val cachedEtag = prefs.getString(PREF_GITHUB_ETAG, "")
                    if (!cachedEtag.isNullOrEmpty()) {
                        connection.setRequestProperty("If-None-Match", cachedEtag)
                    }

                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000
                    connection.instanceFollowRedirects = true

                    val responseCode = connection.responseCode
                    if (responseCode == 200 || responseCode == 304) {
                        val responseJson = if (responseCode == 200) {
                            val etag = connection.getHeaderField("ETag")
                            val body = connection.inputStream.bufferedReader().use { it.readText() }
                            if (etag != null) {
                                prefs.edit {
                                    putString(PREF_GITHUB_ETAG, etag)
                                    putString(PREF_RELEASES_CACHE, body)
                                }
                            }
                            body
                        } else {
                            prefs.getString(PREF_RELEASES_CACHE, "[]") ?: "[]"
                        }

                        val releases = org.json.JSONArray(responseJson)
                        val changelogs = mutableListOf<Pair<String, String>>()

                        for (i in 0 until releases.length()) {
                            val release = releases.getJSONObject(i)
                            val tagName = release.getString("tag_name")
                            val body = release.optString("body", "No release notes provided.")
                            changelogs.add(tagName to body)
                        }

                        withContext(Dispatchers.Main) {
                            _fullChangelogs.value = changelogs
                            _isFetchingChangelogs.value = false
                        }
                        success = true
                    } else if (responseCode == 504 || responseCode == 502 || responseCode == 503) {
                        retryCount++
                        if (retryCount < 2) kotlinx.coroutines.delay(2000.milliseconds)
                    } else {
                        withContext(Dispatchers.Main) { _isFetchingChangelogs.value = false }
                        success = true
                    }
                    connection.disconnect()
                } catch (_: Exception) {
                    retryCount++
                    if (retryCount >= 2) {
                        withContext(Dispatchers.Main) { _isFetchingChangelogs.value = false }
                    } else {
                        kotlinx.coroutines.delay(2000.milliseconds)
                    }
                }
            }
        }
    }

    private val notificationManager = NotificationManagerCompat.from(application)

    companion object {
        private const val CHANNEL_PROGRESS_ID = "rom_shifter_progress_v2"
        private const val CHANNEL_ALERT_ID = "rom_shifter_alerts_v2"
        private const val NOTIFICATION_ID = 1001

        private const val PREF_GITHUB_ETAG = "github_releases_etag"
        private const val PREF_RELEASES_CACHE = "github_releases_json"
        private const val PREF_LAST_UPDATE_CHECK = "last_update_check_time"
    }

    init {
        createNotificationChannel()

        viewModelScope.launch(Dispatchers.IO) {
            if (!prefs.getBoolean("is_first_run", true)) {
                checkForUpdates(isSilent = true)
            }
        }

        checkRootAccess()
    }

    fun checkRootAccess() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isFetchingApps = true)
            Shell.getCachedShell()?.close()
            val isRooted = Shell.getShell().isRoot
            _uiState.value = _uiState.value.copy(hasRoot = isRooted, isFetchingApps = false)

            if (isRooted) {
                val whitelistJob = async {
                    Shell.cmd("dumpsys deviceidle whitelist +build.bytes.romshifter").exec()
                }

                val engineJob = async {
                    val success = BackendInstaller.installEngine(getApplication())
                    if (success) prefs.edit { putBoolean("is_engine_installed", true) }
                    BackendInstaller.backupSelf(getApplication(), _savedPath.value)
                }

                val cleanupJob = async {
                    val commands = arrayOf(
                        "mkdir -p /data/local/tmp",
                        "chmod 1777 /data/local/tmp",
                        "chown shell:shell /data/local/tmp",
                        "rm -rf /data/local/tmp/shifter_apps /data/local/tmp/shifter_targets.txt",
                        "mkdir -p /data/local/tmp/shifter_apps",
                        "chmod 777 /data/local/tmp/shifter_apps",
                        "chown shell:shell /data/local/tmp/shifter_apps",
                        "pm grant ${getApplication<Application>().packageName} android.permission.PACKAGE_USAGE_STATS",
                        "appops set ${getApplication<Application>().packageName} GET_USAGE_STATS allow"
                    )
                    Shell.cmd(*commands).exec()
                }

                whitelistJob.await()
                engineJob.await()
                cleanupJob.await()
            }
        }
    }

    private fun createNotificationChannel() {
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

    private fun updateProgressNotification(
        title: String,
        content: String,
        progress: Int = -1,
        max: Int = 100
    ) {
        if (ContextCompat.checkSelfPermission(
                getApplication(),
                "android.permission.POST_NOTIFICATIONS"
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {

            val parts = content.split("\n")
            val mainText = parts[0]
            val subText = if (parts.size > 1) parts[1] else null

            val displayContent = if (progress in 0..100) "$mainText  •  $progress%" else mainText

            val builder = NotificationCompat.Builder(getApplication(), CHANNEL_PROGRESS_ID)
                .setSmallIcon(R.drawable.ic_home)
                .setContentTitle(title)
                .setContentText(displayContent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .setOngoing(true)

            if (subText != null) {
                builder.setStyle(
                    NotificationCompat.BigTextStyle().bigText("$displayContent\n$subText")
                )
            }

            if (progress in 0..100) {
                builder.setProgress(max, progress, false)
            } else {
                builder.setProgress(0, 0, true)
            }

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun showCompletionNotification(title: String, content: String) {
        if (ContextCompat.checkSelfPermission(
                getApplication(),
                "android.permission.POST_NOTIFICATIONS"
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            val builder = NotificationCompat.Builder(getApplication(), CHANNEL_ALERT_ID)
                .setSmallIcon(R.drawable.ic_home)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

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
        _flashActions.value = emptyList()
        _uiState.value = _uiState.value.copy(
            flashWizardStep = 1,
            flashWipePartitions = setOf("dalvik", "cache"),
            flashFormatData = false,
            flashRebootOption = "system"
        )
    }

    fun flashWizardStepBack() {
        val currentStep = _uiState.value.flashWizardStep
        if (currentStep > 1) {
            _uiState.value = _uiState.value.copy(flashWizardStep = currentStep - 1)
        } else {
            closeFlashWizard()
        }
    }

    fun closeFlashWizard() {
        _uiState.value = _uiState.value.copy(flashWizardStep = 0)
    }

    fun removeAction(index: Int) {
        val l = _flashActions.value.toMutableList()
        if (index in l.indices) {
            l.removeAt(index)
            _flashActions.value = l
        }
    }

    fun moveActionUp(index: Int) {
        if (index > 0) {
            val l = _flashActions.value.toMutableList()
            val item = l.removeAt(index)
            l.add(index - 1, item)
            _flashActions.value = l
        }
    }

    fun moveActionDown(index: Int) {
        val l = _flashActions.value.toMutableList()
        if (index < l.size - 1) {
            val item = l.removeAt(index)
            l.add(index + 1, item)
            _flashActions.value = l
        }
    }

    fun processSelectedZips(uris: List<Uri>, append: Boolean = false) {
        _uiState.value = _uiState.value.copy(isProcessingZips = true)
        viewModelScope.launch(Dispatchers.IO) {
            val currentZips =
                if (append) _flashActions.value.filterIsInstance<FlashAction.InstallZip>()
                    .map { it.zip } else emptyList()
            val newZips = FlashManager.processZips(uris, currentZips, append, getApplication())
            
            withContext(Dispatchers.Main) {
                val actions = mutableListOf<FlashAction>()

                if (!append && _uiState.value.flashWipePartitions.isNotEmpty()) {
                    actions.add(FlashAction.Wipe(_uiState.value.flashWipePartitions))
                }

                if (append) {
                    val existing = _flashActions.value.toMutableList()
                    newZips.filter { nz -> _flashActions.value.none { it is FlashAction.InstallZip && it.zip.path == nz.path } }
                        .forEach { existing.add(FlashAction.InstallZip(it)) }
                    _flashActions.value = existing
                } else {
                    newZips.forEach { actions.add(FlashAction.InstallZip(it)) }
                    if (_uiState.value.flashFormatData) {
                        actions.add(FlashAction.FormatData)
                    }
                    _flashActions.value = actions
                }
                
                _uiState.value = _uiState.value.copy(
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

    fun setFlashRebootOption(option: String) {
        _uiState.value = _uiState.value.copy(flashRebootOption = option)
    }

    fun generateOrsAndProceed() {
        viewModelScope.launch(Dispatchers.IO) {
            FlashManager.generateOrsAndProceed(
                _flashActions.value,
                _uiState.value.flashRebootOption
            )
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(flashWizardStep = 4)
            }
        }
    }

    fun restartFlashWizard() {
        viewModelScope.launch(Dispatchers.IO) {
            FlashManager.restartFlashWizard(); withContext(Dispatchers.Main) {
            _flashActions.value = emptyList()
            _uiState.value = _uiState.value.copy(
                flashWizardStep = 1,
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
            Shell.cmd("mkdir -p \"$newPath\" && touch \"$newPath/.shifter_dir\"")
                .exec()
            _savedPath.value = newPath
            BackendInstaller.backupSelf(getApplication(), newPath)
            withContext(Dispatchers.Main) { onSuccess() }
        }
    }

    fun autoDetectShifterFolder(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val searchCmd =
                "find /data/media/0 /mnt/media_rw -maxdepth 5 -type f -name \".shifter_dir\" 2>/dev/null | head -n 1"
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
                    viewModelScope.launch {
                        BackendInstaller.backupSelf(
                            getApplication(),
                            detected
                        )
                    }
                    Toast.makeText(
                        getApplication(),
                        "Auto-detected folder at: $detected",
                        Toast.LENGTH_SHORT
                    ).show()
                    onResult(true)
                } else {
                    Toast.makeText(
                        getApplication(),
                        "No existing Shifter folder found. Please select manually.",
                        Toast.LENGTH_SHORT
                    ).show()
                    onResult(false)
                }
            }
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
            progress = -1
        )
        updateProgressNotification(title, "Starting Process...", -1)

        val selectedItems = mutableListOf<String>()
        if (doSms) selectedItems.add("SMS")
        if (doCall) selectedItems.add("Call Logs")
        if (doContacts) selectedItems.add("Contacts")
        val itemsProcessed = if (selectedItems.isNotEmpty()) selectedItems.joinToString(", ") else "No data selected"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                NativeManager.runOperation(
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

                    showCompletionNotification(finalMsg, itemsProcessed)
                    _uiState.value = _uiState.value.copy(
                        isRunning = false,
                        currentAction = finalMsg,
                        currentStep = itemsProcessed,
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

            if (doSms) Shell.cmd("rm -f '$path/Native/Messages.shift'").exec()
            if (doCall) Shell.cmd("rm -f '$path/Native/CallLogs.shift'").exec()
            if (doContacts) Shell.cmd("rm -f '$path/Native/Contacts.shift'").exec()

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Selected Native Backups Deleted",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    suspend fun isMagisk(): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("[ -d /data/adb/magisk ] && echo YES").exec().out.joinToString("")
            .trim() == "YES"
    }

    suspend fun canSystemize(): Boolean = withContext(Dispatchers.IO) {
        if (isMagisk()) return@withContext true
        val check =
            Shell.cmd("[ -d /data/adb/modules/mountify ] || [ -d /data/adb/modules/meta-overlayfs ] || [ -d /data/adb/metamodule ] && echo YES")
                .exec().out.joinToString("").trim()
        return@withContext check == "YES"
    }

    fun toggleSystemApps() {
        _uiState.value = _uiState.value.copy(showSystemApps = !_uiState.value.showSystemApps)
    }

    fun toggleActionFilter() {
        val mode = _uiState.value.migratorMode
        val currentState = _uiState.value.actionFilterState

        val newState = if (mode == MigratorMode.BACKUP_APPS || mode == MigratorMode.RESTORE_APPS) {
            (currentState + 1) % 3
        } else {
            if (currentState == 1) 2 else 1
        }

        when (mode) {
            MigratorMode.DEBLOAT -> {
                if (newState == 2 && _appList.value.none { !it.isInstalled }) {
                    _uiState.value = _uiState.value.copy(isFetchingApps = true)
                    viewModelScope.launch {
                        val uninstalled = MigratorManager.fetchAppsList(
                            getApplication(),
                            _savedPath.value,
                            "Uninstalled",
                            true,
                            _appList.value,
                            isRestore = false
                        )
                        _appList.value = uninstalled
                        _uiState.value = _uiState.value.copy(
                            actionFilterState = newState,
                            isFetchingApps = false
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(actionFilterState = newState)
                }
            }

            MigratorMode.SYSTEMIZE -> {
                if (!_uiState.value.systemAppsFetched && newState == 2) {
                    _uiState.value = _uiState.value.copy(isFetchingApps = true)
                    viewModelScope.launch {
                        val sys = MigratorManager.fetchAppsList(
                            getApplication(),
                            _savedPath.value,
                            "System",
                            true,
                            _appList.value,
                            isRestore = false
                        )
                        _appList.value = sys
                        _uiState.value = _uiState.value.copy(
                            systemAppsFetched = true,
                            actionFilterState = newState,
                            isFetchingApps = false
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(actionFilterState = newState)
                }
            }

            else -> {
                _uiState.value = _uiState.value.copy(actionFilterState = newState)
            }
        }
    }

    fun setMigratorMode(mode: MigratorMode) {
        val showSysApps =
            (mode == MigratorMode.DEBLOAT || mode == MigratorMode.RESTORE_APPS || mode == MigratorMode.MANAGE || mode == MigratorMode.SYSTEMIZE)

        _appList.value = emptyList()
        _uiState.value = _uiState.value.copy(
            migratorMode = mode,
            progress = 0,
            searchQuery = "",
            currentStep = "",
            showUserApps = true,
            showSystemApps = showSysApps,
            systemAppsFetched = false,
            actionFilterState = if (mode == MigratorMode.BACKUP_APPS || mode == MigratorMode.RESTORE_APPS || mode == MigratorMode.MANAGE) 0 else 1,
            globalComponents = setOf(1, 2, 3, 4, 5)
        )

        when (mode) {
            MigratorMode.BACKUP_APPS -> fetchAppsList("AllInstalled")
            MigratorMode.RESTORE_APPS -> {
                MigratorManager.clearCache(); fetchAppsList("AllBackups")
            }

            MigratorMode.DEBLOAT -> fetchAppsList("AllInstalled")
            MigratorMode.MANAGE -> {
                MigratorManager.clearCache(); fetchAppsList("AllBackups")
            }

            MigratorMode.SYSTEMIZE -> fetchAppsList("AllInstalled")
            else -> {}
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setForceRemove(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(forceRemoveEnabled = enabled)
    }

    fun toggleGlobalComponent(id: Int) {
        val current = _uiState.value.globalComponents.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _uiState.value = _uiState.value.copy(globalComponents = current)
    }

    fun toggleAppSelection(packageName: String) {
        val currentList = _appList.value.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            currentList[index] =
                currentList[index].copy(isSelected = !currentList[index].isSelected)
            _appList.value = currentList
        }
    }

    fun smartSelect(visibleApps: List<AppInfo>) {
        val state = _uiState.value
        val visiblePackages = visibleApps.map { it.packageName }.toSet()

        fun isSmartMatch(app: AppInfo): Boolean {
            return when (state.migratorMode) {
                MigratorMode.BACKUP_APPS -> app.backupTime != "No backup on device"
                MigratorMode.RESTORE_APPS -> !app.isInstalled
                MigratorMode.MANAGE -> app.isInstalled
                MigratorMode.DEBLOAT -> state.actionFilterState == 1 && app.backupTime != "No backup on device"
                else -> false
            }
        }

        val matches = visibleApps.filter { isSmartMatch(it) }
        val allMatchesSelected = matches.isNotEmpty() && matches.all { it.isSelected }

        val updatedList = _appList.value.map { app ->
            if (visiblePackages.contains(app.packageName) && isSmartMatch(app)) {
                app.copy(isSelected = !allMatchesSelected)
            } else app
        }
        _appList.value = updatedList
    }

    fun selectAllVisibleApps(select: Boolean, visibleApps: List<AppInfo>) {
        val visiblePackageNames = visibleApps.map { it.packageName }.toSet()
        val updatedList = _appList.value.map {
            if (visiblePackageNames.contains(it.packageName)) it.copy(isSelected = select) else it
        }
        _appList.value = updatedList
    }

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(
            currentStep = "",
            progress = 0
        )
    }

    private fun fetchAppsList(type: String, append: Boolean = false) {
        _uiState.value = _uiState.value.copy(isFetchingApps = true, currentAction = "Fetching apps list...")

        viewModelScope.launch(Dispatchers.IO) {
            val apps = MigratorManager.fetchAppsList(
                getApplication(),
                _savedPath.value,
                type,
                append,
                _appList.value,
                isRestore = _uiState.value.migratorMode == MigratorMode.RESTORE_APPS
            )
            withContext(Dispatchers.Main) {
                _appList.value = apps
                _uiState.value = _uiState.value.copy(
                    isFetchingApps = false,
                )
                if (type == "System" || type == "RestoreSystem" || type == "AllBackups") {
                    _uiState.value = _uiState.value.copy(systemAppsFetched = true)
                }
            }
        }
    }

    fun runDynamicOperation() {
        val state = _uiState.value
        val apps = _appList.value
        val selectedApps =
            apps.filter { it.isSelected && (it.isSystem && state.showSystemApps || !it.isSystem && state.showUserApps || state.actionFilterState == 2) }
        if (selectedApps.isEmpty()) return

        val initText = when (state.migratorMode) {
            MigratorMode.RESTORE_APPS -> "Restoring..."
            MigratorMode.DEBLOAT -> if (state.actionFilterState == 2) "Restoring..." else "Debloating..."
            MigratorMode.SYSTEMIZE -> if (state.actionFilterState == 2) "De-Systemizing..." else "Systemizing..."
            MigratorMode.MANAGE -> "Deleting..."
            else -> "Backing Up..."
        }

        _uiState.value = state.copy(
            isRunning = true,
            currentAction = initText,
            currentStep = "Preparing Data...",
            progress = -1
        )

        viewModelScope.launch {
            val updateProgress: (String, String, Int) -> Unit = { action, step, prog ->
                viewModelScope.launch(Dispatchers.Main) {
                    val safeAction = action.ifEmpty { "ROM Shifter" }
                    updateProgressNotification(safeAction, step, prog)

                    _uiState.value = _uiState.value.copy(
                        currentAction = action.ifEmpty { _uiState.value.currentAction },
                        currentStep = step.ifEmpty { _uiState.value.currentStep },
                        progress = if (prog >= 0) prog else _uiState.value.progress
                    )
                }
            }

            val onComplete: (String, String) -> Unit = { action, step ->
                viewModelScope.launch(Dispatchers.Main) {
                    val finalStepText = when (state.migratorMode) {
                        MigratorMode.MANAGE -> {
                            val cleanStep =
                                if (step == "Freed up storage space.") "Freed space" else step
                            if (cleanStep.isNotBlank()) "$cleanStep | Apps: ${selectedApps.size}" else "Apps: ${selectedApps.size}"
                        }

                        MigratorMode.DEBLOAT, MigratorMode.SYSTEMIZE -> step
                        else -> {
                            if (step.isNotBlank()) "$step | Apps: ${selectedApps.size}" else "Apps: ${selectedApps.size}"
                        }
                    }

                    showCompletionNotification(action, finalStepText)

                    when (state.migratorMode) {
                        MigratorMode.DEBLOAT, MigratorMode.SYSTEMIZE -> {
                            MigratorManager.clearCache()
                            fetchAppsList("AllInstalled")
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
                        currentStep = finalStepText,
                        progress = 100
                    )
                }
            }

            when (state.migratorMode) {
                MigratorMode.DEBLOAT -> {
                    ToolsManager.runDebloatOperation(
                        context = getApplication(),
                        selectedApps = selectedApps,
                        isRestore = state.actionFilterState == 2,
                        forceRemove = state.forceRemoveEnabled,
                        updateLog = {},
                        updateProgress = updateProgress,
                        onComplete = onComplete
                    )
                }

                MigratorMode.SYSTEMIZE -> {
                    if (state.actionFilterState == 2) {
                        val appPaths = selectedApps.joinToString(" ") {
                            val safeLabel = it.label.replace(Regex("[^a-zA-Z0-9_]"), "")
                            "'/data/adb/modules/ROM-Shifter/system/product/app/$safeLabel' '/data/adb/modules_update/ROM-Shifter/system/product/app/$safeLabel'"
                        }
                        Shell.cmd("rm -rf $appPaths").exec()
                        updateProgress("De-Systemizing", "Removed from module folder", 100)
                        onComplete("De-Systemize Complete!", "Reboot required to apply changes.")
                    } else {
                        ToolsManager.runSystemizeOperation(
                            context = getApplication(),
                            selectedApps = selectedApps,
                            updateLog = {},
                            updateProgress = updateProgress,
                            onComplete = onComplete
                        )
                    }
                }

                else -> {
                    MigratorManager.runDynamicOperation(
                        context = getApplication(),
                        state = state,
                        selectedApps = selectedApps,
                        currentPath = _savedPath.value,
                        updateProgress = updateProgress,
                        onComplete = onComplete
                    )
                }
            }
        }
    }

    fun refreshCurrentList() {
        MigratorManager.clearCache()
        val currentMode = _uiState.value.migratorMode
        if (currentMode != MigratorMode.MENU) {
            when (currentMode) {
                MigratorMode.BACKUP_APPS -> fetchAppsList("AllInstalled")
                MigratorMode.RESTORE_APPS -> fetchAppsList("AllBackups")
                MigratorMode.DEBLOAT, MigratorMode.SYSTEMIZE -> fetchAppsList("AllInstalled")
                MigratorMode.MANAGE -> fetchAppsList("AllBackups")
            }
        }
    }
}
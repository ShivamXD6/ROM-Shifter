package build.bytes.romshifter

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import build.bytes.romshifter.models.AppInfo
import build.bytes.romshifter.models.AppInstallInfo
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.FlashAction
import build.bytes.romshifter.models.MigratorMode
import build.bytes.romshifter.utils.BackendInstaller
import build.bytes.romshifter.utils.DeviceManager
import build.bytes.romshifter.utils.FlashManager
import build.bytes.romshifter.utils.MigratorManager
import build.bytes.romshifter.utils.SettingsManager
import build.bytes.romshifter.utils.ToolsManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.log10
import kotlin.math.pow
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

    private val _availableDeviceBackups = MutableStateFlow<Set<String>>(emptySet())
    val availableDeviceBackups: StateFlow<Set<String>> = _availableDeviceBackups.asStateFlow()

    fun refreshDeviceBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            val backups = DeviceManager.getAvailableBackups(_savedPath.value)
            _availableDeviceBackups.value = backups
        }
    }
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

                            val currentVersionCode = BuildConfig.VERSION_CODE.toLong()

                            val cleanLatest =
                                tagName.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L

                            withContext(Dispatchers.Main) {
                                if (cleanLatest > currentVersionCode) {
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
    private var lastNotificationUpdateTime = 0L

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
        val currentTime = System.currentTimeMillis()
        if (progress != 100 && progress != -1 && currentTime - lastNotificationUpdateTime < 250) {
            return
        }
        lastNotificationUpdateTime = currentTime

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

    fun analyzeApps(uris: List<Uri>, showInstaller: Boolean = false, isIntent: Boolean = false) {
        val context = getApplication<Application>()
        val initialApps = uris.mapIndexed { index, uri ->
            val resolvedPath = FlashManager.getPathFromUri(context, uri) ?: ""

            AppInstallInfo(
                path = resolvedPath,
                uriString = uri.toString(),
                status = "Analyzing",
                label = "Analyzing app ${index + 1}..."
            )
        }

        _uiState.update {
            it.copy(
                isAnalyzingApps = true,
                showAppInstaller = if (showInstaller) true else it.showAppInstaller,
                isInstallerIntent = isIntent,
                batchInstallApps = if (isIntent) initialApps else it.batchInstallApps + initialApps,
                totalInstallTimeSeconds = 0L
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val tempDir = File(context.cacheDir, "app_analysis")
            tempDir.mkdirs()

            coroutineScope {
                initialApps.forEach { initialApp ->
                    launch(Dispatchers.IO) {
                        val startTime = System.currentTimeMillis()
                        val uri = Uri.parse(initialApp.uriString)
                        val originalPath = initialApp.path

                        val analysisDir = File(
                            tempDir,
                            "analysis_${System.currentTimeMillis()}_${(0..100000).random()}"
                        )
                        analysisDir.mkdirs()

                        try {
                            val stagingDir = "/data/local/tmp/shifter_install"
                            Shell.cmd("mkdir -p $stagingDir && chmod 777 $stagingDir").exec()

                            val ext = getExtensionFromUri(context, uri)
                            val tempInputFile = File(analysisDir, "input_app.$ext")
                            
                            var success = false
                            var localAnalysisPath = ""

                            if (originalPath.isNotEmpty()) {
                                try {
                                    val source = File(originalPath)
                                    if (source.exists() && source.canRead()) {
                                        localAnalysisPath = originalPath
                                        success = true
                                    }
                                } catch (_: Exception) {
                                }
                            }

                            if (!success) {
                                try {
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        tempInputFile.outputStream()
                                            .use { output -> input.copyTo(output) }
                                    }
                                    if (tempInputFile.exists() && tempInputFile.length() > 0) {
                                        localAnalysisPath = tempInputFile.absolutePath
                                        success = true
                                    }
                                } catch (e: Exception) {
                                    Log.e("ROMShifter_Batch", "Staging Error: ${e.message}")
                                }
                            }

                            if (success) {
                                var analysisApkPath = localAnalysisPath

                                if (ext != "apk" && ext.isNotEmpty() && ext in listOf(
                                        "apks",
                                        "xapk",
                                        "apkm",
                                        "zip"
                                    )
                                ) {
                                    val unzipDir = File(analysisDir, "unzipped")
                                    unzipDir.mkdirs()

                                    var extractedBaseFile: File? = null
                                    try {
                                        java.util.zip.ZipFile(File(localAnalysisPath)).use { zip ->
                                            val entries = zip.entries().asSequence()
                                            val targetEntry =
                                                entries.find { it.name.endsWith("base.apk", true) }
                                                    ?: zip.entries().asSequence()
                                                        .filter { it.name.endsWith(".apk", true) }
                                                        .maxByOrNull { it.size }

                                            if (targetEntry != null) {
                                                val targetFile = File(
                                                    unzipDir,
                                                    targetEntry.name.substringAfterLast("/")
                                                )
                                                zip.getInputStream(targetEntry).use { input ->
                                                    targetFile.outputStream()
                                                        .use { output -> input.copyTo(output) }
                                                }
                                                extractedBaseFile = targetFile
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                            "ROMShifter_Batch",
                                            "Fast extraction failed: ${e.message}"
                                        )
                                    }

                                    extractedBaseFile?.let {
                                        analysisApkPath = it.absolutePath
                                    }
                                }

                                val info = pm.getPackageArchiveInfo(analysisApkPath, 0)
                                if (info != null && info.applicationInfo != null) {
                                    val appInfo = info.applicationInfo!!
                                    appInfo.sourceDir = analysisApkPath
                                    appInfo.publicSourceDir = analysisApkPath

                                    val label = appInfo.loadLabel(pm).toString()
                                    val pkgName = info.packageName
                                    val vCode = PackageInfoCompat.getLongVersionCode(info)

                                    val targetSdkInt = appInfo.targetSdkVersion
                                    val verName = getAndroidVersion(targetSdkInt)
                                    val targetSdk =
                                        if (verName.startsWith("API")) verName else "Android $verName"

                                    val arch = getArchitecture(analysisApkPath)
                                    val iconPath = getIconPath(pm, appInfo, pkgName)

                                    val finalPath =
                                        "$stagingDir/${pkgName}_${System.currentTimeMillis()}.${ext}"
                                    Shell.cmd("cp '$localAnalysisPath' '$finalPath' && chmod 666 '$finalPath'")
                                        .exec()

                                    val updatedApp = initialApp.copy(
                                        label = label,
                                        packageName = pkgName,
                                        path = finalPath,
                                        version = info.versionName ?: "Unknown",
                                        versionCode = vCode,
                                        size = formatPreciseSize(File(localAnalysisPath).length()),
                                        installedVersion = try {
                                            pm.getPackageInfo(pkgName, 0).versionName
                                        } catch (_: Exception) {
                                            null
                                        },
                                        installedVersionCode = try {
                                            PackageInfoCompat.getLongVersionCode(
                                                pm.getPackageInfo(
                                                    pkgName,
                                                    0
                                                )
                                            )
                                        } catch (_: Exception) {
                                            null
                                        },
                                        isInstalled = try {
                                            pm.getPackageInfo(pkgName, 0); true
                                        } catch (_: Exception) {
                                            false
                                        },
                                        iconPath = iconPath,
                                        status = "Pending",
                                        isAnalysisComplete = true,
                                        targetSdk = targetSdk,
                                        architecture = arch
                                    )

                                    withContext(Dispatchers.Main) {
                                        _uiState.update { state ->
                                            val existing =
                                                state.batchInstallApps.find { it.packageName == pkgName && it.isAnalysisComplete }
                                            if (existing != null) {
                                                if (vCode > existing.versionCode) {
                                                    state.copy(batchInstallApps = state.batchInstallApps.filterNot { it.path == existing.path }
                                                        .map { if (it.uriString == initialApp.uriString) updatedApp else it })
                                                } else {
                                                    state.copy(batchInstallApps = state.batchInstallApps.filterNot { it.uriString == initialApp.uriString })
                                                }
                                            } else {
                                                state.copy(batchInstallApps = state.batchInstallApps.map { if (it.uriString == initialApp.uriString) updatedApp else it })
                                            }
                                        }
                                    }
                                    Log.d(
                                        "ROMShifter_Batch",
                                        "Total Analysis Time: ${System.currentTimeMillis() - startTime}ms"
                                    )
                                } else {
                                    throw Exception("Failed to parse package info")
                                }
                            } else {
                                throw Exception("Failed to access file data")
                            }
                        } catch (e: Exception) {
                            Log.e("ROMShifter_Batch", "Analysis CRASHED: ${e.message}")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Skipping invalid file: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                _uiState.update { state ->
                                    val newList =
                                        state.batchInstallApps.filterNot { it.uriString == initialApp.uriString }
                                    state.copy(
                                        batchInstallApps = newList,
                                        showAppInstaller = if (newList.isEmpty()) false else state.showAppInstaller
                                    )
                                }
                            }
                        } finally {
                            Shell.cmd("rm -rf '${analysisDir.absolutePath}'").exec()
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isAnalyzingApps = false) }
            }
        }
    }

    private fun getExtensionFromUri(context: Context, uri: Uri): String {
        val resolvedPath = FlashManager.getPathFromUri(context, uri)
        if (!resolvedPath.isNullOrEmpty()) {
            val ext = resolvedPath.substringAfterLast(".", "").lowercase()
            if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) return ext
        }

        try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name =
                        cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                    val ext = name.substringAfterLast(".", "").lowercase()
                    if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) return ext
                }
            }
        } catch (_: Exception) {
        }

        val mime = context.contentResolver.getType(uri)
        if (mime != null) {
            if (mime.contains("android.package-archive")) return "apk"
            android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?.let { return it }
        }

        return "apk"
    }

    private fun formatPreciseSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        return String.format(
            java.util.Locale.US,
            "%.2f %s",
            bytes / 1024.0.pow(digitGroups.toDouble()),
            units[digitGroups]
        )
    }

    private fun getAndroidVersion(api: Int): String {
        return when (api) {
            37 -> "17"; 36 -> "16"; 35 -> "15"; 34 -> "14"; 33 -> "13"; 32 -> "12.1"; 31 -> "12"
            30 -> "11"; 29 -> "10"; 28 -> "9"; 27 -> "8.1"; 26 -> "8"; 25 -> "7.1"; 24 -> "7"
            else -> "API $api"
        }
    }

    private fun getArchitecture(path: String): String {
        if (path.isEmpty()) return "Universal"
        return try {
            val out = Shell.cmd("su -c \"unzip -l '$path'\"").exec().out.joinToString("\n")
            when {
                out.contains("lib/arm64-v8a/") -> "arm64-v8a"
                out.contains("lib/armeabi-v7a/") -> "armeabi-v7a"
                out.contains("lib/x86_64/") -> "x86_64"
                out.contains("lib/x86/") -> "x86"
                else -> "Universal"
            }
        } catch (_: Exception) {
            "Universal"
        }
    }

    private fun getIconPath(
        pm: PackageManager,
        appInfo: android.content.pm.ApplicationInfo,
        pkgName: String
    ): String? {
        val icon = appInfo.loadIcon(pm)
        val iconDir = File(getApplication<Application>().cacheDir, "icons")
        iconDir.mkdirs()
        val iconFile = File(iconDir, "$pkgName.png")

        try {
            val bitmap = if (icon is BitmapDrawable) {
                icon.bitmap
            } else {
                val b = createBitmap(
                    icon.intrinsicWidth.coerceAtLeast(1),
                    icon.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(b)
                icon.setBounds(0, 0, canvas.width, canvas.height)
                icon.draw(canvas)
                b
            }
            FileOutputStream(iconFile).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            return iconFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun executeBatchInstall() {
        val allApps = _uiState.value.batchInstallApps
        val apps = allApps.filter { it.isSelected && it.isAnalysisComplete }
        if (apps.isEmpty()) return

        val startTime = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isRunning = true,
                currentAction = "Installing Apps...",
                progress = -1,
                installStartTime = startTime
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val targetFile = File(context.cacheDir, "install_targets.txt")
            val targetsContent =
                apps.joinToString("\n") { "${it.path}|${it.packageName}|${it.label}" }
            Log.d("ROMShifter_Batch", "--- App Installation Hand-off ---")
            Log.d("ROMShifter_Batch", "Targets Content:\n$targetsContent")
            targetFile.writeText(targetsContent)

            Shell.cmd("chmod 666 ${targetFile.absolutePath}").exec()

            val activeLabels = mutableListOf<String>()

            Shell.cmd("su -mm -c 'sh /data/adb/Shifter/ROM-Shifter.sh --install-apps ${targetFile.absolutePath}'")
                .to(object : java.util.ArrayList<String>() {
                    override fun add(element: String): Boolean {
                        if (element.startsWith("INFO:STEP|MSG:INSTALLING|")) {
                            val pkg = element.substringAfter("PKG:").substringBefore("|")
                            val label = element.substringAfter("LABEL:")
                            viewModelScope.launch(Dispatchers.Main) {
                                synchronized(activeLabels) {
                                    if (!activeLabels.contains(label)) activeLabels.add(label)
                                }
                                _uiState.update { state ->
                                    val currentText = synchronized(activeLabels) {
                                        activeLabels.take(3).joinToString(", ")
                                    }
                                    state.copy(
                                        currentStep = "Installing $currentText...",
                                        batchInstallApps = state.batchInstallApps.map {
                                            if (it.packageName == pkg) it.copy(status = "Installing") else it
                                        }
                                    )
                                }
                            }
                        } else if (element.startsWith("ACTION:INSTALL_DONE|PKG:")) {
                            val pkg = element.substringAfter("PKG:")
                            viewModelScope.launch(Dispatchers.Main) {
                                _uiState.update { state ->
                                    val labelToRemove =
                                        state.batchInstallApps.find { it.packageName == pkg }?.label
                                    synchronized(activeLabels) {
                                        activeLabels.remove(labelToRemove)
                                    }
                                    val currentText = synchronized(activeLabels) {
                                        activeLabels.take(3).joinToString(", ")
                                    }
                                    state.copy(
                                        currentStep = if (currentText.isNotEmpty()) "Installing $currentText..." else "Finishing up...",
                                        batchInstallApps = state.batchInstallApps.map {
                                            if (it.packageName == pkg) it.copy(status = "Done") else it
                                        }
                                    )
                                }
                            }
                        } else if (element.startsWith("ACTION:INSTALL_ERROR|PKG:")) {
                            val pkg = element.substringAfter("PKG:").substringBefore("|")
                            viewModelScope.launch(Dispatchers.Main) {
                                _uiState.update { state ->
                                    val labelToRemove =
                                        state.batchInstallApps.find { it.packageName == pkg }?.label
                                    synchronized(activeLabels) {
                                        activeLabels.remove(labelToRemove)
                                    }
                                    val currentText = synchronized(activeLabels) {
                                        activeLabels.take(3).joinToString(", ")
                                    }
                                    state.copy(
                                        currentStep = if (currentText.isNotEmpty()) "Installing $currentText..." else "Error occurred during some installs.",
                                        batchInstallApps = state.batchInstallApps.map {
                                            if (it.packageName == pkg) it.copy(status = "Error") else it
                                        }
                                    )
                                }
                            }
                        }
                        return super.add(element)
                    }
                }).exec()

            val endTime = System.currentTimeMillis()
            val totalSeconds = (endTime - startTime) / 1000

            withContext(Dispatchers.Main) {
                val finalApps =
                    _uiState.value.batchInstallApps.filter { it.isSelected && it.isAnalysisComplete }
                val successCount = finalApps.count { it.status == "Done" }
                val errorCount = finalApps.count { it.status == "Error" }

                val title =
                    if (errorCount > 0) "Installation Finished with Errors" else "Installation Complete"
                val content =
                    if (errorCount > 0) "Installed $successCount, Failed $errorCount apps in ${totalSeconds}s."
                    else "Installed $successCount apps in ${totalSeconds}s."

                showCompletionNotification(title, content)
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        currentAction = title,
                        currentStep = content,
                        progress = 100,
                        totalInstallTimeSeconds = totalSeconds
                    )
                }
                targetFile.delete()
                Shell.cmd("rm -rf /data/local/tmp/shifter_install").exec()
            }
        }
    }

    fun closeAppInstaller(onFinish: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("rm -rf /data/local/tmp/shifter_install").exec()
        }
        
        val wasIntent = _uiState.value.isInstallerIntent
        _uiState.update {
            it.copy(
                showAppInstaller = false,
                batchInstallApps = emptyList(),
                isInstallerIntent = false,
                totalInstallTimeSeconds = 0L
            )
        }
        if (wasIntent) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(100.milliseconds)
                onFinish?.invoke()
            }
        }
    }

    fun toggleAppInstallSelection(path: String) {
        _uiState.update { state ->
            state.copy(batchInstallApps = state.batchInstallApps.map {
                if (it.path == path) it.copy(isSelected = !it.isSelected) else it
            })
        }
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
                _uiState.update { it.copy(flashWizardStep = 4) }
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
            Shell.cmd("su -c 'mkdir -p \"$newPath\" && touch \"$newPath/.shifter_dir\"'")
                .exec()
            _savedPath.value = newPath
            BackendInstaller.backupSelf(getApplication(), newPath)
            updateStorageInfo()
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

    fun setDefaultSmsHandled() {
        _uiState.update { it.copy(requestDefaultSms = false) }
    }

    fun runDeviceDataOperation(
        context: Context,
        isBackup: Boolean,
        doSms: Boolean,
        doCall: Boolean,
        doContacts: Boolean,
        doWifi: Boolean,
        doWallpaper: Boolean,
        doBluetooth: Boolean
    ) {
        val title = if (isBackup) "Backing up Device Data" else "Restoring Device Data"
        _uiState.value = _uiState.value.copy(
            isRunning = true,
            currentAction = title,
            currentStep = "Processing via ContentResolver...",
            progress = -1
        )
        updateProgressNotification(title, "Starting Process...", -1)

        val selectedItems = mutableListOf<String>()
        if (doSms) selectedItems.add("Messages")
        if (doCall) selectedItems.add("Calls")
        if (doContacts) selectedItems.add("Contacts")
        if (doWifi) selectedItems.add("Wi-Fi")
        if (doWallpaper) selectedItems.add("Wallpaper")
        if (doBluetooth) selectedItems.add("Bluetooth")
        val itemsProcessed = if (selectedItems.isNotEmpty()) selectedItems.joinToString(", ") else "No data selected"

        viewModelScope.launch(Dispatchers.IO) {
            var originalDefaultSms: String? = null
            val pkg = context.packageName
            try {
                if (doSms && !isBackup) {
                    originalDefaultSms =
                        android.provider.Telephony.Sms.getDefaultSmsPackage(context)
                    if (originalDefaultSms != pkg) {
                        Log.d("MainViewModel", "Setting ROM Shifter as default SMS app...")
                        val rootSuccess = DeviceManager.setDefaultSmsAppRoot(pkg)
                        if (!rootSuccess) {
                            Log.d(
                                "MainViewModel",
                                "Root failed, requesting user to set default SMS app"
                            )
                            _uiState.update { it.copy(requestDefaultSms = true) }
                            var timeout = 60
                            while (android.provider.Telephony.Sms.getDefaultSmsPackage(context) != pkg && timeout > 0) {
                                kotlinx.coroutines.delay(1000.milliseconds)
                                timeout--
                            }
                            _uiState.update { it.copy(requestDefaultSms = false) }
                            if (android.provider.Telephony.Sms.getDefaultSmsPackage(context) != pkg) {
                                throw Exception("User did not set ROM Shifter as default SMS app")
                            }
                        }
                    }
                }

                DeviceManager.runOperation(
                    context,
                    isBackup,
                    doSms,
                    doCall,
                    doContacts,
                    doWifi,
                    doWallpaper,
                    doBluetooth,
                    _savedPath.value
                ) { step, prog ->
                    _uiState.value = _uiState.value.copy(currentStep = step, progress = prog)
                    updateProgressNotification(title, step, prog)
                }
                withContext(Dispatchers.Main) {
                    val finalMsg = if (isBackup) "Backup Complete!" else "Restore Complete!"

                    refreshDeviceBackups()
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
            } finally {
                if (doSms && !isBackup) {
                    val restoreTo =
                        if (originalDefaultSms != null && originalDefaultSms != pkg) originalDefaultSms else "com.google.android.apps.messaging"
                    Log.d("MainViewModel", "Restoring default SMS app to: $restoreTo")
                    DeviceManager.setDefaultSmsAppRoot(restoreTo)
                }
            }
        }
    }

    fun deleteDeviceBackups(
        context: Context,
        doSms: Boolean,
        doCall: Boolean,
        doContacts: Boolean,
        doWifi: Boolean,
        doWallpaper: Boolean,
        doBluetooth: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = _savedPath.value

            if (doSms) Shell.cmd("su -c \"rm -f '$path/Device/Messages.shift'\"").exec()
            if (doCall) Shell.cmd("su -c \"rm -f '$path/Device/CallLogs.shift'\"").exec()
            if (doContacts) Shell.cmd("su -c \"rm -f '$path/Device/Contacts.shift'\"").exec()
            if (doWifi) Shell.cmd("su -c \"rm -f '$path/Device/Wi-Fi.shift'\"").exec()
            if (doWallpaper) Shell.cmd("su -c \"rm -f '$path/Device/Wallpaper.shift'\"").exec()
            if (doBluetooth) Shell.cmd("su -c \"rm -f '$path/Device/Bluetooth.shift'\"").exec()

            refreshDeviceBackups()
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Selected Device Backups Deleted",
                    Toast.LENGTH_SHORT
                ).show()
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
            Shell.cmd("su -c '[ -d /data/adb/modules/mountify ] || [ -d /data/adb/modules/meta-overlayfs ] || [ -d /data/adb/metamodule ] && echo YES'")
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
                            isManage = false,
                            includeOverhead = false
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
                            isManage = false,
                            includeOverhead = false
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
            globalComponents = setOf(1, 2, 3, 4, 5),
            keepDebloatData = false
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

    fun toggleGlobalComponent(id: Int) {
        val current = _uiState.value.globalComponents.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        val newState = _uiState.value.copy(globalComponents = current)
        _uiState.value = newState

        if (newState.migratorMode == MigratorMode.RESTORE_APPS) {
            val updatedList = _appList.value.map { app ->
                if (app.isSelected && app.activeComponents != null) {
                    app.copy(activeComponents = app.availableInBackup intersect current)
                } else app
            }
            _appList.value = updatedList
        }
        
        updateStorageInfo()
    }

    fun toggleAppSelection(packageName: String) {
        val currentList = _appList.value.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            val app = currentList[index]
            currentList[index] = app.copy(
                isSelected = !app.isSelected,
                activeComponents = null
            )
            _appList.value = currentList
            updateStorageInfo()
        }
    }

    fun smartSelect(visibleApps: List<AppInfo>) {
        val state = _uiState.value
        val visiblePackages = visibleApps.map { it.packageName }.toSet()

        fun isSmartMatch(app: AppInfo): Boolean {
            return when (state.migratorMode) {
                MigratorMode.BACKUP_APPS -> app.availableInBackup.isNotEmpty()
                MigratorMode.RESTORE_APPS -> !app.isInstalled
                MigratorMode.MANAGE -> app.isInstalled
                MigratorMode.DEBLOAT -> state.actionFilterState == 1 && app.availableInBackup.contains(
                    1
                )
                else -> false
            }
        }

        val matches = visibleApps.filter { isSmartMatch(it) }
        val allMatchesSelected = matches.isNotEmpty() && matches.all { it.isSelected }

        val updatedList = _appList.value.map { app ->
            if (visiblePackages.contains(app.packageName) && isSmartMatch(app)) {
                val shouldSelect = !allMatchesSelected
                val comps = if (shouldSelect) {
                    when (state.migratorMode) {
                        MigratorMode.BACKUP_APPS -> app.availableInBackup
                        MigratorMode.RESTORE_APPS -> app.availableInBackup intersect state.globalComponents
                        MigratorMode.MANAGE, MigratorMode.DEBLOAT -> app.availableInBackup
                        else -> null
                    }
                } else null

                app.copy(
                    isSelected = shouldSelect,
                    activeComponents = comps
                )
            } else app
        }
        _appList.value = updatedList
        updateStorageInfo()
    }

    fun selectAllVisibleApps(select: Boolean, visibleApps: List<AppInfo>) {
        val visiblePackageNames = visibleApps.map { it.packageName }.toSet()
        val updatedList = _appList.value.map {
            if (visiblePackageNames.contains(it.packageName)) it.copy(
                isSelected = select,
                activeComponents = null
            ) else it
        }
        _appList.value = updatedList
        updateStorageInfo()
    }

    fun updateStorageInfo() {
        val state = _uiState.value
        val allApps = _appList.value
        val selectedApps = allApps.filter { it.isSelected }

        var totalBackupsKb = 0L

        val selectedKb = if (state.migratorMode == MigratorMode.MANAGE ||
            state.migratorMode == MigratorMode.DEBLOAT ||
            state.migratorMode == MigratorMode.SYSTEMIZE
        ) {
            if (state.migratorMode == MigratorMode.MANAGE) {
                totalBackupsKb = allApps.sumOf { it.appSizeKb + it.dataSizeKb + it.mediaSizeKb }
            }
            if (state.migratorMode == MigratorMode.DEBLOAT && state.keepDebloatData && state.actionFilterState == 1) {
                selectedApps.sumOf { it.appSizeKb }
            } else {
                selectedApps.sumOf { it.appSizeKb + it.dataSizeKb + it.mediaSizeKb }
            }
        } else {
            var tempKb = 0L
            selectedApps.forEach { app ->
                val activeComps = app.activeComponents ?: state.globalComponents
                tempKb += 25L
                if (activeComps.contains(3)) tempKb += 5L
                if (activeComps.contains(5)) tempKb += 1L
                if (activeComps.contains(1)) tempKb += app.appSizeKb
                if (activeComps.contains(2)) tempKb += app.dataSizeKb
                if (activeComps.contains(4)) tempKb += app.mediaSizeKb
            }
            tempKb
        }

        val targetPath =
            if (state.migratorMode == MigratorMode.RESTORE_APPS ||
                state.migratorMode == MigratorMode.DEBLOAT ||
                state.migratorMode == MigratorMode.SYSTEMIZE
            ) "/data" else _savedPath.value

        val freeKb = MigratorManager.getAvailableSpaceKb(targetPath)
        val totalKb = MigratorManager.getTotalSpaceKb(targetPath)
        val usedKb = if (totalKb > freeKb) totalKb - freeKb else 0L

        _uiState.value = _uiState.value.copy(
            totalRequiredKb = selectedKb,
            totalBackupsSizeKb = totalBackupsKb,
            storageUsedKb = usedKb,
            storageFreeKb = freeKb
        )
    }

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(
            currentStep = "",
            progress = 0
        )
    }

    private fun fetchAppsList(type: String, append: Boolean = false) {
        _uiState.value =
            _uiState.value.copy(isFetchingApps = true, currentAction = "Fetching apps list...")

        viewModelScope.launch(Dispatchers.IO) {
            val apps = MigratorManager.fetchAppsList(
                getApplication(),
                _savedPath.value,
                type,
                append,
                _appList.value,
                isManage = _uiState.value.migratorMode == MigratorMode.MANAGE,
                includeOverhead = _uiState.value.migratorMode == MigratorMode.BACKUP_APPS || _uiState.value.migratorMode == MigratorMode.RESTORE_APPS
            )
            withContext(Dispatchers.Main) {
                _appList.value = apps
                _uiState.value = _uiState.value.copy(
                    isFetchingApps = false,
                )
                updateStorageInfo()
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
                val safeAction = action.ifEmpty { "ROM Shifter" }
                updateProgressNotification(safeAction, step, prog)

                _uiState.update { currentState ->
                    currentState.copy(
                        currentAction = action.ifEmpty { currentState.currentAction },
                        currentStep = step.ifEmpty { currentState.currentStep },
                        progress = prog
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
                        keepData = state.keepDebloatData,
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
                        Shell.cmd("su -c \"rm -rf $appPaths\"").exec()
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
        val state = _uiState.value
        if (state.migratorMode != MigratorMode.MENU) {
            when (state.migratorMode) {
                MigratorMode.BACKUP_APPS -> fetchAppsList("AllInstalled")
                MigratorMode.RESTORE_APPS -> fetchAppsList("AllBackups")
                MigratorMode.DEBLOAT -> {
                    val type = if (state.actionFilterState == 2) "Uninstalled" else "AllInstalled"
                    fetchAppsList(type)
                }

                MigratorMode.SYSTEMIZE -> fetchAppsList("AllInstalled")
                MigratorMode.MANAGE -> fetchAppsList("AllBackups")
            }
        }
    }

    fun toggleKeepDebloatData() {
        _uiState.update { it.copy(keepDebloatData = !it.keepDebloatData) }
    }
}

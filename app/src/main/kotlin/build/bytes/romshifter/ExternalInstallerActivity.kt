package build.bytes.romshifter

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.bytes.romshifter.models.AppInstallInfo
import build.bytes.romshifter.ui.components.AppInstallerBody
import build.bytes.romshifter.ui.components.AppInstallerHeader
import build.bytes.romshifter.ui.components.BatchInstallerBody
import build.bytes.romshifter.ui.components.BatchInstallerHeader
import build.bytes.romshifter.ui.theme.ROMShifterTheme

class ExternalInstallerActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val dynamicColor by viewModel.dynamicColor.collectAsState()
            val appState by viewModel.uiState.collectAsState()

            ROMShifterTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier
                        .wrapContentWidth()
                        .wrapContentHeight()
                        .width(340.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    if (appState.showAppInstaller && appState.batchInstallApps.isNotEmpty()) {
                        if (appState.batchInstallApps.size == 1 && !appState.isRunning && appState.totalInstallTimeSeconds == 0L) {
                            val app = appState.batchInstallApps[0]
                            Column(modifier = Modifier.padding(24.dp)) {
                                AppInstallerHeader(app)
                                Spacer(Modifier.height(16.dp))
                                AppInstallerBody(app)
                                Spacer(Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (!app.status.startsWith("Analyzing")) {
                                        TextButton(onClick = { viewModel.closeAppInstaller { finish() } }) {
                                            Text("Cancel")
                                        }
                                    }
                                    if (app.isAnalysisComplete) {
                                        Spacer(Modifier.width(8.dp))
                                        Button(onClick = { viewModel.executeBatchInstall() }) {
                                            Text(getButtonText(app))
                                        }
                                    }
                                }
                            }
                        } else {
                            val apps = appState.batchInstallApps
                            val selectedCount =
                                apps.count { it.isSelected && it.isAnalysisComplete }

                            Column(modifier = Modifier.padding(24.dp)) {
                                BatchInstallerHeader(
                                    appState.isRunning,
                                    appState.isAnalyzingApps,
                                    appState.totalInstallTimeSeconds
                                )
                                Spacer(Modifier.height(16.dp))
                                BatchInstallerBody(
                                    apps = apps,
                                    isRunning = appState.isRunning,
                                    currentStep = appState.currentStep,
                                    totalTime = appState.totalInstallTimeSeconds,
                                    onToggleSelect = { viewModel.toggleAppInstallSelection(it) }
                                )
                                Spacer(Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (!appState.isRunning && !appState.isAnalyzingApps && appState.totalInstallTimeSeconds == 0L) {
                                        TextButton(onClick = { viewModel.closeAppInstaller { finish() } }) {
                                            Text("Cancel")
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Button(
                                            onClick = { viewModel.executeBatchInstall() },
                                            enabled = selectedCount > 0
                                        ) {
                                            Text("Install Selected ($selectedCount)")
                                        }
                                    } else if (appState.totalInstallTimeSeconds > 0L) {
                                        Button(onClick = { viewModel.closeAppInstaller { finish() } }) {
                                            Text("Close")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val allUris = when (intent.action) {
            Intent.ACTION_VIEW, "android.intent.action.INSTALL_PACKAGE" -> {
                intent.data?.let { listOf(it) } ?: emptyList()
            }

            Intent.ACTION_SEND -> {
                val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                stream?.let { listOf(it) } ?: emptyList()
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val streams = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        android.net.Uri::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                streams ?: emptyList()
            }

            else -> emptyList()
        }

        val supportedExtensions = setOf("apk", "apks", "xapk", "apkm")
        val uris = allUris.filter { uri ->
            val mimeType = contentResolver.getType(uri)?.lowercase() ?: ""
            val displayName = getDisplayName(uri)?.lowercase() ?: ""
            val uriPath = uri.path?.lowercase() ?: ""

            supportedExtensions.any { displayName.endsWith(".$it") } ||
                    supportedExtensions.any { uriPath.endsWith(".$it") } ||
                    mimeType.contains("android.package-archive") ||
                    (mimeType.contains("zip") && supportedExtensions.any { displayName.endsWith(".$it") }) ||
                    intent.dataString?.lowercase()
                        ?.let { ds -> supportedExtensions.any { ds.endsWith(".$it") } } == true
        }

        if (uris.isEmpty() && allUris.isNotEmpty()) {
            android.widget.Toast.makeText(
                this,
                "Unsupported file format",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        if (uris.isEmpty()) return

        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                try {
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }
            }
        }

        viewModel.analyzeApps(uris, showInstaller = true, isIntent = true)
    }

    private fun getDisplayName(uri: android.net.Uri): String? {
        if (uri.scheme == "content") {
            try {
                contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                    }
                }
            } catch (_: Exception) {
            }
        }
        return uri.path?.substringAfterLast('/')
    }

    private fun getButtonText(app: AppInstallInfo): String {
        if (!app.isInstalled) return "Install"
        return when {
            app.installedVersionCode != null && app.versionCode > app.installedVersionCode -> "Update"
            app.installedVersionCode != null && app.versionCode < app.installedVersionCode -> "Downgrade"
            app.installedVersionCode == app.versionCode -> "Reinstall"
            else -> "Install"
        }
    }
}

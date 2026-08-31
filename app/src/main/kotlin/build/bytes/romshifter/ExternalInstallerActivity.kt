package build.bytes.romshifter

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import build.bytes.romshifter.ui.components.AppInstallerDialog
import build.bytes.romshifter.ui.components.BatchInstallerDialog
import build.bytes.romshifter.ui.theme.ROMShifterTheme

class ExternalInstallerActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val dynamicColor by viewModel.dynamicColor.collectAsState()
            val appState by viewModel.uiState.collectAsState()

            ROMShifterTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (appState.showAppInstaller && appState.batchInstallApps.isNotEmpty()) {
                        if (appState.batchInstallApps.size == 1 && !appState.isRunning && appState.totalInstallTimeSeconds == 0L) {
                            AppInstallerDialog(
                                app = appState.batchInstallApps[0],
                                onDismiss = { viewModel.closeAppInstaller { finish() } },
                                onInstall = { viewModel.executeBatchInstall() }
                            )
                        } else {
                            BatchInstallerDialog(
                                apps = appState.batchInstallApps,
                                isRunning = appState.isRunning,
                                isAnalyzing = appState.isAnalyzingApps,
                                currentStep = appState.currentStep,
                                totalTime = appState.totalInstallTimeSeconds,
                                onInstall = { viewModel.executeBatchInstall() },
                                onCancel = { viewModel.closeAppInstaller { finish() } },
                                onToggleSelect = { viewModel.toggleAppInstallSelection(it) }
                            )
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
            val path = uri.path?.lowercase() ?: ""
            supportedExtensions.any { path.endsWith(".$it") } ||
                    contentResolver.getType(uri)?.contains("android.package-archive") == true ||
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
}

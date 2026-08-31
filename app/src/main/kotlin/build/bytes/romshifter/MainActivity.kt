package build.bytes.romshifter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import build.bytes.romshifter.ui.screens.MainScreen
import build.bytes.romshifter.ui.theme.ROMShifterTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val dynamicColor by viewModel.dynamicColor.collectAsState()
            val systemDark = isSystemInDarkTheme()

            val isDark = when (themeMode) {
                1 -> false
                2, 3, 4 -> true
                else -> systemDark
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = this@MainActivity.window
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
                }
            }

            ROMShifterTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(viewModel = viewModel)
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
        val action = intent.action
        val data = intent.data ?: return
        val type = intent.type

        if (action == Intent.ACTION_VIEW) {
            val path = data.path?.lowercase() ?: ""
            if (path.endsWith(".zip") || type == "application/zip") {
                viewModel.setTab(1)
                viewModel.openFlashWizard()
                viewModel.processSelectedZips(listOf(data), append = true)
            }
        }
    }
}

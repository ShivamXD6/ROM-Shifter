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
            val systemDark = isSystemInDarkTheme()

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = this@MainActivity.window
                    
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !systemDark
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !systemDark
                }
            }

            
            ROMShifterTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }

        if (intent?.action == Intent.ACTION_VIEW && intent.type == "application/zip") {
            intent.data?.let { uri ->
                viewModel.openFlashWizard()
                viewModel.processSelectedZips(listOf(uri), append = true)
            }
        }
    }
}
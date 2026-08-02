package build.bytes.romshifter

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import build.bytes.romshifter.ui.screens.MainScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("shifter_prefs", MODE_PRIVATE)
        setContent {
            val context = LocalContext.current
            var isDarkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", false)) }
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> { if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
                isDarkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = this@MainActivity.window
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDarkTheme
                }
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(viewModel, isDarkTheme) {
                        isDarkTheme = !isDarkTheme
                        prefs.edit { putBoolean("dark_theme", isDarkTheme) }
                    }
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
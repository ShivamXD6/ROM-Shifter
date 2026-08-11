package build.bytes.romshifter.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SecurityUpdateGood
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.MigratorMode
import build.bytes.romshifter.ui.components.MenuCard
import kotlinx.coroutines.launch

@Composable
fun ExtrasTab(appState: AppState, viewModel: MainViewModel) {
    val context = LocalContext.current
    var showMetaWarningDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showMetaWarningDialog) {
        AlertDialog(
            shape = RoundedCornerShape(30.dp),
            onDismissRequest = { showMetaWarningDialog = false },
            title = { Text("Meta-OverlayFS Required", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error) },
            text = { Text("You are not using Magisk. To systemize apps via KernelSU or APatch, you must have the Meta-OverlayFS module installed. Please download and flash it in your root manager, then try again.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KernelSU-Modules-Repo/meta-overlayfs/releases"))
                    context.startActivity(intent)
                    showMetaWarningDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Download Module")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMetaWarningDialog = false }) { Text("Cancel") }
            }
        )
    }

    AnimatedContent(
        targetState = appState.migratorMode !in listOf(MigratorMode.DEBLOAT, MigratorMode.SYSTEMIZE),
        transitionSpec = {
            val goingBack = !initialState && targetState 
            if (goingBack) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {

                slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { it } togetherWith fadeOut(tween(250))
            }
        },
        label = "ExtrasTransition"
    ) { isMenu ->
        if (!isMenu) {
            MigratorActionScreen(appState, viewModel)
        } else {

            Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(16.dp))
                    MenuCard("Debloat / Restore Apps", Icons.Default.DeleteSweep, "Uninstall and restore System/User apps") { viewModel.setMigratorMode(MigratorMode.DEBLOAT) }
                    MenuCard("Systemize User Apps", Icons.Default.SecurityUpdateGood, "Make user apps un-uninstallable securely") {
                        scope.launch {
                            if (viewModel.canSystemize()) {
                                viewModel.setMigratorMode(MigratorMode.SYSTEMIZE)
                            } else {
                                showMetaWarningDialog = true
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}
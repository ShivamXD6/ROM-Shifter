package build.bytes.romshifter.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SecurityUpdateGood
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.models.AppInfo
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.MigratorMode
import build.bytes.romshifter.ui.components.MenuCard
import kotlinx.coroutines.launch

@Composable
fun ToolsTab(appState: AppState, appList: List<AppInfo>, viewModel: MainViewModel) {
    val context = LocalContext.current
    var showMetaWarningDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showMetaWarningDialog) {
        AlertDialog(
            shape = RoundedCornerShape(30.dp),
            onDismissRequest = { showMetaWarningDialog = false },
            title = {
                Text(
                    "Mountify Required",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    "You are not using Magisk. To systemize apps via KernelSU or APatch, you must have the Mountify module installed. Please download and flash it in your root manager, then try again.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/backslashxx/mountify/releases".toUri()
                    )
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
        label = "ToolsTransition"
    ) { isMenu ->
        if (!isMenu) {
            MigratorActionScreen(appState, appList, viewModel)
        } else {

            Column(modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp)) {
                Column(modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())) {
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
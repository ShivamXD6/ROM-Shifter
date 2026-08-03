package build.bytes.romshifter.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SecurityUpdateGood
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.MigratorMode
import build.bytes.romshifter.ui.components.MenuCard
import build.bytes.romshifter.ui.components.SectionHeader

@Composable
fun ExtrasTab(appState: AppState, viewModel: MainViewModel) {

    if (appState.migratorMode in listOf(MigratorMode.DEBLOAT, MigratorMode.SYSTEMIZE)) {
        MigratorActionScreen(appState, viewModel)
    } else {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SectionHeader("Advanced Utilities", "Root tools and device modifications")
                MenuCard("Debloat / Restore Apps", Icons.Default.DeleteSweep, "Uninstall and restore System/User apps") { viewModel.setMigratorMode(MigratorMode.DEBLOAT) }
                MenuCard("Systemize User Apps", Icons.Default.SecurityUpdateGood, "Make user apps un-uninstallable securely") { viewModel.setMigratorMode(MigratorMode.SYSTEMIZE) }
            }

            AnimatedVisibility(visible = appState.isRunning || appState.currentStep.isNotEmpty(), enter = expandVertically(), exit = shrinkVertically()) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (appState.isRunning) {
                            CircularProgressIndicator(
                                progress = { appState.progress / 100f },
                                modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.onPrimaryContainer,
                                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f), strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(appState.currentAction, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            if (appState.currentStep.isNotEmpty()) {
                                Text(appState.currentStep, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
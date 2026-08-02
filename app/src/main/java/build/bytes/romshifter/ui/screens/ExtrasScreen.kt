package build.bytes.romshifter.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.MigratorMode
import build.bytes.romshifter.ui.components.MenuCard
import build.bytes.romshifter.ui.components.SectionHeader

@Composable
fun ExtrasTab(appState: AppState, viewModel: MainViewModel) {
    var metaInstalled by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { metaInstalled = viewModel.isMetaModuleInstalled() }

    if (appState.migratorMode in listOf(MigratorMode.DEBLOAT, MigratorMode.SYSTEMIZE)) {
        MigratorActionScreen(appState, viewModel)
    } else {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Advanced Utilities", "Root tools and device modifications")
            MenuCard("Debloat / Restore Apps", Icons.Default.DeleteSweep, "Uninstall and restore System/User apps") { viewModel.setMigratorMode(MigratorMode.DEBLOAT) }

            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("Systemizer", "Magisk systemless module generation")
            MenuCard("Systemize User Apps", Icons.Default.SecurityUpdateGood, "Make user apps un-uninstallable securely without resizing partitions") {
                if (metaInstalled) viewModel.setMigratorMode(MigratorMode.SYSTEMIZE)
            }
            if (!metaInstalled) {
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Meta-OverlayFS Required", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Modern Android requires the Meta module to mount apps systemlessly. Please install it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.checkAndInstallMetaModule() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Install Module Now") }
                    }
                }
            }
        }
    }
}
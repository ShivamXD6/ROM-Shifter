package build.bytes.romshifter.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.ui.components.MenuCard
import build.bytes.romshifter.ui.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashTab(context: Context, viewModel: MainViewModel) {
    val appState by viewModel.uiState.collectAsState()
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var selectedPartition by remember { mutableStateOf("") }
    var restoreMode by remember { mutableStateOf("backup") }
    var customImgPath by remember { mutableStateOf("") }
    var allPartitions by remember { mutableStateOf(listOf<String>()) }
    var backedUpImages by remember { mutableStateOf(listOf<String>()) }
    var isAppending by remember { mutableStateOf(false) }

    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.processSelectedZips(uris, isAppending)
    }
    val imgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            build.bytes.romshifter.utils.FlashManager.getPathFromUri(context, uri)?.let { customImgPath = it }
        }
    }

    if (showBackupDialog) {
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { allPartitions = viewModel.getAllPartitions() } }
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Backup Partition", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Select a partition to securely extract to local storage:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (allPartitions.isEmpty()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                            items(allPartitions) { part ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { selectedPartition = part }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selectedPartition == part, onClick = { selectedPartition = part })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(part, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (selectedPartition.isNotBlank()) {
                        viewModel.runLiveOperation("--live-backup", selectedPartition) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        showBackupDialog = false
                    }
                }) { Text("Backup") }
            },
            dismissButton = { TextButton(onClick = { showBackupDialog = false }) { Text("Cancel") } }
        )
    }

    if (showRestoreDialog) {
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { allPartitions = viewModel.getAllPartitions(); backedUpImages = viewModel.getBackedUpImages() } }
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Flash Partition", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("Warning: Live flashing modifies raw hardware partitions. Incorrect images will hard brick your device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        FilterChip(selected = restoreMode == "backup", onClick = { restoreMode = "backup"; selectedPartition = "" }, label = { Text("From Backup") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = restoreMode == "custom", onClick = { restoreMode = "custom"; selectedPartition = "" }, label = { Text("Custom .img") })
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (restoreMode == "backup") {
                        if (backedUpImages.isEmpty()) {
                            Text("No images found in Live-Partition folder.", color = MaterialTheme.colorScheme.error)
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                                items(backedUpImages) { img ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Row(modifier = Modifier.weight(1f).clickable { selectedPartition = img }, verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = selectedPartition == img, onClick = { selectedPartition = img })
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(img, fontWeight = FontWeight.Medium)
                                        }
                                        IconButton(onClick = {
                                            viewModel.deleteLivePartitionImage(img)
                                            backedUpImages = backedUpImages.filter { it != img }
                                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedButton(onClick = { imgLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (customImgPath.isEmpty()) "Select .img File" else customImgPath.substringAfterLast("/"))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Target Partition:", style = MaterialTheme.typography.labelMedium)
                        LazyColumn(modifier = Modifier.fillMaxHeight(0.5f)) {
                            items(allPartitions) { part ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { selectedPartition = part }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selectedPartition == part, onClick = { selectedPartition = part })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(part, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (restoreMode == "backup" && selectedPartition.isNotBlank()) {
                        val partName = selectedPartition.substringBefore("_backup.img")
                        viewModel.runLiveOperation("--live-restore", partName, "${viewModel.savedPath.value}/Live-Partition/$selectedPartition") { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        showRestoreDialog = false
                    } else if (restoreMode == "custom" && customImgPath.isNotBlank() && selectedPartition.isNotBlank()) {
                        viewModel.runLiveOperation("--live-restore", selectedPartition, customImgPath) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        showRestoreDialog = false
                    }
                }) { Text("Flash Image") }
            },
            dismissButton = { TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") } }
        )
    }

    if (appState.flashWizardStep > 0) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            when (appState.flashWizardStep) {
                1 -> {
                    SectionHeader("Step 1: Wipe Mode", "Select partitions to clear before flashing")
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val wipeModes = listOf(
                                Triple(1, "Dirty (Dalvik, Cache)", "ex: flashing recovery or kernel"),
                                Triple(2, "Clean (+ System, Data)", "ex: flashing roms or gapps"),
                                Triple(3, "Format (+ Metadata, Format Data)", "ex: removing encryption or flashing firmware")
                            )
                            wipeModes.forEach { (mode, title, desc) ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.setFlashWipeMode(mode) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = appState.flashWipeMode == mode, onClick = { viewModel.setFlashWipeMode(mode) })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(title, fontWeight = FontWeight.Medium)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = { isAppending = false; zipLauncher.launch(arrayOf("application/zip")) }, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Next: Select ZIP Files") }
                }
                2 -> {
                    SectionHeader("Step 2: Review & Order", "Valid ZIPs have been safely ordered")
                    if (appState.isProcessingZips) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 40.dp))
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(appState.flashZips) { index, zip ->
                                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${index + 1}", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(zip.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Category: ${zip.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column {
                                            IconButton(onClick = { viewModel.moveZipUp(index) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.KeyboardArrowUp, null) }
                                            IconButton(onClick = { viewModel.moveZipDown(index) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.KeyboardArrowDown, null) }
                                        }
                                        IconButton(onClick = { viewModel.removeZip(index) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { isAppending = true; zipLauncher.launch(arrayOf("application/zip")) }, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f).height(56.dp)) { Text("Add More ZIPs") }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(onClick = { viewModel.checkLockscreenAndProceed() }, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f).height(56.dp)) { Text("Next") }
                        }
                    }
                }
                3 -> {
                    SectionHeader("Step 3: Security Check", "Recovery cannot flash encrypted data")
                    if (appState.hasLockscreen) {
                        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Lock, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Screen Lock Detected!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text("You must remove your PIN/Pattern before flashing to prevent FRP lock or decryption issues.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer)) { Text("Open Settings") }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(onClick = { viewModel.checkLockscreenAndProceed() }, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("I've Removed It - Verify Again") }
                    } else {
                        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Storage is Decrypted!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(onClick = { viewModel.generateOrsAndProceed() }, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Next: Finalize") }
                    }
                }
                4 -> {
                    SectionHeader("Ready to Flash", "Press button to reboot to recovery and flash")
                    if (appState.currentAction == "Rebooting to Recovery...") {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 40.dp))
                    } else {
                        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Everything is done, you can now use the button to start flashing.", textAlign = TextAlign.Center)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { viewModel.executeFlashNow() }, shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Reboot to Recovery & Flash Now") }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = { viewModel.restartFlashWizard() }, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Restart Wizard") }
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SectionHeader("Auto Flash", "Queue and flash ZIPs automatically")
                MenuCard("Launch Wizard", Icons.Default.FlashOn, "Setup wipes and select ZIP files to flash") { viewModel.openFlashWizard() }

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("Live Partitions", "Read/Write raw images safely")
                MenuCard("Backup Partitions", Icons.Default.Save, "Extract partition images to local storage") { selectedPartition = ""; showBackupDialog = true }
                MenuCard("Flash Partitions", Icons.Default.SystemUpdateAlt, "Flash images directly to active slot") { backedUpImages = viewModel.getBackedUpImages(); selectedPartition = ""; customImgPath = ""; restoreMode = "backup"; showRestoreDialog = true }
            }
        }
    }
}
package build.bytes.romshifter.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.MigratorMode
import build.bytes.romshifter.ui.components.AppListItem
import build.bytes.romshifter.ui.components.MenuCard
import build.bytes.romshifter.ui.components.SectionHeader
import build.bytes.romshifter.ui.components.ShimmerAppListItem

@Composable
fun MigratorTab(appState: AppState, viewModel: MainViewModel) {
    if (appState.migratorMode == MigratorMode.MENU) {
        MigratorMenu(appState, viewModel)
    } else {
        MigratorActionScreen(appState, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigratorMenu(appState: AppState, viewModel: MainViewModel) {
    val context = LocalContext.current
    var showNativeBackupDialog by remember { mutableStateOf(false) }
    var showNativeRestoreDialog by remember { mutableStateOf(false) }
    var showPermissionWarning by remember { mutableStateOf(false) }
    var pendingNativeAction by remember { mutableStateOf<Pair<Boolean, Triple<Boolean, Boolean, Boolean>>?>(null) }

    var doSms by remember { mutableStateOf(true) }
    var doCall by remember { mutableStateOf(true) }
    var doContacts by remember { mutableStateOf(true) }

    if (showPermissionWarning) {
        AlertDialog(
            onDismissRequest = {
                showPermissionWarning = false
                Toast.makeText(context, "Permissions are required to process telephony data", Toast.LENGTH_SHORT).show()
            },
            title = { Text("Native Permissions Required", fontWeight = FontWeight.Bold) },
            text = { Text("ROM Shifter will automatically grant the necessary native Android permissions via root to access SMS, Call Logs, or Contacts based on your selection. Do you want to continue?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = {
                    showPermissionWarning = false
                    pendingNativeAction?.let { (isBackup, flags) ->
                        viewModel.runNativeDataOperation(context, isBackup, flags.first, flags.second, flags.third)
                    }
                }) { Text("Yes, Start") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionWarning = false
                    Toast.makeText(context, "Permissions are required to process telephony data", Toast.LENGTH_SHORT).show()
                }) { Text("No, Cancel") }
            }
        )
    }

    if (showNativeBackupDialog) {
        AlertDialog(
            onDismissRequest = { showNativeBackupDialog = false },
            title = { Text("Backup Native Data", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Select items to backup locally:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { doSms = !doSms }.fillMaxWidth()) {
                        Checkbox(checked = doSms, onCheckedChange = { doSms = it })
                        Text("SMS Messages")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { doCall = !doCall }.fillMaxWidth()) {
                        Checkbox(checked = doCall, onCheckedChange = { doCall = it })
                        Text("Call Logs")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { doContacts = !doContacts }.fillMaxWidth()) {
                        Checkbox(checked = doContacts, onCheckedChange = { doContacts = it })
                        Text("Contacts (vCard)")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (doSms || doCall || doContacts) {
                        pendingNativeAction = Pair(true, Triple(doSms, doCall, doContacts))
                        showPermissionWarning = true
                    }
                    showNativeBackupDialog = false
                }) { Text("Backup") }
            },
            dismissButton = { TextButton(onClick = { showNativeBackupDialog = false }) { Text("Cancel") } }
        )
    }

    if (showNativeRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showNativeRestoreDialog = false },
            title = { Text("Restore Native Data", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Select items to restore from storage:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { doSms = !doSms }.fillMaxWidth()) {
                        Checkbox(checked = doSms, onCheckedChange = { doSms = it })
                        Text("SMS Messages")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { doCall = !doCall }.fillMaxWidth()) {
                        Checkbox(checked = doCall, onCheckedChange = { doCall = it })
                        Text("Call Logs")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { doContacts = !doContacts }.fillMaxWidth()) {
                        Checkbox(checked = doContacts, onCheckedChange = { doContacts = it })
                        Text("Contacts (vCard)")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (doSms || doCall || doContacts) {
                        pendingNativeAction = Pair(false, Triple(doSms, doCall, doContacts))
                        showPermissionWarning = true
                    }
                    showNativeRestoreDialog = false
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { showNativeRestoreDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionHeader("Apps and Data Migrator", "Backup/Restore or Manage your Apps and Telephony Data")
            MenuCard("Backup Apps", Icons.Default.CloudUpload, "Backup system / user apps") { viewModel.setMigratorMode(MigratorMode.BACKUP_APPS) }
            MenuCard("Restore Apps", Icons.Default.RestorePage, "Restore Apps from Storage") { viewModel.setMigratorMode(MigratorMode.RESTORE_APPS) }
            MenuCard("Backup Telephony Data", Icons.Default.Sms, "Backup SMS, Calls, and Contacts") { showNativeBackupDialog = true }
            MenuCard("Restore Telephony Data", Icons.Default.SettingsPhone, "Restore Telephony Data from Storage") { showNativeRestoreDialog = true }
            MenuCard("Manage Backups", Icons.Default.Delete, "View and delete existing app backups") { viewModel.setMigratorMode(MigratorMode.MANAGE) }
            Spacer(modifier = Modifier.height(16.dp))
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MigratorActionScreen(appState: AppState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val filteredApps = appState.appList.filter {
        val matchesSearch = it.label.contains(appState.searchQuery, ignoreCase = true) || it.packageName.contains(appState.searchQuery, ignoreCase = true)
        val matchesType = (it.isSystem && appState.showSystemApps) || (!it.isSystem && appState.showUserApps)
        matchesSearch && (matchesType || appState.isRestoreDebloatMode)
    }

    var showLogsSheet by remember { mutableStateOf(false) }
    var showForceRemoveWarning by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val compIcons = mapOf(1 to Icons.Default.Android, 2 to Icons.Default.Storage, 3 to Icons.Default.Folder, 4 to Icons.Default.PermMedia, 5 to Icons.Default.Inventory, 6 to Icons.Default.Fingerprint)
    val compNames = mapOf(1 to "App", 2 to "Data", 3 to "ExtData", 4 to "Media", 5 to "OBB", 6 to "Android ID")

    if (showForceRemoveWarning) {
        AlertDialog(
            onDismissRequest = { showForceRemoveWarning = false },
            title = { Text("Danger: Physical Deletion", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("Force removing system apps will execute a raw 'rm -rf' command to physically delete the app directories from the device partition. They cannot be restored later using the standard Restore Debloated feature.\n\nProceed at your own risk.") },
            confirmButton = { Button(onClick = { viewModel.setForceRemove(true); showForceRemoveWarning = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("I Understand") } },
            dismissButton = { TextButton(onClick = { showForceRemoveWarning = false }) { Text("Cancel") } }
        )
    }

    if (showLogsSheet) {
        ModalBottomSheet(onDismissRequest = { showLogsSheet = false }) {
            Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.7f)) {
                Text("Execution Logs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1E1E1E)).padding(8.dp)) {
                    LazyColumn(reverseLayout = true) {
                        items(appState.logs.reversed()) { log -> Text(text = log, color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 1.dp)) }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))

        if (appState.migratorMode == MigratorMode.MANAGE) {
            Button(onClick = { viewModel.clearNativeDataBackups(context) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                Icon(Icons.Default.DeleteSweep, null)
                Spacer(Modifier.width(8.dp))
                Text("Clear Native Telephony Backups")
            }
        }

        if (appState.migratorMode == MigratorMode.BACKUP_APPS || appState.migratorMode == MigratorMode.RESTORE_APPS) {
            LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(compNames.entries.toList()) { entry ->
                    val isSelected = appState.globalComponents.contains(entry.key)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.toggleGlobalComponent(entry.key) },
                        label = { if(isSelected) Text(entry.value, style = MaterialTheme.typography.labelMedium) else Text("") },
                        leadingIcon = { compIcons[entry.key]?.let { Icon(it, null, modifier = Modifier.size(16.dp)) } },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = appState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search apps...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
                singleLine = true, shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.weight(1f).height(56.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Universally use the Hamburger icon for all screens
            Box {
                FilledTonalIconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(56.dp), shape = CircleShape) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (appState.migratorMode == MigratorMode.DEBLOAT) {
                        DropdownMenuItem(
                            text = { Text("Restore Debloated Apps") },
                            trailingIcon = { if (appState.isRestoreDebloatMode) Icon(Icons.Default.Check, null) },
                            onClick = { viewModel.toggleRestoreDebloatMode(); menuExpanded = false }
                        )
                        HorizontalDivider()
                    }

                    // This logic successfully hides the system/user toggles exactly when in Restore Apps mode
                    if (!appState.isRestoreDebloatMode && appState.migratorMode != MigratorMode.RESTORE_APPS) {
                        DropdownMenuItem(
                            text = { Text("Show User Apps") },
                            trailingIcon = { if (appState.showUserApps) Icon(Icons.Default.Check, null) },
                            onClick = { viewModel.toggleShowUserApps(!appState.showUserApps); menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Show System Apps") },
                            trailingIcon = { if (appState.showSystemApps) Icon(Icons.Default.Check, null) },
                            onClick = { viewModel.toggleSystemApps(); menuExpanded = false }
                        )
                    }

                    if (appState.migratorMode == MigratorMode.DEBLOAT && !appState.isRestoreDebloatMode) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Force Deletion (rm -rf)") },
                            trailingIcon = { if (appState.forceRemoveEnabled) Icon(Icons.Default.Check, null) },
                            onClick = {
                                if(!appState.forceRemoveEnabled) showForceRemoveWarning = true
                                else viewModel.setForceRemove(false)
                                menuExpanded = false
                            }
                        )
                    }

                    HorizontalDivider()
                    val allSelected = filteredApps.isNotEmpty() && filteredApps.all { it.isSelected }
                    DropdownMenuItem(
                        text = { Text(if (allSelected) "Deselect All" else "Select All") },
                        onClick = { viewModel.selectAllVisibleApps(!allSelected, filteredApps); menuExpanded = false }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            if (appState.isFetchingApps) {
                LazyColumn(modifier = Modifier.fillMaxSize()) { items(8) { ShimmerAppListItem() } }
            } else if (filteredApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No apps found.") }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredApps) { app ->
                        AppListItem(app = app, onToggleSelect = { pkgName -> viewModel.toggleAppSelection(pkgName) })
                    }
                    if (appState.isFetchingApps) { item { Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val selectedCount = appState.appList.count { it.isSelected }
        Button(
            onClick = { viewModel.runDynamicOperation() },
            enabled = !appState.isRunning && selectedCount > 0,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (appState.migratorMode == MigratorMode.MANAGE || (appState.migratorMode == MigratorMode.DEBLOAT && !appState.isRestoreDebloatMode)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp)
        ) {
            val act = when (appState.migratorMode) {
                MigratorMode.MANAGE -> "Delete"
                MigratorMode.DEBLOAT -> if (appState.isRestoreDebloatMode) "Restore" else "Debloat"
                MigratorMode.RESTORE_APPS -> "Restore"
                MigratorMode.SYSTEMIZE -> "Systemize"
                else -> "Backup"
            }
            Text(if (appState.isRunning) "Running..." else "$act $selectedCount Selected Apps", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))

        AnimatedVisibility(visible = appState.isRunning || appState.currentStep.isNotEmpty(), enter = expandVertically(), exit = shrinkVertically()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).clickable { showLogsSheet = true },
                shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (appState.isRunning) {
                        CircularProgressIndicator(progress = { appState.progress / 100f }, modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f), strokeWidth = 4.dp)
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
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Details", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}
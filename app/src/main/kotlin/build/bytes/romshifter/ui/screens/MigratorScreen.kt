package build.bytes.romshifter.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.MigratorMode
import build.bytes.romshifter.ui.components.AppListItem
import build.bytes.romshifter.ui.components.MenuCard
import build.bytes.romshifter.ui.components.ShimmerAppListItem

@Composable
fun MigratorTab(appState: AppState, viewModel: MainViewModel) {
    if (appState.migratorMode == MigratorMode.MENU) {
        MigratorMenu(appState, viewModel)
    } else {
        MigratorActionScreen(appState, viewModel)
    }
}

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
            onDismissRequest = { showPermissionWarning = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null) },
            title = { Text("Permissions Required") },
            text = { Text("ROM Shifter will automatically grant native Android permissions via root to access SMS, Call Logs, or Contacts. Do you want to continue?") },
            confirmButton = { Button(onClick = { showPermissionWarning = false; pendingNativeAction?.let { (isBackup, flags) -> viewModel.runNativeDataOperation(context, isBackup, flags.first, flags.second, flags.third) } }) { Text("Yes, Start") } },
            dismissButton = { TextButton(onClick = { showPermissionWarning = false }) { Text("Cancel") } }
        )
    }

    if (showNativeBackupDialog || showNativeRestoreDialog) {
        val isBackup = showNativeBackupDialog
        AlertDialog(
            onDismissRequest = { showNativeBackupDialog = false; showNativeRestoreDialog = false },
            icon = { Icon(if (isBackup) Icons.Default.CloudUpload else Icons.Default.SettingsPhone, null) },
            title = { Text(if (isBackup) "Telephony Data" else "Restore Telephony Data") },
            text = {
                Column {
                    // FIX: Removed unnecessary subtext
                    val options = listOf(
                        "SMS Messages" to doSms,
                        "Call Logs" to doCall,
                        "Contacts (vCard)" to doContacts
                    )

                    options.forEach { (label, state) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    when (label) {
                                        "SMS Messages" -> doSms = !doSms
                                        "Call Logs" -> doCall = !doCall
                                        "Contacts (vCard)" -> doContacts = !doContacts
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = state,
                                onCheckedChange = null,
                                thumbContent = { // FIX: Added Checked/Cross Icon
                                    Icon(
                                        imageVector = if (state) Icons.Filled.Check else Icons.Filled.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (doSms || doCall || doContacts) { pendingNativeAction = Pair(isBackup, Triple(doSms, doCall, doContacts)); showPermissionWarning = true }
                    showNativeBackupDialog = false; showNativeRestoreDialog = false
                }) { Text(if (isBackup) "Backup" else "Restore") }
            },
            dismissButton = { TextButton(onClick = { showNativeBackupDialog = false; showNativeRestoreDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(8.dp))
            MenuCard("Backup Apps", Icons.Default.CloudUpload, "Backup system / user apps") { viewModel.setMigratorMode(MigratorMode.BACKUP_APPS) }
            MenuCard("Restore Apps", Icons.Default.RestorePage, "Restore Apps from Storage") { viewModel.setMigratorMode(MigratorMode.RESTORE_APPS) }
            MenuCard("Backup Telephony Data", Icons.Default.Sms, "Backup SMS, Calls, and Contacts") { showNativeBackupDialog = true }
            MenuCard("Restore Telephony Data", Icons.Default.SettingsPhone, "Restore Telephony Data from Storage") { showNativeRestoreDialog = true }
            MenuCard("Manage Backups", Icons.Default.Delete, "View and delete existing app backups") { viewModel.setMigratorMode(MigratorMode.MANAGE) }

            Spacer(modifier = Modifier.height(120.dp))
        }

        AnimatedVisibility(visible = appState.isRunning || appState.currentStep.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (appState.isRunning) {
                        CircularProgressIndicator(progress = { appState.progress / 100f }, modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, strokeWidth = 3.dp)
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigratorActionScreen(appState: AppState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val isPrivileged by viewModel.isPrivilegedSystemize.collectAsState()
    val filteredApps by remember(appState.appList, appState.searchQuery, appState.showSystemApps, appState.showUserApps, appState.isRestoreDebloatMode) {
        derivedStateOf {
            appState.appList.filter {
                val matchesSearch = it.label.contains(appState.searchQuery, ignoreCase = true) || it.packageName.contains(appState.searchQuery, ignoreCase = true)
                val matchesType = (it.isSystem && appState.showSystemApps) || (!it.isSystem && appState.showUserApps)
                matchesSearch && (matchesType || appState.isRestoreDebloatMode)
            }
        }
    }

    var showForceRemoveWarning by remember { mutableStateOf(false) }
    var isTerminalExpanded by remember { mutableStateOf(false) }

    var showNativeDeleteDialog by remember { mutableStateOf(false) }
    var delSms by remember { mutableStateOf(true) }
    var delCall by remember { mutableStateOf(true) }
    var delContacts by remember { mutableStateOf(true) }

    LaunchedEffect(appState.logs.isEmpty()) {
        if (appState.logs.isEmpty()) isTerminalExpanded = false
    }

    val compIcons = mapOf(1 to Icons.Default.Android, 2 to Icons.Default.Storage, 3 to Icons.Default.Folder, 4 to Icons.Default.PermMedia, 5 to Icons.Default.Description, 6 to Icons.Default.Smartphone)
    val compNames = mapOf(1 to "App", 2 to "Data", 3 to "ExtData", 4 to "Media", 5 to "Obb", 6 to "Android ID")

    if (showNativeDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showNativeDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, null) },
            title = { Text("Delete Telephony Backups") },
            text = {
                Column {
                    // FIX: Removed unnecessary subtext
                    val options = listOf(
                        "SMS Messages" to delSms,
                        "Call Logs" to delCall,
                        "Contacts (vCard)" to delContacts
                    )
                    options.forEach { (label, state) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    when (label) {
                                        "SMS Messages" -> delSms = !delSms
                                        "Call Logs" -> delCall = !delCall
                                        "Contacts (vCard)" -> delContacts = !delContacts
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = state,
                                onCheckedChange = null,
                                thumbContent = { // FIX: Added Checked/Cross Icon
                                    Icon(
                                        imageVector = if (state) Icons.Filled.Check else Icons.Filled.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (delSms || delCall || delContacts) {
                            viewModel.deleteNativeBackups(context, delSms, delCall, delContacts)
                        }
                        showNativeDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Data") }
            },
            dismissButton = { TextButton(onClick = { showNativeDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    if (showForceRemoveWarning) {
        AlertDialog(
            onDismissRequest = { showForceRemoveWarning = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Enable Force Deletion?") },
            text = { Text("This will completely wipe the app data from the root partitions to free up space.\n\nWarning: Apps removed this way CANNOT be restored later using ROM Shifter!", color = MaterialTheme.colorScheme.error) },
            confirmButton = {
                Button(
                    onClick = { viewModel.setForceRemove(true); showForceRemoveWarning = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("I Understand, Enable") }
            },
            dismissButton = { TextButton(onClick = { showForceRemoveWarning = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))

        if (appState.migratorMode == MigratorMode.BACKUP_APPS || appState.migratorMode == MigratorMode.RESTORE_APPS) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(compNames.entries.toList()) { entry ->
                    val isSelected = appState.globalComponents.contains(entry.key)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.toggleGlobalComponent(entry.key) },
                        label = { Text(entry.value, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = { compIcons[entry.key]?.let { Icon(it, null, modifier = Modifier.size(16.dp)) } }
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = appState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search apps...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(10.dp))

            val allSelected = filteredApps.isNotEmpty() && filteredApps.all { it.isSelected }
            FilledTonalIconButton(
                onClick = { viewModel.selectAllVisibleApps(!allSelected, filteredApps) },
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            ) {
                Icon(if (allSelected) Icons.Default.RemoveDone else Icons.Default.DoneAll, contentDescription = "Select All")
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (appState.migratorMode == MigratorMode.DEBLOAT) {
                item {
                    FilterChip(
                        selected = appState.isRestoreDebloatMode,
                        onClick = { viewModel.toggleRestoreDebloatMode() },
                        label = { Text("Restore Mode") },
                        leadingIcon = { Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            if (!appState.isRestoreDebloatMode && appState.migratorMode != MigratorMode.SYSTEMIZE) {
                item {
                    FilterChip(
                        selected = appState.showUserApps,
                        onClick = { viewModel.toggleShowUserApps(!appState.showUserApps) },
                        label = { Text("User Apps") },
                        leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp)) }
                    )
                }
                item {
                    FilterChip(
                        selected = appState.showSystemApps,
                        onClick = { viewModel.toggleSystemApps() },
                        label = { Text("System Apps") },
                        leadingIcon = { Icon(Icons.Default.Android, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            if (appState.migratorMode == MigratorMode.DEBLOAT && !appState.isRestoreDebloatMode) {
                item {
                    FilterChip(
                        selected = appState.forceRemoveEnabled,
                        onClick = { if (appState.forceRemoveEnabled) viewModel.setForceRemove(false) else showForceRemoveWarning = true },
                        label = { Text("Force Deletion") },
                        leadingIcon = { Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }
            }

            if (appState.migratorMode == MigratorMode.SYSTEMIZE) {
                item {
                    FilterChip(
                        selected = isPrivileged,
                        onClick = { viewModel.setPrivilegedSystemize(!isPrivileged) },
                        label = { Text("Privileged Mode") },
                        leadingIcon = { Icon(Icons.Default.SecurityUpdateGood, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }

            if (appState.migratorMode == MigratorMode.MANAGE) {
                item {
                    AssistChip(
                        onClick = { showNativeDeleteDialog = true },
                        label = { Text("Delete Telephony") },
                        leadingIcon = { Icon(Icons.Default.SettingsPhone, null, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            if (appState.isFetchingApps) {
                LazyColumn(modifier = Modifier.fillMaxSize()) { items(8) { ShimmerAppListItem() } }
            } else if (filteredApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No apps found.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredApps, key = { it.packageName }, contentType = { "app" }) { app ->
                        AppListItem(app = app, onToggleSelect = { pkgName -> viewModel.toggleAppSelection(pkgName) })
                    }
                }
            }
        }

        // TRUE M3 BOTTOM ACTION BAR: Fixes selected text alignment and padding
        val selectedCount = appState.appList.count { it.isSelected }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding() // Never touches gesture pill
                    .padding(horizontal = 24.dp, vertical = 16.dp), // Perfectly padded aligned components
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$selectedCount Selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = { viewModel.runDynamicOperation() },
                    enabled = !appState.isRunning && selectedCount > 0,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (appState.migratorMode == MigratorMode.MANAGE || (appState.migratorMode == MigratorMode.DEBLOAT && !appState.isRestoreDebloatMode)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(48.dp)
                ) {
                    val act = when (appState.migratorMode) {
                        MigratorMode.MANAGE -> "Delete Apps"
                        MigratorMode.DEBLOAT -> if (appState.isRestoreDebloatMode) "Restore" else "Debloat"
                        MigratorMode.RESTORE_APPS -> "Restore"
                        MigratorMode.SYSTEMIZE -> "Systemize"
                        else -> "Backup"
                    }
                    Text(if (appState.isRunning) "Processing..." else act, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        AnimatedVisibility(visible = appState.logs.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { isTerminalExpanded = !isTerminalExpanded },
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(appState.currentAction, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Green)
                            Text(if (isTerminalExpanded) "Tap to minimize logs" else "Tap to expand logs", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        if (!appState.isRunning) {
                            TextButton(onClick = { viewModel.clearLogs() }) { Text("Dismiss", color = MaterialTheme.colorScheme.error) }
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Green, strokeWidth = 2.dp)
                        }
                    }

                    AnimatedVisibility(visible = isTerminalExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                                Text(appState.logs.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = Color.LightGray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}
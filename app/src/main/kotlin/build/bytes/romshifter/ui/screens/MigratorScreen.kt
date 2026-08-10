package build.bytes.romshifter.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import build.bytes.romshifter.utils.getAvatarColor
import coil.compose.AsyncImage
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
    AnimatedContent(
        targetState = appState.migratorMode == MigratorMode.MENU,
        transitionSpec = {
            val goingBack = !initialState && targetState
            if (goingBack) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {

                slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { it } togetherWith fadeOut(tween(250))
            }
        },
        label = "MigratorTransition"
    ) { isMenu ->
        if (isMenu) {
            MigratorMenu(appState, viewModel)
        } else {
            MigratorActionScreen(appState, viewModel)
        }
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
            shape = MaterialTheme.shapes.large,
            onDismissRequest = { showPermissionWarning = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(28.dp)) },
            title = { Text("Permissions Required") },
            text = { Text("ROM Shifter will automatically grant native Android permissions via root to access SMS, Call Logs, or Contacts. Do you want to continue?", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { Button(onClick = { showPermissionWarning = false; pendingNativeAction?.let { (isBackup, flags) -> viewModel.runNativeDataOperation(context, isBackup, flags.first, flags.second, flags.third) } }) { Text("Yes, Start") } },
            dismissButton = { TextButton(onClick = { showPermissionWarning = false }) { Text("Cancel") } }
        )
    }

    if (showNativeBackupDialog || showNativeRestoreDialog) {
        val isBackup = showNativeBackupDialog
        AlertDialog(
            shape = MaterialTheme.shapes.large,
            onDismissRequest = { showNativeBackupDialog = false; showNativeRestoreDialog = false },
            icon = { Icon(if (isBackup) Icons.Default.CloudUpload else Icons.Default.SettingsPhone, null, modifier = Modifier.size(28.dp)) },
            title = { Text(if (isBackup) "Telephony Data" else "Restore Telephony Data") },
            text = {
                Column {
                    val options = listOf("SMS Messages" to doSms, "Call Logs" to doCall, "Contacts (vCard)" to doContacts)
                    options.forEach { (label, state) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(if (state) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                .clickable {
                                    when (label) {
                                        "SMS Messages" -> doSms = !doSms
                                        "Call Logs" -> doCall = !doCall
                                        "Contacts (vCard)" -> doContacts = !doContacts
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = state,
                                onCheckedChange = null,
                                thumbContent = { Icon(imageVector = if (state) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
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


    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(12.dp))
            MenuCard("Backup Apps", Icons.Default.CloudUpload, "Backup system / user apps") { viewModel.setMigratorMode(MigratorMode.BACKUP_APPS) }
            MenuCard("Restore Apps", Icons.Default.RestorePage, "Restore Apps from Storage") { viewModel.setMigratorMode(MigratorMode.RESTORE_APPS) }
            MenuCard("Backup Telephony Data", Icons.Default.Sms, "Backup SMS, Calls, and Contacts") { showNativeBackupDialog = true }
            MenuCard("Restore Telephony Data", Icons.Default.SettingsPhone, "Restore Telephony Data from Storage") { showNativeRestoreDialog = true }
            MenuCard("Manage Backups", Icons.Default.Delete, "View and delete existing app backups") { viewModel.setMigratorMode(MigratorMode.MANAGE) }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigratorActionScreen(appState: AppState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val isPrivileged by viewModel.isPrivilegedSystemize.collectAsState()
    var showFilters by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }

    val filteredApps by remember(appState.appList, appState.searchQuery, appState.showSystemApps, appState.showUserApps, appState.actionFilterState) {
        derivedStateOf {
            appState.appList.filter { app ->
                val matchesSearch = app.label.contains(appState.searchQuery, ignoreCase = true) || app.packageName.contains(appState.searchQuery, ignoreCase = true)

                val isDebloatedMode = appState.migratorMode == MigratorMode.DEBLOAT && appState.actionFilterState == 2
                val matchesType = isDebloatedMode || ((app.isSystem && appState.showSystemApps) || (!app.isSystem && appState.showUserApps))

                val matchesAction = when (appState.migratorMode) {
                    MigratorMode.BACKUP_APPS -> {
                        if (appState.actionFilterState == 0) true
                        else if (appState.actionFilterState == 1) app.backupTime == "No backup on device"
                        else app.backupTime != "No backup on device"
                    }
                    MigratorMode.RESTORE_APPS -> {
                        if (appState.actionFilterState == 0) true
                        else if (appState.actionFilterState == 1) !app.isInstalled
                        else app.isInstalled
                    }
                    MigratorMode.SYSTEMIZE -> {
                        if (appState.actionFilterState == 1) !app.isSystem && !app.isSystemized else app.isSystemized
                    }
                    MigratorMode.DEBLOAT -> {
                        if (appState.actionFilterState == 1) app.isInstalled else !app.isInstalled
                    }
                    else -> true
                }

                matchesSearch && matchesType && matchesAction
            }
        }
    }

    var showForceRemoveWarning by remember { mutableStateOf(false) }
    var showNativeDeleteDialog by remember { mutableStateOf(false) }
    var showPrivilegedInfo by remember { mutableStateOf(false) }
    var delSms by remember { mutableStateOf(true) }
    var delCall by remember { mutableStateOf(true) }
    var delContacts by remember { mutableStateOf(true) }

    val compIcons = mapOf(1 to Icons.Default.Android, 2 to Icons.Default.Storage, 3 to Icons.Default.Folder, 4 to Icons.Default.PermMedia, 5 to Icons.Default.Description, 6 to Icons.Default.Smartphone)
    val compNames = mapOf(1 to "App", 2 to "Data", 3 to "ExtData", 4 to "Media", 5 to "Obb", 6 to "Android ID")

    if (showNativeDeleteDialog) {
        AlertDialog(
            shape = MaterialTheme.shapes.large,
            onDismissRequest = { showNativeDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(28.dp)) },
            title = { Text("Delete Telephony Backups") },
            text = {
                Column {
                    val options = listOf("SMS Messages" to delSms, "Call Logs" to delCall, "Contacts (vCard)" to delContacts)
                    options.forEach { (label, state) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(MaterialTheme.shapes.medium)
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
                            Text(label, style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = state,
                                onCheckedChange = null,
                                thumbContent = { Icon(imageVector = if (state) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { if (delSms || delCall || delContacts) viewModel.deleteNativeBackups(context, delSms, delCall, delContacts); showNativeDeleteDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete Data") }
            },
            dismissButton = { TextButton(onClick = { showNativeDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    if (showForceRemoveWarning) {
        AlertDialog(
            shape = MaterialTheme.shapes.large,
            onDismissRequest = { showForceRemoveWarning = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp)) },
            title = { Text("Enable Force Deletion?") },
            text = { Text("This will completely wipe the app data from the root partitions to free up space.\n\nWarning: Apps removed this way CANNOT be restored later using ROM Shifter!", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { Button(onClick = { viewModel.setForceRemove(true); showForceRemoveWarning = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("I Understand, Enable") } },
            dismissButton = { TextButton(onClick = { showForceRemoveWarning = false }) { Text("Cancel") } }
        )
    }

    if (showPrivilegedInfo) {
        AlertDialog(
            shape = MaterialTheme.shapes.large,
            onDismissRequest = { showPrivilegedInfo = false },
            icon = { Icon(Icons.Default.SecurityUpdateGood, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
            title = { Text("Enable Privileged Mode?") },
            text = { Text("This grants the application access to restricted, system-level permissions (e.g., secure settings, deep telephony routing, or recent apps integration).\n\nRecommended for: Custom Launchers, Google Dialer/Phone ports, Camera ports, or System UI plugins.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(onClick = {
                    viewModel.setPrivilegedSystemize(true)
                    showPrivilegedInfo = false
                }) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showPrivilegedInfo = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = appState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search apps...", style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(22.dp)) },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.weight(1f).height(52.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            val allSelected = filteredApps.isNotEmpty() && filteredApps.all { it.isSelected }
            FilledTonalIconButton(
                onClick = { viewModel.selectAllVisibleApps(!allSelected, filteredApps) },
                modifier = Modifier.size(52.dp),
                shape = CircleShape
            ) { Icon(if (allSelected) Icons.Default.RemoveDone else Icons.Default.DoneAll, contentDescription = "Select All", modifier = Modifier.size(22.dp)) }
        }

        Column {
            if (appState.migratorMode == MigratorMode.BACKUP_APPS || appState.migratorMode == MigratorMode.RESTORE_APPS) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(compNames.entries.toList()) { entry ->
                        val isSelected = appState.globalComponents.contains(entry.key)
                        FilterChip(
                            selected = isSelected, onClick = { viewModel.toggleGlobalComponent(entry.key) },
                            label = { Text(entry.value, style = MaterialTheme.typography.labelLarge) }, leadingIcon = { compIcons[entry.key]?.let { Icon(it, null, modifier = Modifier.size(16.dp)) } },
                            shape = CircleShape, modifier = Modifier.height(36.dp)
                        )
                    }
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chipModifier = Modifier.height(36.dp)

                val actionChipLabel = when (appState.migratorMode) {
                    MigratorMode.BACKUP_APPS -> when(appState.actionFilterState) { 0 -> "All Apps"; 1 -> "Backup"; else -> "Backed Up" }
                    MigratorMode.RESTORE_APPS -> when(appState.actionFilterState) { 0 -> "All Apps"; 1 -> "Restore"; else -> "Restored" }
                    MigratorMode.DEBLOAT -> if (appState.actionFilterState == 1) "Debloat" else "Restore"
                    MigratorMode.SYSTEMIZE -> if (appState.actionFilterState == 1) "Systemize" else "De-Systemize"
                    else -> ""
                }

                val actionIcon = when (appState.actionFilterState) {
                    0 -> Icons.Default.AllInclusive
                    1 -> Icons.Default.FilterList
                    else -> Icons.Default.CheckCircle
                }

                if (actionChipLabel.isNotEmpty()) {
                    item {
                        FilterChip(
                            selected = appState.actionFilterState != 0,
                            onClick = { viewModel.toggleActionFilter() },
                            label = { Text(actionChipLabel, style = MaterialTheme.typography.labelLarge) },
                            leadingIcon = { Icon(actionIcon, null, modifier = Modifier.size(16.dp)) },
                            shape = CircleShape,
                            modifier = chipModifier
                        )
                    }
                }

                if (appState.migratorMode != MigratorMode.SYSTEMIZE && !(appState.migratorMode == MigratorMode.DEBLOAT && appState.actionFilterState == 2)) {
                    item { FilterChip(selected = appState.showUserApps, onClick = { viewModel.toggleShowUserApps(!appState.showUserApps) }, label = { Text("User Apps", style = MaterialTheme.typography.labelLarge) }, leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp)) }, shape = CircleShape, modifier = chipModifier) }
                    item { FilterChip(selected = appState.showSystemApps, onClick = { viewModel.toggleSystemApps() }, label = { Text("System Apps", style = MaterialTheme.typography.labelLarge) }, leadingIcon = { Icon(Icons.Default.Android, null, modifier = Modifier.size(16.dp)) }, shape = CircleShape, modifier = chipModifier) }
                }

                if (appState.migratorMode == MigratorMode.DEBLOAT && appState.actionFilterState == 1) {
                    item {
                        FilterChip(
                            selected = appState.forceRemoveEnabled, onClick = { if (appState.forceRemoveEnabled) viewModel.setForceRemove(false) else showForceRemoveWarning = true },
                            label = { Text("Force Deletion", style = MaterialTheme.typography.labelLarge) }, leadingIcon = { Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp)) }, shape = CircleShape, modifier = chipModifier,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.errorContainer, selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer, selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer)
                        )
                    }
                }

                if (appState.migratorMode == MigratorMode.MANAGE) {
                    item {
                        AssistChip(
                            onClick = { showNativeDeleteDialog = true }, label = { Text("Delete Telephony", style = MaterialTheme.typography.labelLarge) }, leadingIcon = { Icon(Icons.Default.SettingsPhone, null, modifier = Modifier.size(16.dp)) }, shape = CircleShape, modifier = chipModifier,
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer, labelColor = MaterialTheme.colorScheme.onErrorContainer, leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer)
                        )
                    }
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = appState.isFetchingApps,
            onRefresh = { viewModel.refreshCurrentList() },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            if (appState.isFetchingApps) {
                LazyColumn(modifier = Modifier.fillMaxSize()) { items(8) { ShimmerAppListItem() } }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No apps found. Pull down to refresh.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp, top = 6.dp)) {
                    items(filteredApps, key = { it.packageName }, contentType = { "app" }) { app ->

                        val showTime = appState.migratorMode == MigratorMode.BACKUP_APPS || appState.migratorMode == MigratorMode.RESTORE_APPS || appState.migratorMode == MigratorMode.MANAGE || appState.migratorMode == MigratorMode.DEBLOAT

                        Box(modifier = Modifier.animateItem()) {
                            AppListItem(
                                app = app,
                                showBackupTime = showTime,
                                onToggleSelect = { pkgName -> viewModel.toggleAppSelection(pkgName) }
                            )
                        }
                    }
                }
            }
        }

        val showProgressPanel = appState.isRunning || appState.currentStep.isNotEmpty()

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shadowElevation = 4.dp
        ) {
            AnimatedContent(
                targetState = showProgressPanel,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                },
                label = "BottomBarTransition"
            ) { isProcessing ->
                if (isProcessing) {
                    
                    val processingApp = remember(appState.currentAction, appState.currentStep) {
                        appState.appList.firstOrNull { app ->
                            app.isSelected && (
                                    appState.currentAction.contains(app.label, ignoreCase = true) ||
                                            appState.currentStep.contains(app.label, ignoreCase = true) ||
                                            appState.currentAction.contains(app.packageName, ignoreCase = true) ||
                                            appState.currentStep.contains(app.packageName, ignoreCase = true)
                                    )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            
                            AnimatedVisibility(
                                visible = processingApp != null,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val iconModifier = Modifier
                                        .size(42.dp)
                                        .clip(MaterialTheme.shapes.small)

                                    if (processingApp?.iconBitmap != null) {
                                        Image(bitmap = processingApp.iconBitmap.asImageBitmap(), contentDescription = null, modifier = iconModifier)
                                    } else if (processingApp?.iconPath != null) {
                                        AsyncImage(model = processingApp.iconPath, contentDescription = null, modifier = iconModifier)
                                    } else if (processingApp != null) {
                                        val letter = processingApp.label.firstOrNull()?.uppercase() ?: "?"
                                        Box(modifier = iconModifier.background(getAvatarColor(processingApp.label)), contentAlignment = Alignment.Center) {
                                            Text(text = letter, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 20.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appState.currentAction,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (appState.currentStep.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = appState.currentStep,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (!appState.isRunning) {
                                Spacer(modifier = Modifier.width(12.dp))
                                FilledTonalIconButton(
                                    onClick = { viewModel.clearLogs() },
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else if (appState.progress in 0..100) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "${appState.progress}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (appState.progress in 0..100) {
                            LinearProgressIndicator(
                                progress = { appState.progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                } else {
                    val selectedCount = appState.appList.count { it.isSelected }
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {

                        AnimatedContent(
                            targetState = selectedCount,
                            transitionSpec = {
                                if (targetState > initialState) {
                                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                                            (slideOutVertically { height -> -height } + fadeOut())
                                } else {
                                    (slideInVertically { height -> -height } + fadeIn()) togetherWith
                                            (slideOutVertically { height -> height } + fadeOut())
                                }
                            },
                            label = "CountAnimation",
                            modifier = Modifier.weight(1f)
                        ) { count ->
                            Text(
                                text = "$count Selected",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = { viewModel.runDynamicOperation() },
                            enabled = selectedCount > 0,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (appState.migratorMode == MigratorMode.MANAGE || (appState.migratorMode == MigratorMode.DEBLOAT && appState.actionFilterState == 2) || (appState.migratorMode == MigratorMode.SYSTEMIZE && appState.actionFilterState == 2)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.height(52.dp).widthIn(min = 120.dp)
                        ) {
                            val act = when (appState.migratorMode) {
                                MigratorMode.MANAGE -> "Delete Apps"
                                MigratorMode.DEBLOAT -> if (appState.actionFilterState == 2) "Restore Apps" else "Debloat Apps"
                                MigratorMode.RESTORE_APPS -> "Restore Apps"
                                MigratorMode.SYSTEMIZE -> if (appState.actionFilterState == 2) "De-Systemize" else "Systemize Apps"
                                else -> "Backup Apps"
                            }
                            Text(act, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
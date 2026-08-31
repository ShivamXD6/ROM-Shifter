package build.bytes.romshifter.ui.screens

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsPhone
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.models.AppInfo
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.MigratorMode
import build.bytes.romshifter.ui.components.AnimatedFilterChip
import build.bytes.romshifter.ui.components.AppListItem
import build.bytes.romshifter.ui.components.ExpressiveRefreshIndicator
import build.bytes.romshifter.ui.components.MenuCard
import build.bytes.romshifter.ui.components.ShimmerAppListItem
import build.bytes.romshifter.utils.getAvatarColor
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MigratorTab(appState: AppState, appList: List<AppInfo>, viewModel: MainViewModel) {
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
            MigratorMenu(viewModel)
        } else {
            MigratorActionScreen(appState, appList, viewModel)
        }
    }
}

@Composable
fun MigratorMenu(viewModel: MainViewModel) {
    val context = LocalContext.current
    val availableBackups by viewModel.availableNativeBackups.collectAsState()

    var showNativeBackupDialog by remember { mutableStateOf(false) }
    var showNativeRestoreDialog by remember { mutableStateOf(false) }
    var showPermissionWarning by remember { mutableStateOf(false) }
    var pendingNativeAction by remember { mutableStateOf<Pair<Boolean, List<Boolean>>?>(null) }

    var doSms by remember { mutableStateOf(true) }
    var doCall by remember { mutableStateOf(true) }
    var doContacts by remember { mutableStateOf(false) }
    var doWifi by remember { mutableStateOf(false) }
    var doWallpaper by remember { mutableStateOf(false) }
    var doBluetooth by remember { mutableStateOf(false) }

    LaunchedEffect(showNativeRestoreDialog, showNativeBackupDialog) {
        if (showNativeRestoreDialog) {
            viewModel.refreshNativeBackups()
            doSms = false; doCall = false; doContacts = false
            doWifi = false; doWallpaper = false; doBluetooth = false
        } else if (showNativeBackupDialog) {
            doSms = true; doCall = true; doContacts = false
            doWifi = false; doWallpaper = false; doBluetooth = false
        }
    }

    LaunchedEffect(availableBackups, showNativeRestoreDialog) {
        if (showNativeRestoreDialog) {
            if (availableBackups.contains("Messages.shift")) doSms = true
            if (availableBackups.contains("CallLogs.shift")) doCall = true
            if (availableBackups.contains("Contacts.shift")) doContacts = true
            if (availableBackups.contains("Wifi.shift")) doWifi = true
            if (availableBackups.contains("Wallpaper.shift")) doWallpaper = true
            if (availableBackups.contains("Bluetooth.shift")) doBluetooth = true
        }
    }

    if (showPermissionWarning) {
        AlertDialog(
            shape = MaterialTheme.shapes.large,
            onDismissRequest = { showPermissionWarning = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(28.dp)) },
            title = { Text("Permissions Required") },
            text = {
                Text(
                    "ROM Shifter will automatically grant required native Android permissions via root to backup/restore SMS, Call Logs, or Contacts. Do you want to continue?",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPermissionWarning = false; pendingNativeAction?.let { (isBackup, flags) ->
                    viewModel.runNativeDataOperation(
                        context,
                        isBackup,
                        flags[0],
                        flags[1],
                        flags[2],
                        flags[3],
                        flags[4],
                        flags[5]
                    )
                }
                }) { Text("Yes, Start") }
            },
            dismissButton = { TextButton(onClick = { showPermissionWarning = false }) { Text("Cancel") } }
        )
    }

    if (showNativeBackupDialog || showNativeRestoreDialog) {
        val isBackup = showNativeBackupDialog
        AlertDialog(
            shape = MaterialTheme.shapes.large,
            onDismissRequest = { showNativeBackupDialog = false; showNativeRestoreDialog = false },
            icon = { Icon(if (isBackup) Icons.Default.CloudUpload else Icons.Default.SettingsPhone, null, modifier = Modifier.size(28.dp)) },
            title = { Text(if (isBackup) "Backup Native Data" else "Restore Native Data") },            text = {
                Column {
                    val options = mutableListOf(
                        "Messages" to doSms,
                        "Call Logs" to doCall,
                        "Contacts" to doContacts,
                        "WiFi" to doWifi,
                        "Wallpaper" to doWallpaper,
                        "Bluetooth" to doBluetooth
                    )

                    if (!isBackup) {
                        options.retainAll { (label, _) ->
                            val fileName = when (label) {
                                "Messages" -> "Messages.shift"
                                "Call Logs" -> "CallLogs.shift"
                                "Contacts" -> "Contacts.shift"
                                "WiFi" -> "Wifi.shift"
                                "Wallpaper" -> "Wallpaper.shift"
                                "Bluetooth" -> "Bluetooth.shift"
                                else -> ""
                            }
                            availableBackups.contains(fileName)
                        }
                    }

                    if (options.isEmpty()) {
                        Text(
                            "No native backups found in this folder.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    options.forEach { (label, state) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    if (state) MaterialTheme.colorScheme.secondaryContainer.copy(
                                        alpha = 0.5f
                                    ) else Color.Transparent
                                )
                                .clickable {
                                    when (label) {
                                        "Messages" -> doSms = !doSms
                                        "Call Logs" -> doCall = !doCall
                                        "Contacts" -> doContacts = !doContacts
                                        "WiFi" -> doWifi = !doWifi
                                        "Wallpaper" -> doWallpaper = !doWallpaper
                                        "Bluetooth" -> doBluetooth = !doBluetooth
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
                    if (doSms || doCall || doContacts || doWifi || doWallpaper || doBluetooth) {

                        val needsSms = doSms && ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED
                        val needsCall = doCall && ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED
                        val needsContacts = doContacts && ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED

                        if (needsSms || needsCall || needsContacts) {
                            pendingNativeAction = Pair(
                                isBackup,
                                listOf(doSms, doCall, doContacts, doWifi, doWallpaper, doBluetooth)
                            )
                            showPermissionWarning = true
                        } else {

                            viewModel.runNativeDataOperation(
                                context,
                                isBackup,
                                doSms,
                                doCall,
                                doContacts,
                                doWifi,
                                doWallpaper,
                                doBluetooth
                            )
                            showNativeBackupDialog = false
                            showNativeRestoreDialog = false
                        }
                    } else {
                        showNativeBackupDialog = false
                        showNativeRestoreDialog = false
                    }
                }) { Text(if (isBackup) "Backup" else "Restore") }
            },
            dismissButton = { TextButton(onClick = { showNativeBackupDialog = false; showNativeRestoreDialog = false }) { Text("Cancel") } }
        )
    }
    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 16.dp)) {
        Column(modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(12.dp))
            MenuCard("Backup Apps", Icons.Default.CloudUpload, "Backup system / user apps") { viewModel.setMigratorMode(MigratorMode.BACKUP_APPS) }
            MenuCard("Restore Apps", Icons.Default.RestorePage, "Restore Apps from Storage") { viewModel.setMigratorMode(MigratorMode.RESTORE_APPS) }
            MenuCard("Backup Native Data", Icons.Default.Sms, "Backup SMS, Calls, and ROM Data") { showNativeBackupDialog = true }
            MenuCard("Restore Native Data", Icons.Default.SettingsPhone, "Restore Native Data from Storage") { showNativeRestoreDialog = true }
            MenuCard("Manage Backups", Icons.Default.Delete, "View and delete existing app backups") { viewModel.setMigratorMode(MigratorMode.MANAGE) }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigratorActionScreen(appState: AppState, appList: List<AppInfo>, viewModel: MainViewModel) {
    val context = LocalContext.current


    val filteredApps by remember(
        appList,
        appState.searchQuery,
        appState.showSystemApps,
        appState.actionFilterState
    ) {
        derivedStateOf {
            appList.filter { app ->
                val matchesSearch = app.label.contains(
                    appState.searchQuery,
                    ignoreCase = true
                ) || app.packageName.contains(appState.searchQuery, ignoreCase = true)

                val isDebloatedMode =
                    appState.migratorMode == MigratorMode.DEBLOAT && appState.actionFilterState == 2
                val matchesType =
                    isDebloatedMode || (app.isSystem && appState.showSystemApps) || !app.isSystem
                val matchesAction = when (appState.migratorMode) {
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

    var expandedChipLabel by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var timerJob by remember { mutableStateOf<Job?>(null) }

    fun expandChip(label: String) {
        expandedChipLabel = label
        timerJob?.cancel()
        timerJob = scope.launch {
            delay(3000.milliseconds)
            expandedChipLabel = null
        }
    }

    var showNativeDeleteDialog by remember { mutableStateOf(false) }
    var delSms by remember { mutableStateOf(true) }
    var delCall by remember { mutableStateOf(true) }
    var delContacts by remember { mutableStateOf(true) }
    var delWifi by remember { mutableStateOf(true) }
    var delWallpaper by remember { mutableStateOf(true) }
    var delBluetooth by remember { mutableStateOf(true) }

    val compIcons = mapOf(
        1 to Icons.Default.Android,
        2 to Icons.Default.Storage,
        3 to Icons.Default.Security,
        4 to Icons.Default.PermMedia,
        5 to Icons.Default.Smartphone
    )
    val compNames = mapOf(
        1 to "App",
        2 to "Data & External",
        3 to "Permissions",
        4 to "Media & OBB",
        5 to "Android ID"
    )
    val compOrder = listOf(1, 2, 3, 4, 5)

    if (showNativeDeleteDialog) {
        AlertDialog(
            shape = MaterialTheme.shapes.large,
            onDismissRequest = { showNativeDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(28.dp)) },
            title = { Text("Erase Native Backups") },
            text = {
                Column {
                    val options = listOf(
                        "Messages" to delSms,
                        "Call Logs" to delCall,
                        "Contacts" to delContacts,
                        "WiFi" to delWifi,
                        "Wallpaper" to delWallpaper,
                        "Bluetooth" to delBluetooth
                    )
                    options.forEach { (label, state) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    when (label) {
                                        "Messages" -> delSms = !delSms
                                        "Call Logs" -> delCall = !delCall
                                        "Contacts" -> delContacts = !delContacts
                                        "WiFi" -> delWifi = !delWifi
                                        "Wallpaper" -> delWallpaper = !delWallpaper
                                        "Bluetooth" -> delBluetooth = !delBluetooth
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
                                thumbContent = {
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
                        if (delSms || delCall || delContacts || delWifi || delWallpaper || delBluetooth) viewModel.deleteNativeBackups(
                            context,
                            delSms,
                            delCall,
                            delContacts,
                            delWifi,
                            delWallpaper,
                            delBluetooth
                        ); showNativeDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Data") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNativeDeleteDialog = false
                }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = appState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = {
                    Text(
                        "Search apps...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(22.dp)
                    )
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            val allSelected = filteredApps.isNotEmpty() && filteredApps.all { it.isSelected }
            FilledTonalIconButton(
                onClick = { viewModel.selectAllVisibleApps(!allSelected, filteredApps) },
                modifier = Modifier.size(52.dp),
                shape = CircleShape
            ) {
                Icon(
                    if (allSelected) Icons.Default.RemoveDone else Icons.Default.DoneAll,
                    contentDescription = "Select All",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            val showActionChip = when (appState.migratorMode) {
                MigratorMode.DEBLOAT, MigratorMode.SYSTEMIZE -> true
                else -> false
            }

            val showEraseNative = appState.migratorMode == MigratorMode.MANAGE
            val hasButtons = showActionChip || showEraseNative

            val hasComponents =
                appState.migratorMode == MigratorMode.BACKUP_APPS || appState.migratorMode == MigratorMode.RESTORE_APPS

            val showAppFilters =
                appState.migratorMode != MigratorMode.SYSTEMIZE && !(appState.migratorMode == MigratorMode.DEBLOAT && appState.actionFilterState == 2)

            val showSmartSelect = when (appState.migratorMode) {
                MigratorMode.BACKUP_APPS, MigratorMode.RESTORE_APPS, MigratorMode.MANAGE -> true
                MigratorMode.DEBLOAT -> appState.actionFilterState == 1
                else -> false
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showEraseNative) {
                    item {
                        AnimatedFilterChip(
                            selected = false,
                            onClick = { showNativeDeleteDialog = true },
                            label = "Erase Native",
                            leadingIcon = Icons.Default.SettingsPhone,
                            showLabel = expandedChipLabel == "Erase Native",
                            onExpand = { expandChip("Erase Native") },
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedContentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (showActionChip) {
                    val actionChipLabel = when (appState.migratorMode) {
                        MigratorMode.DEBLOAT -> if (appState.actionFilterState == 1) "Debloat" else "Restore"
                        MigratorMode.SYSTEMIZE -> if (appState.actionFilterState == 1) "Systemize" else "De-Systemize"
                        else -> ""
                    }
                    val actionIcon = when (appState.migratorMode) {
                        MigratorMode.DEBLOAT -> {
                            if (appState.actionFilterState == 1) Icons.Default.Delete
                            else Icons.Default.RestorePage
                        }

                        MigratorMode.SYSTEMIZE -> {
                            if (appState.actionFilterState == 1) Icons.Default.Settings
                            else Icons.Default.Build
                        }

                        else -> {
                            when (appState.actionFilterState) {
                                0 -> Icons.Default.AllInclusive
                                1 -> Icons.Default.FilterList
                                else -> Icons.Default.CheckCircle
                            }
                        }
                    }
                    item {
                        AnimatedFilterChip(
                            selected = appState.actionFilterState != 0,
                            onClick = { viewModel.toggleActionFilter() },
                            label = actionChipLabel,
                            leadingIcon = actionIcon,
                            showLabel = expandedChipLabel == "ActionGroup",
                            onExpand = { expandChip("ActionGroup") }
                        )
                    }
                }

                if (hasButtons && (showSmartSelect || showAppFilters)) {
                    item {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }

                if (hasComponents) {
                    items(compOrder) { id ->
                        val label = compNames[id] ?: ""
                        AnimatedFilterChip(
                            selected = appState.globalComponents.contains(id),
                            onClick = { viewModel.toggleGlobalComponent(id) },
                            label = label,
                            leadingIcon = compIcons[id] ?: Icons.Default.Android,
                            showLabel = expandedChipLabel == label,
                            onExpand = { expandChip(label) }
                        )
                    }
                }

                if (hasComponents) {
                    item {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }

                if (showSmartSelect) {
                    item {
                        val allSmartMatchesSelected = remember(
                            filteredApps,
                            appState.migratorMode,
                            appState.actionFilterState
                        ) {
                            val matches = filteredApps.filter { app ->
                                when (appState.migratorMode) {
                                    MigratorMode.BACKUP_APPS -> app.availableInBackup.isNotEmpty()
                                    MigratorMode.RESTORE_APPS -> !app.isInstalled
                                    MigratorMode.MANAGE -> app.isInstalled
                                    MigratorMode.DEBLOAT -> appState.actionFilterState == 1 && app.availableInBackup.contains(
                                        1
                                    )
                                    else -> false
                                }
                            }
                            matches.isNotEmpty() && matches.all { it.isSelected }
                        }

                        AnimatedFilterChip(
                            selected = allSmartMatchesSelected,
                            onClick = {
                                viewModel.smartSelect(filteredApps)
                            },
                            label = "Auto Select",
                            leadingIcon = Icons.Default.AutoAwesome,
                            showLabel = expandedChipLabel == "Auto Select",
                            onExpand = { expandChip("Auto Select") }
                        )
                    }
                }

                if (showAppFilters) {
                    item {
                        AnimatedFilterChip(
                            selected = appState.showSystemApps,
                            onClick = { viewModel.toggleSystemApps() },
                            label = "System Apps",
                            leadingIcon = Icons.Default.Settings,
                            showLabel = expandedChipLabel == "System Apps",
                            onExpand = { expandChip("System Apps") }
                        )
                    }
                }
            }
        }

        if (filteredApps.any { it.isSelected && it.activeComponents != null }) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    val msg = when (appState.migratorMode) {
                        MigratorMode.BACKUP_APPS -> "Apps already backed up with previous components"
                        MigratorMode.RESTORE_APPS -> "Apps not yet restored with available components"
                        MigratorMode.MANAGE -> "Apps already restored"
                        MigratorMode.DEBLOAT -> "Apps already backed up with App components"
                        else -> "Auto Select Active"
                    }
                    Text(
                        msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        val pullRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = appState.isFetchingApps,
            onRefresh = { viewModel.refreshCurrentList() },
            state = pullRefreshState,
            indicator = {
                ExpressiveRefreshIndicator(
                    state = pullRefreshState,
                    isRefreshing = appState.isFetchingApps,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (appState.isFetchingApps) {
                LazyColumn(modifier = Modifier.fillMaxSize()) { items(8) { ShimmerAppListItem() } }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No apps found. Pull down to refresh.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp, top = 6.dp)
                ) {
                    items(filteredApps, key = { it.packageName }, contentType = { "app" }) { app ->

                        val showTime = appState.migratorMode == MigratorMode.BACKUP_APPS || appState.migratorMode == MigratorMode.RESTORE_APPS || appState.migratorMode == MigratorMode.MANAGE || appState.migratorMode == MigratorMode.DEBLOAT
                        val isMonochrome = (appState.migratorMode == MigratorMode.RESTORE_APPS || appState.migratorMode == MigratorMode.MANAGE) && !app.isInstalled

                        val showComps =
                            appState.migratorMode == MigratorMode.BACKUP_APPS || appState.migratorMode == MigratorMode.RESTORE_APPS

                        Box(modifier = Modifier.animateItem()) {
                            AppListItem(
                                app = app,
                                showBackupTime = showTime,
                                isMonochrome = isMonochrome,
                                showSelectedComponents = showComps,
                                onToggleSelect = { pkgName -> viewModel.toggleAppSelection(pkgName) }
                            )
                        }
                    }
                }
            }
        }

        val showProgressPanel = appState.isRunning || appState.currentStep.isNotEmpty()

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
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
                    val isCompleted = !appState.isRunning && appState.progress == 100
                    val isError = remember(appState.currentAction) {
                        appState.currentAction.contains("Insufficient Space", ignoreCase = true) ||
                                appState.currentAction.contains("Error", ignoreCase = true)
                    }

                    val processingApp = remember(appState.currentAction, appState.currentStep) {
                        appList.firstOrNull { app ->
                            app.isSelected && (
                                    appState.currentAction.contains(app.label, ignoreCase = true) ||
                                            appState.currentStep.contains(
                                                app.label,
                                                ignoreCase = true
                                            ) ||
                                            appState.currentAction.contains(
                                                app.packageName,
                                                ignoreCase = true
                                            ) ||
                                            appState.currentStep.contains(
                                                app.packageName,
                                                ignoreCase = true
                                            )
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

                            AnimatedContent(
                                targetState = isCompleted to (processingApp != null),
                                transitionSpec = {
                                    (scaleIn(tween(400, easing = FastOutSlowInEasing)) + fadeIn(
                                        tween(400)
                                    )) togetherWith
                                            (scaleOut(tween(200)) + fadeOut(tween(200)))
                                },
                                label = "IconTransition"
                            ) { (completed, hasApp) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (completed) {
                                        Icon(
                                            imageVector = if (isError) Icons.Default.Error else Icons.Default.Verified,
                                            contentDescription = if (isError) "Error" else "Success",
                                            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(42.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                    } else if (hasApp) {
                                        val iconModifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))

                                        if (processingApp?.iconPath != null) {
                                            AsyncImage(
                                                model = processingApp.iconPath,
                                                contentDescription = null,
                                                modifier = iconModifier
                                                )
                                        } else if (processingApp != null) {
                                            val letter =
                                                processingApp.label.firstOrNull()?.uppercase()
                                                    ?: "?"
                                            Box(
                                                modifier = iconModifier.background(
                                                    getAvatarColor(
                                                        processingApp.label
                                                    )
                                                ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = letter,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 20.sp
                                                )
                                            }
                                            }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        }
                                    }
                                }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appState.currentAction,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                        }
                } else {
                    val selectedCount = appList.count { it.isSelected }
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {

                        val isNeedFreeMode = appState.migratorMode == MigratorMode.BACKUP_APPS ||
                                appState.migratorMode == MigratorMode.RESTORE_APPS ||
                                (appState.migratorMode == MigratorMode.DEBLOAT && appState.actionFilterState == 2) ||
                                (appState.migratorMode == MigratorMode.SYSTEMIZE && appState.actionFilterState == 1)

                        val isNowMayFreeMode = appState.migratorMode == MigratorMode.MANAGE ||
                                (appState.migratorMode == MigratorMode.DEBLOAT && appState.actionFilterState == 1) ||
                                (appState.migratorMode == MigratorMode.SYSTEMIZE && appState.actionFilterState == 2)

                        val isInsufficientSpace =
                            appState.totalRequiredKb > appState.storageFreeKb && isNeedFreeMode

                        Column(modifier = Modifier.weight(1f)) {
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
                                label = "CountAnimation"
                            ) { count ->
                                Text(
                                    text = "$count Selected",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (isInsufficientSpace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (selectedCount > 0 && isNeedFreeMode) {
                                val reqSize =
                                    build.bytes.romshifter.utils.MigratorManager.formatSize(
                                        appState.totalRequiredKb.toString()
                                    )
                                val freeSize =
                                    build.bytes.romshifter.utils.MigratorManager.formatSize(
                                        appState.storageFreeKb.toString()
                                    )
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("Need: ")
                                        }
                                        append(reqSize)
                                        append(" | ")
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("Free: ")
                                        }
                                        append(freeSize)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isInsufficientSpace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (selectedCount > 0 && isNowMayFreeMode) {
                                val selSize =
                                    build.bytes.romshifter.utils.MigratorManager.formatSize(
                                        appState.totalRequiredKb.toString()
                                    )
                                val usedSize =
                                    build.bytes.romshifter.utils.MigratorManager.formatSize(
                                        appState.storageUsedKb.toString()
                                    )
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("Used: ")
                                        }
                                        append(usedSize)
                                        append(" | ")
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("Free Up: ")
                                        }
                                        append(selSize)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = { viewModel.runDynamicOperation() },
                            enabled = selectedCount > 0 && !isInsufficientSpace,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (appState.migratorMode == MigratorMode.MANAGE || (appState.migratorMode == MigratorMode.DEBLOAT && appState.actionFilterState == 2) || (appState.migratorMode == MigratorMode.SYSTEMIZE && appState.actionFilterState == 2)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .height(52.dp)
                                .widthIn(min = 120.dp)
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

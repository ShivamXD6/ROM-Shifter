package build.bytes.romshifter.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.FlashAction
import build.bytes.romshifter.ui.components.CircularCheckbox
import build.bytes.romshifter.ui.components.MenuCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashTab(
    appState: AppState,
    flashActions: List<FlashAction>,
    context: Context,
    viewModel: MainViewModel
) {
    val currentFlashActions by rememberUpdatedState(flashActions)
    val currentViewModel by rememberUpdatedState(viewModel)

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    var selectedBackupPartitions by remember { mutableStateOf(setOf<String>()) }
    var selectedPartition by remember { mutableStateOf("") }

    var restoreMode by remember { mutableStateOf("backup") }
    var customImgPath by remember { mutableStateOf("") }
    var allPartitions by remember { mutableStateOf(listOf<String>()) }
    var backedUpImages by remember { mutableStateOf(listOf<String>()) }
    var isAppending by remember { mutableStateOf(false) }
    var partitionSearchQuery by remember { mutableStateOf("") }

    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.processSelectedZips(uris, isAppending)
    }

    val imgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            build.bytes.romshifter.utils.FlashManager.getPathFromUri(context, uri)?.let { path ->
                if (path.endsWith(".img", ignoreCase = true)) {
                    customImgPath = path
                }
            }
        }
    }

    val appLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) viewModel.analyzeApps(uris, showInstaller = true)
    }

    if (showBackupDialog) {
        LaunchedEffect(Unit) {
            partitionSearchQuery = ""
            selectedBackupPartitions = emptySet()
            withContext(Dispatchers.IO) { allPartitions = viewModel.getAllPartitions() }
        }

        val filteredPartitions = allPartitions.filter { it.contains(partitionSearchQuery, ignoreCase = true) }

        AlertDialog(
            shape = MaterialTheme.shapes.large,
            onDismissRequest = { showBackupDialog = false },
            title = {
                Text("Backup Partitions", style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Column {
                    Text("Select partitions to securely extract to local storage:", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = partitionSearchQuery,
                            onValueChange = { partitionSearchQuery = it },
                            placeholder = { Text("Search...", style = MaterialTheme.typography.bodyLarge) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp)) },
                            singleLine = true,
                            shape = CircleShape,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        val allSelected = filteredPartitions.isNotEmpty() && filteredPartitions.all { selectedBackupPartitions.contains(it) }
                        FilledTonalIconButton(
                            onClick = {
                                selectedBackupPartitions = if (allSelected) {
                                    selectedBackupPartitions - filteredPartitions.toSet()
                                } else {
                                    selectedBackupPartitions + filteredPartitions.toSet()
                                }
                            },
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape
                        ) {
                            Icon(if (allSelected) Icons.Default.RemoveDone else Icons.Default.DoneAll, contentDescription = "Select All", modifier = Modifier.size(24.dp))
                        }
                    }

                    if (allPartitions.isEmpty()) {
                        CircularProgressIndicator(modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(24.dp))
                    } else if (filteredPartitions.isEmpty()) {
                        Text("No partitions found.", modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(filteredPartitions) { part ->
                                val isSelected = selectedBackupPartitions.contains(part)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                        .clickable {
                                            selectedBackupPartitions =
                                                if (isSelected) selectedBackupPartitions - part else selectedBackupPartitions + part
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularCheckbox(checked = isSelected, onCheckedChange = null)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = part,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedBackupPartitions.isNotEmpty()) {
                            selectedBackupPartitions.forEach { part ->
                                viewModel.runLiveOperation("--live-backup", part) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                            }
                            showBackupDialog = false
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier.height(52.dp),
                    enabled = selectedBackupPartitions.isNotEmpty()
                ) {
                    val text = if (selectedBackupPartitions.isEmpty()) "Backup" else "Backup (${selectedBackupPartitions.size})"
                    Text(text, style = MaterialTheme.typography.titleMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) { Text("Cancel", style = MaterialTheme.typography.titleMedium) }
            }
        )
    }

    if (showRestoreDialog) {
        LaunchedEffect(Unit) {
            partitionSearchQuery = ""
            withContext(Dispatchers.IO) { allPartitions = viewModel.getAllPartitions(); backedUpImages = viewModel.getBackedUpImages() }
        }

        AlertDialog(
            shape = MaterialTheme.shapes.large,
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Flash Partition", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    ElevatedCard(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Warning: Live flashing modifies raw hardware partitions. Incorrect images will hard brick your device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        FilterChip(
                            selected = restoreMode == "backup",
                            onClick = { restoreMode = "backup"; selectedPartition = "" },
                            label = { Text("From Backup", style = MaterialTheme.typography.labelLarge) },
                            shape = CircleShape,
                            modifier = Modifier.height(40.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        FilterChip(
                            selected = restoreMode == "custom",
                            onClick = { restoreMode = "custom"; selectedPartition = "" },
                            label = { Text("Custom .img", style = MaterialTheme.typography.labelLarge) },
                            shape = CircleShape,
                            modifier = Modifier.height(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (restoreMode == "backup") {
                        if (backedUpImages.isEmpty()) {
                            Text(
                                "No images found in Partitions folder.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                items(backedUpImages) { img ->
                                    val isSelected = selectedPartition == img
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                            .clickable { selectedPartition = img }
                                            .padding(
                                                start = 12.dp,
                                                end = 4.dp,
                                                top = 6.dp,
                                                bottom = 6.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            RadioButton(selected = isSelected, onClick = null)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = img,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        IconButton(onClick = {
                                            viewModel.deleteLivePartitionImage(img)
                                            backedUpImages = backedUpImages.filter { it != img }
                                            if (selectedPartition == img) selectedPartition = ""
                                        }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { imgLauncher.launch(arrayOf("application/octet-stream")) },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = if (customImgPath.isEmpty()) "Select .img File" else customImgPath.substringAfterLast("/"),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        TextField(
                            value = partitionSearchQuery,
                            onValueChange = { partitionSearchQuery = it },
                            placeholder = { Text("Search target partition...", style = MaterialTheme.typography.bodyLarge) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(24.dp)) },
                            singleLine = true,
                            shape = CircleShape,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )

                        val filteredPartitions = allPartitions.filter { it.contains(partitionSearchQuery, ignoreCase = true) }

                        LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                            items(filteredPartitions) { part ->
                                val isSelected = selectedPartition == part
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                        .clickable { selectedPartition = part }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = isSelected, onClick = null)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = part,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (restoreMode == "backup" && selectedPartition.isNotBlank()) {
                            val partName = selectedPartition.substringBefore("_backup.img")
                            viewModel.runLiveOperation(
                                "--live-restore",
                                partName,
                                "${viewModel.savedPath.value}/Partitions/$selectedPartition"
                            ) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                            showRestoreDialog = false
                        } else if (restoreMode == "custom" && customImgPath.isNotBlank() && selectedPartition.isNotBlank()) {
                            viewModel.runLiveOperation("--live-restore", selectedPartition, customImgPath) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                            showRestoreDialog = false
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier.height(52.dp),
                    enabled = selectedPartition.isNotBlank() && (restoreMode == "backup" || customImgPath.isNotBlank())
                ) { Text("Flash Image", style = MaterialTheme.typography.titleMedium) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel", style = MaterialTheme.typography.titleMedium) }
            }
        )
    }

    AnimatedContent(
        targetState = appState.flashWizardStep,
        transitionSpec = {
            val goingBack = targetState < initialState
            if (goingBack) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { it } togetherWith fadeOut(tween(250))
            }
        },
        label = "FlashWizardSteps"
    ) { step ->
        if (step > 0) {

            Column(modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(16.dp)) {
                when (step) {
                    1 -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 0.dp
                        ) {
                            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                val availableWipes = listOf(
                                    "dalvik" to "Dalvik / ART Cache",
                                    "cache" to "Cache",
                                    "data" to "Data (Keeps internal storage)",
                                    "metadata" to "Metadata",
                                    "system" to "System (For Non-Dynamic)"
                                )

                                availableWipes.forEach { (partId, label) ->
                                    val isChecked = appState.flashWipePartitions.contains(partId)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 3.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                            .clickable { viewModel.toggleFlashWipePartition(partId) }
                                            .padding(horizontal = 20.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Switch(
                                            checked = isChecked,
                                            onCheckedChange = null,
                                            thumbContent = { Icon(imageVector = if (isChecked) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                                        )
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setFlashFormatData(!appState.flashFormatData) }
                                        .padding(horizontal = 24.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Format Data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                                        Text("Erase EVERYTHING including internal storage", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                                    }
                                    Switch(
                                        checked = appState.flashFormatData,
                                        onCheckedChange = null,
                                        thumbContent = { Icon(imageVector = if (appState.flashFormatData) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.error, checkedTrackColor = MaterialTheme.colorScheme.errorContainer)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { isAppending = false; zipLauncher.launch(arrayOf("application/zip")) },
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) { Text("Next: Select ZIP Files", style = MaterialTheme.typography.titleMedium) }
                    }
                    2 -> {
                        if (appState.isProcessingZips) {
                            CircularProgressIndicator(modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 40.dp))
                        } else {
                            val density = LocalDensity.current
                            val swapThreshold =
                                with(density) { 56.dp.toPx() }

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                itemsIndexed(
                                    items = flashActions,
                                    key = { _, action -> action.id }) { index, action ->
                                    var dragOffset by remember(action.id) { mutableFloatStateOf(0f) }
                                    val currentIndexState by rememberUpdatedState(index)

                                    val cardColor = when (action) {
                                        is FlashAction.Wipe -> MaterialTheme.colorScheme.secondaryContainer
                                        is FlashAction.FormatData -> MaterialTheme.colorScheme.errorContainer
                                        is FlashAction.InstallZip -> MaterialTheme.colorScheme.surfaceContainer
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .offset { IntOffset(0, dragOffset.roundToInt()) }
                                            .animateItem(),
                                        shape = MaterialTheme.shapes.large,
                                        color = cardColor,
                                        tonalElevation = 0.dp
                                    ) {
                                        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${index + 1}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(20.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                when (action) {
                                                    is FlashAction.Wipe -> {
                                                        Text(
                                                            "Wipe Partitions",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            action.partitions.joinToString(", "),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    is FlashAction.FormatData -> {
                                                        Text(
                                                            "Format Data",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                        Text(
                                                            "Will erase all user data!",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }

                                                    is FlashAction.InstallZip -> {
                                                        Text(
                                                            action.zip.name,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            "Category: ${action.zip.category}",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }

                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "Drag to reorder",
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .pointerInput(action.id) {
                                                        detectVerticalDragGestures(
                                                            onDragEnd = { dragOffset = 0f },
                                                            onDragCancel = { dragOffset = 0f }
                                                        ) { change, dragAmount ->
                                                            change.consume()
                                                            dragOffset += dragAmount

                                                            val list = currentFlashActions
                                                            val currentIndex =
                                                                list.indexOfFirst { it.id == action.id }

                                                            if (currentIndex != -1) {
                                                                if (dragOffset > swapThreshold && currentIndex < list.size - 1) {
                                                                    currentViewModel.moveActionDown(
                                                                        currentIndex
                                                                    )
                                                                    dragOffset -= swapThreshold
                                                                } else if (dragOffset < -swapThreshold && currentIndex > 0) {
                                                                    currentViewModel.moveActionUp(
                                                                        currentIndex
                                                                    )
                                                                    dragOffset += swapThreshold
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .padding(4.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            IconButton(onClick = {
                                                currentViewModel.removeAction(currentIndexState)
                                            }, modifier = Modifier.size(40.dp)) {
                                                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { isAppending = true; zipLauncher.launch(arrayOf("application/zip")) }, shape = CircleShape, modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)) {
                                    Text("Add More ZIPs", style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(onClick = { viewModel.checkLockscreenAndProceed() }, shape = CircleShape, modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)) {
                                    Text("Next", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                    3 -> {
                        val hasRomZip =
                            flashActions.filterIsInstance<FlashAction.InstallZip>().any {
                                it.zip.category.contains("ROM", ignoreCase = true) ||
                                        it.zip.name.contains("ROM", ignoreCase = true)
                        }

                        if (appState.hasLockscreen) {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Screen Lock Detected!", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val warningText = if (hasRomZip) {
                                        "You must remove your PIN/Pattern to avoid FRP (Factory Reset Protection) lock when flashing a ROM zip."
                                    } else {
                                        "You must remove your PIN/Pattern before flashing so recovery can decrypt your storage automatically."
                                    }
                                    Text(warningText, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)

                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer)
                                    ) { Text("Open Settings", style = MaterialTheme.typography.titleMedium) }
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = { viewModel.checkLockscreenAndProceed() }, shape = CircleShape, modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)) {
                                    Text("I've Removed It - Verify Again", style = MaterialTheme.typography.titleMedium)
                                }

                                if (!hasRomZip) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.generateOrsAndProceed() },
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) { Text("Skip (Only if Recovery Touch works)", style = MaterialTheme.typography.titleMedium) }
                                }
                            }
                        } else {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.LockOpen,
                                            null,
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                }
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        "Storage Decrypted!",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                            }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Button(onClick = { viewModel.generateOrsAndProceed() }, shape = CircleShape, modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)) {
                                Text("Next: Finalize", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    4 -> {
                        if (appState.currentAction == "Rebooting to Recovery...") {
                            CircularProgressIndicator(modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 40.dp))
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 0.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Ready to Flash!",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Everything is prepared. Tap below to begin.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        "Select an Option to Reboot after Flash:",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(
                                            8.dp,
                                            Alignment.CenterHorizontally
                                        )
                                    ) {
                                        listOf("system", "recovery", "bootloader").forEach { opt ->
                                            val isSelected = appState.flashRebootOption == opt
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    val newOpt = if (isSelected) "none" else opt
                                                    viewModel.setFlashRebootOption(newOpt)
                                                    viewModel.generateOrsAndProceed()
                                                },
                                                label = {
                                                    Text(
                                                        text = opt.replaceFirstChar { it.uppercase() },
                                                        style = MaterialTheme.typography.labelLarge,
                                                        textAlign = TextAlign.Center
                                                    )
                                                },
                                                shape = CircleShape
                                            )
                                        }
                                    }
                            }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { viewModel.executeFlashNow() },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) { Text("Reboot to Recovery & Flash Now", style = MaterialTheme.typography.titleMedium) }
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(onClick = { viewModel.restartFlashWizard() }, shape = CircleShape, modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)) {
                                    Text(
                                        "Restart Wizard & Clear ORS",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {

            Column(modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)) {
                Column(modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(16.dp))

                    MenuCard("Start Auto Flash Wizard", Icons.Default.FlashOn, "Auto Flash zip files in recovery, ideal for broken recovery touch") { viewModel.openFlashWizard() }
                    MenuCard(
                        "Install Batch Apps",
                        Icons.Default.Download,
                        "Install multiple APK/APKS/XAPK/APKM files in parallel"
                    ) {
                        appLauncher.launch(
                            arrayOf(
                                "application/vnd.android.package-archive",
                                "application/octet-stream"
                            )
                        )
                    }
                    MenuCard("Backup Partitions", Icons.Default.Save, "Extract partition images to local storage") { showBackupDialog = true }
                    MenuCard("Flash Partitions", Icons.Default.SystemUpdateAlt, "Flash images directly to active slot") { backedUpImages = viewModel.getBackedUpImages(); selectedPartition = ""; customImgPath = ""; restoreMode = "backup"; showRestoreDialog = true }
                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }
}
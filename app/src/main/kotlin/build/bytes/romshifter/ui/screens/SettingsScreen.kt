package build.bytes.romshifter.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import build.bytes.romshifter.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(context: Context, viewModel: MainViewModel) {
    val savedPath by viewModel.savedPath.collectAsState()
    val currentTheme by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val updateChannel by viewModel.updateChannel.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val fullChangelogs by viewModel.fullChangelogs.collectAsState()
    val isFetchingChangelogs by viewModel.isFetchingChangelogs.collectAsState()

    var inputPath by remember { mutableStateOf(savedPath) }
    var isMoving by remember { mutableStateOf(false) }
    var showMoveWarning by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedPath) { if (!isMoving) inputPath = savedPath }
    val isEditing = inputPath != savedPath

    var showAboutSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    var showChangelogSheet by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
            val path = if ("primary".equals(split[0], true)) "$basePath/${split.getOrNull(1) ?: ""}" else "/storage/${split[0]}/${split.getOrNull(1) ?: ""}"
            val finalPath = if (path.endsWith("Shifter")) path else "$path/Shifter"
            inputPath = finalPath

            val oldIsInternal =
                savedPath.startsWith(basePath) || savedPath.startsWith("/storage/emulated")
            val newIsInternal = "primary".equals(split[0], true)

            if (oldIsInternal != newIsInternal) {
                showMoveWarning = finalPath
            } else {
                isMoving = true
                viewModel.migrateFolder(finalPath) { isMoving = false }
            }
        }
    }

    if (showMoveWarning != null) {
        AlertDialog(
            onDismissRequest = { showMoveWarning = null },
            confirmButton = {
                Button(
                    onClick = {
                        val path = showMoveWarning!!
                        showMoveWarning = null
                        isMoving = true
                        viewModel.migrateFolder(path) { isMoving = false }
                    }
                ) {
                    Text("Proceed")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showMoveWarning = null }) {
                    Text("Cancel")
                }
            },
            title = { Text("Moving Backups") },
            text = { Text("Moving your existing Shifter files between Internal Storage and MicroSD will take time. Please don't close the app or don't go back during this process.") },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    if (showUpdateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showUpdateSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text("App Updates", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("Select release channel", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))

                val channels =
                    remember { listOf(0 to "Stable Release", 1 to "Early Access (Pre-releases)") }
                channels.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { viewModel.setUpdateChannel(value) }
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium, color = if (updateChannel == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        if (updateChannel == value) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.checkForUpdates(isSilent = false)
                            showUpdateSheet = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update App", style = MaterialTheme.typography.titleMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.fetchAllChangelogs()
                            showChangelogSheet = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = CircleShape
                    ) {
                        if (isFetchingChangelogs) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Description, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Changelogs", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showChangelogSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChangelogSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    "All Changelogs",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "History of ROM Shifter releases",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (isFetchingChangelogs && fullChangelogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        fullChangelogs.forEach { (tag, body) ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = formatChangelog(
                                            body,
                                            MaterialTheme.colorScheme.primary
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showThemeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text("Choose Theme", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))

                val options = remember {
                    listOf(
                        0 to "System Default",
                        1 to "Light",
                        2 to "Dark",
                        3 to "Amoled (Black)"
                    )
                }
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { viewModel.setTheme(value); showThemeSheet = false }
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium, color = if (currentTheme == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        if (currentTheme == value) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { viewModel.setDynamicColor(!dynamicColor) }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Dynamic Colors",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subText =
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) "Use Monet colors based on wallpaper" else "Auto-detect accent colors from wallpaper"
                        Text(
                            subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) },
                        thumbContent = {
                            Icon(
                                imageVector = if (dynamicColor) Icons.Filled.Check else Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showAboutSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAboutSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text("About & Support", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("App info, social links, and donations", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { openUriSafely(context, "https://t.me/buildbytes") }
                    .padding(vertical = 14.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Join Telegram",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Join the Build Bytes community", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { openUriSafely(context, "https://www.youtube.com/@BuildBytesX") }
                    .padding(vertical = 14.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "YouTube Channel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Subscribe to BuildBytesX", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        openUriSafely(
                            context,
                            "https://github.com/ShivamXD6/ROM-Shifter/"
                        )
                    }
                    .padding(vertical = 14.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Star,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Star on GitHub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("View source code and releases", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Check out ROM Shifter: https://github.com/ShivamXD6/ROM-Shifter/"
                                )
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    "Share ROM Shifter via"
                                )
                            )
                        }
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Share,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Share App",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Recommend ROM Shifter to others",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            openUriSafely(
                                context,
                                "https://github.com/sponsors/ShivamXD6"
                            )
                        }
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Sponsor Project",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Support the development via GitHub",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            openUriSafely(
                                context,
                                "upi://pay?pa=shivamashokdhage6@oksbi&pn=Build%20Bytes&cu=INR"
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = CircleShape
                    ) { Text("UPI (Any)") }

                    Button(
                        onClick = { openUriSafely(context, "https://paypal.me/ShivamXD6") },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = CircleShape
                    ) { Text("PayPal") }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Made by @ShivamXD6 | Build Bytes Team", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    Column(modifier = Modifier
        .padding(horizontal = 16.dp)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .animateContentSize(animationSpec = tween(350, easing = FastOutSlowInEasing)),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            Column {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = "Folder",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Shifter Location",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("All backups and files are saved here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { if (!isMoving) launcher.launch(null) }) { Icon(Icons.Default.FolderOpen, contentDescription = "Browse", modifier = Modifier.size(28.dp)) }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedTextField(
                        value = inputPath,
                        onValueChange = { inputPath = it },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        placeholder = { Text("${android.os.Environment.getExternalStorageDirectory().absolutePath}/Shifter") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Storage,
                                null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )

                    AnimatedVisibility(visible = isEditing || isMoving, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            Spacer(modifier = Modifier.height(16.dp))
                            if (isMoving) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Saving Directory...", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val basePath =
                                            android.os.Environment.getExternalStorageDirectory().absolutePath
                                        val oldIsInternal =
                                            savedPath.startsWith(basePath) || savedPath.startsWith("/storage/emulated")
                                        val newIsInternal =
                                            inputPath.startsWith(basePath) || inputPath.startsWith("/storage/emulated")

                                        if (oldIsInternal != newIsInternal) {
                                            showMoveWarning = inputPath
                                        } else {
                                            isMoving = true
                                            viewModel.migrateFolder(inputPath) { isMoving = false }
                                        }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.height(52.dp)
                                ) { Text("Apply Path", style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeSheet = true }
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = "Theme",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Appearance",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val themeText = when (currentTheme) {
                            1 -> "Light"; 2 -> "Dark"; 3, 4 -> "Amoled"; else -> "System Default"
                        }
                        val dynamicText = if (dynamicColor) " (Dynamic)" else ""
                        Text(
                            "Theme: $themeText$dynamicText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showUpdateSheet = true }
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = "Updates",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "App Updates",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val channelText = if (updateChannel == 0) "Stable" else "Early Access"
                        val displayText = updateStatus.ifEmpty { "Channel: $channelText" }
                        Text(displayText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAboutSheet = true }
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "About",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "About & Support",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("App info, social links, and donations", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

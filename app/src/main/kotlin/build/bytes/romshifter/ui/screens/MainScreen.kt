package build.bytes.romshifter.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.R
import build.bytes.romshifter.models.MigratorMode

fun openUriSafely(context: Context, uriString: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uriString)))
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to open this link.", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val appState by viewModel.uiState.collectAsState()
    val isFirstRun by viewModel.isFirstRun.collectAsState()
    var selectedTab by remember { mutableIntStateOf(1) }
    var showSettings by remember { mutableStateOf(false) }

    if (!appState.hasRoot) {
        NoRootScreen()
        return
    }

    if (isFirstRun) {
        OnboardingWizard(viewModel)
        return
    }

    val tabs = listOf(
        Triple("Flash", Icons.Default.FlashOn, "Flash"),
        Triple("Migrate", Icons.Default.CloudSync, "Migrate"),
        Triple("Extras", Icons.Default.Build, "Extras")
    )

    val dynamicTitle = when {
        showSettings -> "Settings" // FIX: Updated text exactly as requested!
        appState.flashWizardStep > 0 -> "Auto Flash Wizard"
        appState.migratorMode != MigratorMode.MENU -> {
            when (appState.migratorMode) {
                MigratorMode.BACKUP_APPS -> "Backup Apps"
                MigratorMode.RESTORE_APPS -> "Restore Apps"
                MigratorMode.MANAGE -> "Manage Backups"
                MigratorMode.DEBLOAT -> "Debloat / Restore Apps"
                MigratorMode.SYSTEMIZE -> "Systemize User Apps"
                else -> "ROM Shifter"
            }
        }
        else -> "ROM Shifter"
    }

    BackHandler(enabled = appState.migratorMode != MigratorMode.MENU || showSettings || appState.flashWizardStep > 0) {
        if (showSettings) showSettings = false
        else if (appState.flashWizardStep > 0) viewModel.closeFlashWizard()
        else viewModel.setMigratorMode(MigratorMode.MENU)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (dynamicTitle == "ROM Shifter") {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_home),
                                    contentDescription = "Logo",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Text(
                                text = dynamicTitle,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    navigationIcon = {
                        if (appState.migratorMode != MigratorMode.MENU || showSettings || appState.flashWizardStep > 0) {
                            IconButton(onClick = {
                                if (showSettings) showSettings = false
                                else if (appState.flashWizardStep > 0) viewModel.closeFlashWizard()
                                else viewModel.setMigratorMode(MigratorMode.MENU)
                            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        }
                    },
                    actions = {
                        if (appState.migratorMode == MigratorMode.MENU && !showSettings && appState.flashWizardStep == 0) {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(top = innerPadding.calculateTopPadding()).fillMaxSize()) {

                AnimatedContent(
                    targetState = showSettings to selectedTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "AppContentTransition",
                    modifier = Modifier.fillMaxSize()
                ) { (isSettingsOpen, currentTab) ->
                    if (isSettingsOpen) {
                        SettingsTab(LocalContext.current, viewModel)
                    } else {
                        when (currentTab) {
                            0 -> FlashTab(LocalContext.current, viewModel)
                            1 -> MigratorTab(appState, viewModel)
                            2 -> ExtrasTab(appState, viewModel)
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !showSettings && appState.flashWizardStep == 0 && appState.migratorMode == MigratorMode.MENU,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeIn(tween(400)),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400, easing = FastOutLinearInEasing)) + fadeOut(tween(400)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    NavigationBar(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .clip(RoundedCornerShape(32.dp)),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                icon = { Icon(imageVector = tab.second, contentDescription = tab.first) },
                                label = { Text(text = tab.third, style = MaterialTheme.typography.labelSmall) },
                                selected = selectedTab == index,
                                onClick = { selectedTab = index; if (index != 1) viewModel.setMigratorMode(MigratorMode.MENU) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingWizard(viewModel: MainViewModel) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(1) }

    BackHandler(enabled = step > 1) {
        step -= 1
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
            val path = if ("primary".equals(split[0], true)) "$basePath/${split.getOrNull(1) ?: ""}" else "/storage/${split[0]}/${split.getOrNull(1) ?: ""}"
            val finalPath = if (path.endsWith("#Shifter")) path else "$path/#Shifter"
            viewModel.migrateFolder(finalPath) { step = 3 }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { step = 4 }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        AnimatedVisibility(
            visible = step > 1,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 24.dp, start = 8.dp)
        ) {
            IconButton(onClick = { step -= 1 }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                1 -> {
                    Icon(Icons.Default.Bolt, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(28.dp))
                    Text("Get Started with", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("ROM Shifter", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text("The ultimate root-powered tool for migrating apps, backing up telephony data, auto-flashing ZIPs directly in recovery, and modifying your ROM securely. Let's get things set up.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp)) { Text("Next") }
                }
                2 -> {
                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(24.dp))
                    Text("Shifter Directory", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("ROM Shifter needs a dedicated folder to store your backups, images, and logs safely. You can auto-detect an existing one or select manually.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = { viewModel.autoDetectShifterFolder { success -> if (success) step = 3 } }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp)) { Text("Auto-Detect #Shifter Folder") }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp)) { Text("Select Folder Manually") }
                }
                3 -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        LaunchedEffect(Unit) {
                            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    Icon(Icons.Default.Security, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(24.dp))
                    Text("Permissions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("To accurately back up native Call Logs, SMS, and Contacts securely via ContentResolver without failing, ROM Shifter requires explicit permissions. Notifications are also needed to track background progress.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = {
                        permLauncher.launch(arrayOf(
                            android.Manifest.permission.READ_SMS,
                            android.Manifest.permission.READ_CALL_LOG,
                            android.Manifest.permission.WRITE_CALL_LOG,
                            android.Manifest.permission.READ_CONTACTS,
                            android.Manifest.permission.WRITE_CONTACTS
                        ))
                    }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp)) { Text("Grant Permissions & Next") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { step = 4 }) { Text("Skip for now") }
                }
                4 -> {
                    Icon(Icons.Default.Favorite, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(20.dp))
                    Text("Made by @ShastikXD | Build Bytes Team", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("If ROM Shifter helped you, please consider starring the repository on GitHub or supporting the project via donations!", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { openUriSafely(context, "https://t.me/buildbytes") }, modifier = Modifier.weight(1f)) { Text("Telegram") }
                        FilledTonalButton(onClick = { openUriSafely(context, "https://www.youtube.com/@BuildBytesX") }, modifier = Modifier.weight(1f)) { Text("YouTube") }
                        FilledTonalButton(onClick = { openUriSafely(context, "https://github.com/ShivamXD6/ROM-Shifter-App/") }, modifier = Modifier.weight(1f)) { Text("GitHub") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { openUriSafely(context, "upi://pay?pa=shivamashokdhage6@oksbi&pn=Build%20Bytes&cu=INR") }, modifier = Modifier.weight(1f)) { Text("UPI (Any)") }
                        Button(onClick = { openUriSafely(context, "https://paypal.me/ShivamXD6") }, modifier = Modifier.weight(1f)) { Text("PayPal") }
                    }

                    Spacer(Modifier.height(28.dp))
                    Button(onClick = { viewModel.finishOnboarding() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp)) { Text("Let's Shift!") }
                }
            }
        }
    }
}

@Composable
fun NoRootScreen() {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.Warning, contentDescription = "No Root", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Root Access Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Please grant root permissions in Magisk/KernelSU to use ROM Shifter. Then, restart the app.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
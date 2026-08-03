package build.bytes.romshifter.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.models.MigratorMode

// Helper function to launch intents securely
fun openUriSafely(context: Context, uriString: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uriString)))
    } catch (e: Exception) {
        Toast.makeText(context, "No suitable app found to open this link.", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, isDarkTheme: Boolean, onThemeToggle: () -> Unit) {
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
        showSettings -> "Settings & Config"
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (dynamicTitle == "ROM Shifter") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = "Logo", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = dynamicTitle, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.SansSerif, letterSpacing = 0.5.sp)
                                Text("by @BuildBytes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(text = dynamicTitle, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.SansSerif, letterSpacing = 0.5.sp)
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
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.padding(end = 8.dp)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !showSettings && appState.flashWizardStep == 0 && appState.migratorMode == MigratorMode.MENU,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NavigationBar(tonalElevation = 8.dp) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            icon = { Icon(imageVector = tab.second, contentDescription = tab.first) },
                            label = { Text(text = tab.third, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index; if (index != 1) viewModel.setMigratorMode(MigratorMode.MENU) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (showSettings) {
                SettingsTab(LocalContext.current, viewModel, isDarkTheme, onThemeToggle)
            } else {
                when (selectedTab) {
                    0 -> FlashTab(LocalContext.current, viewModel)
                    1 -> MigratorTab(appState, viewModel)
                    2 -> ExtrasTab(appState, viewModel)
                }
            }
        }
    }
}

@Composable
fun OnboardingWizard(viewModel: MainViewModel) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(1) }

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

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        step = 4
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (step) {
            1 -> {
                Icon(Icons.Default.Bolt, null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text("Get Started with", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("ROM Shifter", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("The ultimate root-powered tool for migrating apps, backing up telephony data, auto-flashing ZIPs directly in recovery, and modifying your ROM securely.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("Developed by @ShastikXD & the @BuildBytes team.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(32.dp))
                Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("Next") }
            }
            2 -> {
                Icon(Icons.Default.Folder, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text("Shifter Directory", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("ROM Shifter needs a dedicated folder to store your backups, images, and logs safely. You can auto-detect an existing one or select manually.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        viewModel.autoDetectShifterFolder { success ->
                            if (success) step = 3
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)
                ) { Text("Auto-Detect #Shifter Folder") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("Select Folder Manually") }
            }
            3 -> {
                Icon(Icons.Default.Security, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text("Permissions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("To accurately back up native Call Logs, SMS, and Contacts securely via ContentResolver without failing, ROM Shifter requires explicit permissions. These are completely optional, but Native features will not work without them.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))
                Button(onClick = {
                    permLauncher.launch(arrayOf(
                        android.Manifest.permission.READ_SMS,
                        android.Manifest.permission.READ_CALL_LOG,
                        android.Manifest.permission.WRITE_CALL_LOG,
                        android.Manifest.permission.READ_CONTACTS,
                        android.Manifest.permission.WRITE_CONTACTS
                    ))
                }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("Grant Permissions & Next") }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { step = 4 }) { Text("Skip for now") }
            }
            4 -> {
                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text("About & Support", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Made by @ShastikXD | Build Bytes Team", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("If ROM Shifter helped you, please consider starring the repository on GitHub or supporting the project via donations!", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { openUriSafely(context, "https://t.me/buildbytes") }, modifier = Modifier.weight(1f)) { Text("Telegram") }
                    OutlinedButton(onClick = { openUriSafely(context, "https://github.com/ShivamXD6/ROM-Shifter-App/") }, modifier = Modifier.weight(1f)) { Text("GitHub") }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { openUriSafely(context, "upi://pay?pa=shivam.dhage@superyes&pn=Build%20Bytes&cu=INR") }, modifier = Modifier.weight(1f)) { Text("UPI") }
                    Button(onClick = { openUriSafely(context, "https://i.ibb.co/5g4J2RXR/1f38d6d7-a8a2-4696-88e6-9cf503e0592c.png") }, modifier = Modifier.weight(1f)) { Text("GPay") }
                    Button(onClick = { openUriSafely(context, "https://paypal.me/ShivamXD6") }, modifier = Modifier.weight(1f)) { Text("PayPal") }
                }

                Spacer(Modifier.height(32.dp))
                Button(onClick = { viewModel.finishOnboarding() }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("Let's Shift!") }
            }
        }
    }
}

@Composable
fun NoRootScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Warning, contentDescription = "No Root", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Root Access Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Please grant root permissions in Magisk/KernelSU to use ROM Shifter. Then, restart the app.", textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
        }
    }
}
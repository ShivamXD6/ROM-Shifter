package build.bytes.romshifter.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.R
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.MigratorMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

fun openUriSafely(context: Context, uriString: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uriString)))
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to open this link.", Toast.LENGTH_SHORT).show()
    }
}

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

    val isBackEnabled = !appState.isRunning && (appState.migratorMode != MigratorMode.MENU || showSettings || appState.flashWizardStep > 0)

    val backProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isPopping by remember { mutableStateOf(false) }

    val triggerBackNavigation = {
        if (!isPopping) {
            isPopping = true
            scope.launch {
                backProgress.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing))

                if (showSettings) showSettings = false
                else if (appState.flashWizardStep > 0) viewModel.flashWizardStepBack()
                else viewModel.setMigratorMode(MigratorMode.MENU)

                kotlinx.coroutines.delay(50)
                backProgress.snapTo(0f)
                isPopping = false
            }
        }
    }

    PredictiveBackHandler(enabled = isBackEnabled) { progress ->
        try {
            progress.collect { backEvent ->
                backProgress.snapTo(backEvent.progress)
            }
            triggerBackNavigation()
        } catch (e: CancellationException) {
            scope.launch { backProgress.animateTo(0f, animationSpec = tween(350, easing = FastOutSlowInEasing)) }
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    var frozenPreviousAppState by remember {
        mutableStateOf(appState.copy(
            migratorMode = if (showSettings) appState.migratorMode else if (appState.migratorMode != MigratorMode.MENU) MigratorMode.MENU else appState.migratorMode,
            flashWizardStep = if (showSettings) appState.flashWizardStep else if (appState.flashWizardStep > 0) appState.flashWizardStep - 1 else 0
        ))
    }

    LaunchedEffect(appState, showSettings, isPopping) {
        if (!isPopping) {
            frozenPreviousAppState = appState.copy(
                migratorMode = if (showSettings) appState.migratorMode else if (appState.migratorMode != MigratorMode.MENU) MigratorMode.MENU else appState.migratorMode,
                flashWizardStep = if (showSettings) appState.flashWizardStep else if (appState.flashWizardStep > 0) appState.flashWizardStep - 1 else 0
            )
        }
    }

    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim)) {

        if (backProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -(screenWidthPx * 0.3f) * (1f - backProgress.value)
                        scaleX = 0.95f + (backProgress.value * 0.05f)
                        scaleY = 0.95f + (backProgress.value * 0.05f)
                    }
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                AppScaffold(
                    appState = frozenPreviousAppState,
                    showSettings = false,
                    selectedTab = selectedTab,
                    viewModel = viewModel,
                    onTabSelect = {},
                    onSettingsToggle = {},
                    onBackClick = {}
                )
            }
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f * (1f - backProgress.value))))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (backProgress.value > 0f) {
                        translationX = backProgress.value * screenWidthPx
                        val cornerRadius = 32.dp * backProgress.value
                        shape = RoundedCornerShape(cornerRadius)
                        clip = true
                        shadowElevation = 40f
                    }
                }
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AppScaffold(
                appState = appState,
                showSettings = showSettings,
                selectedTab = selectedTab,
                viewModel = viewModel,
                onTabSelect = { selectedTab = it; if (it != 1) viewModel.setMigratorMode(MigratorMode.MENU) },
                onSettingsToggle = { showSettings = it },
                onBackClick = { triggerBackNavigation() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    appState: AppState,
    showSettings: Boolean,
    selectedTab: Int,
    viewModel: MainViewModel,
    onTabSelect: (Int) -> Unit,
    onSettingsToggle: (Boolean) -> Unit,
    onBackClick: () -> Unit
) {
    val tabs = listOf(
        Triple("Flash", Icons.Default.FlashOn, "Flash"),
        Triple("Migrate", Icons.Default.CloudSync, "Migrate"),
        Triple("Extras", Icons.Default.Build, "Extras")
    )

    val dynamicTitle = when {
        showSettings -> "Settings"
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

    val isBackEnabled = !appState.isRunning && (appState.migratorMode != MigratorMode.MENU || showSettings || appState.flashWizardStep > 0)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (dynamicTitle == "ROM Shifter") {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_home),
                                contentDescription = "Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = dynamicTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    if (isBackEnabled) {
                        IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(26.dp)) }
                    }
                },
                actions = {
                    if (appState.migratorMode == MigratorMode.MENU && !showSettings && appState.flashWizardStep == 0) {
                        IconButton(onClick = { onSettingsToggle(true) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
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
                    val goingBack = initialState.first && !targetState.first
                    if (goingBack) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else if (!initialState.first && targetState.first) {
                        slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { it } togetherWith fadeOut(tween(250))
                    } else {
                        fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                    }
                },
                label = "AppContentTransition",
                modifier = Modifier.fillMaxSize()
            ) { (isSettingsOpen, currentTab) ->
                if (isSettingsOpen) {
                    SettingsTab(LocalContext.current, viewModel)
                } else {
                    when (currentTab) {
                        0 -> FlashTab(appState, LocalContext.current, viewModel)
                        1 -> MigratorTab(appState, viewModel)
                        2 -> ExtrasTab(appState, viewModel)
                    }
                }
            }

            AnimatedVisibility(
                visible = !showSettings && appState.flashWizardStep == 0 && appState.migratorMode == MigratorMode.MENU,
                enter = EnterTransition.None,
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400, easing = FastOutLinearInEasing)) + fadeOut(tween(400)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                NavigationBar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(80.dp)
                        .clip(MaterialTheme.shapes.large),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            icon = { Icon(imageVector = tab.second, contentDescription = tab.first, modifier = Modifier.size(24.dp)) },
                            label = { Text(text = tab.third, style = MaterialTheme.typography.labelMedium) },
                            selected = selectedTab == index,
                            onClick = { onTabSelect(index) },
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

@Composable
fun OnboardingWizard(viewModel: MainViewModel) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(1) }

    val backProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isPopping by remember { mutableStateOf(false) }

    val triggerBackNavigation = {
        if (!isPopping) {
            isPopping = true
            scope.launch {
                backProgress.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing))
                step -= 1
                kotlinx.coroutines.delay(50)
                backProgress.snapTo(0f)
                isPopping = false
            }
        }
    }

    PredictiveBackHandler(enabled = step > 1) { progress ->
        try {
            progress.collect { backEvent ->
                backProgress.snapTo(backEvent.progress)
            }
            triggerBackNavigation()
        } catch (e: CancellationException) {
            scope.launch {
                backProgress.animateTo(
                    0f,
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    var frozenBgStep by remember { mutableIntStateOf(if (step > 1) step - 1 else 1) }

    LaunchedEffect(step, isPopping) {
        if (!isPopping) {
            frozenBgStep = if (step > 1) step - 1 else 1
        }
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                val split = docId.split(":")
                val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
                val path = if ("primary".equals(split[0], true)) "$basePath/${split.getOrNull(1) ?: ""}"
                else "/storage/${split[0]}/${split.getOrNull(1) ?: ""}"
                val finalPath = if (path.endsWith("#Shifter")) path else "$path/#Shifter"
                viewModel.migrateFolder(finalPath) { step = 3 }
            }
        }

    val permLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            step = 4
        }
    val notifPermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim)) {

        if (backProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -(screenWidthPx * 0.3f) * (1f - backProgress.value)
                        scaleX = 0.95f + (backProgress.value * 0.05f)
                        scaleY = 0.95f + (backProgress.value * 0.05f)
                    }
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                OnboardingStepContent(
                    step = frozenBgStep,
                    context = context,
                    viewModel = viewModel,
                    onNext = {},
                    onSkip = {},
                    launcher = launcher,
                    permLauncher = permLauncher,
                    notifPermLauncher = notifPermLauncher
                )
            }
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f * (1f - backProgress.value)))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (backProgress.value > 0f) {
                        translationX = backProgress.value * screenWidthPx
                        val cornerRadius = 32.dp * backProgress.value
                        shape = RoundedCornerShape(cornerRadius)
                        clip = true
                        shadowElevation = 40f
                    }
                }
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val goingBack = targetState < initialState
                    if (goingBack) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { it } togetherWith fadeOut(tween(250))
                    }
                },
                label = "OnboardingTransition",
                modifier = Modifier.fillMaxSize()
            ) { currentStep ->
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    OnboardingStepContent(
                        step = currentStep,
                        context = context,
                        viewModel = viewModel,
                        onNext = { step = it },
                        onSkip = { step = it },
                        launcher = launcher,
                        permLauncher = permLauncher,
                        notifPermLauncher = notifPermLauncher
                    )
                }
            }

            AnimatedVisibility(
                visible = step > 1,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart).padding(top = 24.dp, start = 8.dp)
            ) {
                IconButton(onClick = { triggerBackNavigation() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingStepContent(
    step: Int,
    context: Context,
    viewModel: MainViewModel,
    onNext: (Int) -> Unit,
    onSkip: (Int) -> Unit,
    launcher: androidx.activity.compose.ManagedActivityResultLauncher<Uri?, Uri?>,
    permLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Map<String, @JvmSuppressWildcards Boolean>>,
    notifPermLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (step) {
            1 -> {
                Box(
                    modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painter = painterResource(id = R.drawable.ic_home), contentDescription = "ROM Shifter", modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)                }
                Spacer(Modifier.height(32.dp))
                Text("Get Started with", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("ROM Shifter", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("The ultimate root-powered tool for migrating apps, backing up telephony data, auto-flashing ZIPs directly in recovery, and modifying your ROM securely.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(48.dp))
                Button(onClick = { onNext(2) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text("Next", style = MaterialTheme.typography.titleMedium) }
            }
            2 -> {
                Box(
                    modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(32.dp))
                Text("Shifter Directory", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("ROM Shifter needs a dedicated folder to store your backups, images, and logs safely. You can auto-detect an existing one or select manually.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(48.dp))
                Button(onClick = { viewModel.autoDetectShifterFolder { success -> if (success) onNext(3) } }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text("Auto-Detect #Shifter Folder", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text("Select Folder Manually", style = MaterialTheme.typography.titleMedium) }
            }
            3 -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    LaunchedEffect(Unit) {
                        notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Box(
                    modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(32.dp))
                Text("Permissions", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("To accurately back up native Call Logs, SMS, and Contacts securely via ContentResolver without failing, ROM Shifter requires explicit permissions. Notifications are also needed to track background progress.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(48.dp))
                Button(onClick = {
                    permLauncher.launch(arrayOf(
                        android.Manifest.permission.READ_SMS,
                        android.Manifest.permission.READ_CALL_LOG,
                        android.Manifest.permission.WRITE_CALL_LOG,
                        android.Manifest.permission.READ_CONTACTS,
                        android.Manifest.permission.WRITE_CONTACTS
                    ))
                }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text("Grant Permissions & Next", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onSkip(4) }) { Text("Skip for now", style = MaterialTheme.typography.titleMedium) }
            }
            4 -> {
                Box(
                    modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Favorite, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(24.dp))
                Text("Made by @ShastikXD", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text("If ROM Shifter helped you, please consider starring the repository on GitHub or supporting the project via donations!", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { openUriSafely(context, "https://t.me/buildbytes") }, modifier = Modifier.weight(1f).height(48.dp), shape = CircleShape) { Text("Telegram") }
                    FilledTonalButton(onClick = { openUriSafely(context, "https://www.youtube.com/@BuildBytesX") }, modifier = Modifier.weight(1f).height(48.dp), shape = CircleShape) { Text("YouTube") }
                    FilledTonalButton(onClick = { openUriSafely(context, "https://github.com/ShivamXD6/ROM-Shifter-App/") }, modifier = Modifier.weight(1f).height(48.dp), shape = CircleShape) { Text("GitHub") }
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openUriSafely(context, "upi://pay?pa=shivamashokdhage6@oksbi&pn=Build%20Bytes&cu=INR") }, modifier = Modifier.weight(1f).height(48.dp), shape = CircleShape) { Text("UPI (Any)") }
                    Button(onClick = { openUriSafely(context, "https://paypal.me/ShivamXD6") }, modifier = Modifier.weight(1f).height(48.dp), shape = CircleShape) { Text("PayPal") }
                }

                Spacer(Modifier.height(32.dp))
                Button(onClick = { viewModel.finishOnboarding() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text("Let's Shift!", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
fun NoRootScreen() {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(24.dp), contentAlignment = Alignment.Center) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Box(
                    modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "No Root", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(56.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Root Access Required", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Please grant root permissions in Magisk/KernelSU to use ROM Shifter. Then, restart the app.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
package build.bytes.romshifter.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import build.bytes.romshifter.MainViewModel
import build.bytes.romshifter.R
import build.bytes.romshifter.models.AppInfo
import build.bytes.romshifter.models.AppState
import build.bytes.romshifter.models.FlashAction
import build.bytes.romshifter.models.MigratorMode
import build.bytes.romshifter.ui.components.AppInstallerDialog
import build.bytes.romshifter.ui.components.BatchInstallerDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

fun openUriSafely(context: Context, uriString: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uriString.toUri()))
    } catch (_: Exception) {
        Toast.makeText(context, "No app available to open this link.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val appState by viewModel.uiState.collectAsState()
    val appList by viewModel.appList.collectAsState()
    val flashActions by viewModel.flashActions.collectAsState()
    val isFirstRun by viewModel.isFirstRun.collectAsState()
    val selectedTab by viewModel.currentTab.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val context = LocalContext.current

    if (appState.showAppInstaller && appState.batchInstallApps.isNotEmpty()) {
        if (appState.batchInstallApps.size == 1 && !appState.isRunning && appState.totalInstallTimeSeconds == 0L) {
            AppInstallerDialog(
                app = appState.batchInstallApps[0],
                onDismiss = { viewModel.closeAppInstaller { (context as? android.app.Activity)?.finish() } },
                onInstall = { viewModel.executeBatchInstall() }
            )
        } else {
            BatchInstallerDialog(
                apps = appState.batchInstallApps,
                isRunning = appState.isRunning,
                isAnalyzing = appState.isAnalyzingApps,
                currentStep = appState.currentStep,
                totalTime = appState.totalInstallTimeSeconds,
                onInstall = { viewModel.executeBatchInstall() },
                onCancel = { viewModel.closeAppInstaller { (context as? android.app.Activity)?.finish() } },
                onToggleSelect = { viewModel.toggleAppInstallSelection(it) }
            )
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { viewModel.showUpdateDialog.value = false },
            icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
            title = { Text("Update Available: ${updateInfo!!.version}") },
            text = {
                val parsedChangelog = formatChangelog(
                    text = updateInfo!!.changelog,
                    linkColor = MaterialTheme.colorScheme.primary
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Release Notes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .animateContentSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(16.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Text(
                            text = parsedChangelog,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.showUpdateDialog.value = false
                        val intent = Intent(Intent.ACTION_VIEW, updateInfo!!.downloadUrl.toUri())
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                ) {
                    Text("Download Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showUpdateDialog.value = false }) {
                    Text("Later")
                }
            }
        )
    }

    if (!appState.hasRoot) {
        NoRootScreen(
            isChecking = appState.isFetchingApps,
            onRestart = {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                    Runtime.getRuntime().exit(0)
                } else {
                    viewModel.checkRootAccess()
                }
            }
        )
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

                kotlinx.coroutines.delay(50.milliseconds)
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
        } catch (_: CancellationException) {
            scope.launch { backProgress.animateTo(0f, animationSpec = tween(350, easing = FastOutSlowInEasing)) }
        }
    }

    val screenWidthPx =
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toFloat()

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
    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {

        if (backProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -(screenWidthPx * 0.3f) * (1f - backProgress.value)
                        scaleX = 0.95f + (backProgress.value * 0.05f)
                        scaleY = 0.95f + (backProgress.value * 0.05f)
                    }
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AppScaffold(
                    appState = frozenPreviousAppState,
                    appList = emptyList(),
                    flashActions = emptyList(),
                    showSettings = false,
                    selectedTab = selectedTab,
                    viewModel = viewModel,
                    onTabSelect = {},
                    onSettingsToggle = {},
                    onBackClick = {},
                )
        }
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AppScaffold(
                appState = appState,
                appList = appList,
                flashActions = flashActions,
                showSettings = showSettings,
                selectedTab = selectedTab,
                viewModel = viewModel,
                onTabSelect = {
                    viewModel.setTab(it); if (it != 1) viewModel.setMigratorMode(
                    MigratorMode.MENU
                )
                },
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
    appList: List<AppInfo>,
    flashActions: List<FlashAction>,
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
        Triple("Tools", Icons.Default.Build, "Tools")
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
            }
        }

        else -> "ROM Shifter"
    }

    val isHome = dynamicTitle == "ROM Shifter"
    val isBackEnabled =
        !appState.isRunning && (appState.migratorMode != MigratorMode.MENU || showSettings || appState.flashWizardStep > 0)

    val pagerState = rememberPagerState(initialPage = selectedTab, pageCount = { 3 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != selectedTab) {
            onTabSelect(pagerState.settledPage)
        }
    }

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "logoPulse")
    val logoAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoAlpha"
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.zIndex(1f)
            ) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isHome) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_home),
                                    contentDescription = "Logo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .graphicsLayer {
                                            alpha = if (appState.isRunning) logoAlpha else 1f
                                        }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(
                                text = dynamicTitle,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    navigationIcon = {
                        if (isBackEnabled) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    "Back",
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        if (isHome) {
                            IconButton(
                                onClick = { onSettingsToggle(true) },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_home),
                                contentDescription = "Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(32.dp)
                                    .graphicsLayer {
                                        alpha = if (appState.isRunning) logoAlpha else 1f
                                    }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .padding(top = innerPadding.calculateTopPadding())
            .fillMaxSize()) {

            AnimatedContent(
                targetState = showSettings,
                transitionSpec = {
                    if (initialState && !targetState) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else if (!initialState && targetState) {
                        slideInHorizontally(
                            tween(
                                250,
                                easing = FastOutSlowInEasing
                            )
                        ) { it } togetherWith fadeOut(tween(250))
                    } else {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    }
                },
                label = "AppContentTransition",
                modifier = Modifier.fillMaxSize()
            ) { isSettingsOpen ->
                if (isSettingsOpen) {
                    SettingsTab(LocalContext.current, viewModel)
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !appState.isRunning && appState.migratorMode == MigratorMode.MENU,
                        beyondViewportPageCount = 1
                    ) { page ->
                        when (page) {
                            0 -> FlashTab(appState, flashActions, LocalContext.current, viewModel)
                            1 -> MigratorTab(appState, appList, viewModel)
                            2 -> ToolsTab(appState, appList, viewModel)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !showSettings && appState.flashWizardStep == 0 && appState.migratorMode == MigratorMode.MENU,
                enter = EnterTransition.None,
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(400, easing = FastOutLinearInEasing)
                ) + fadeOut(tween(400)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {

                Surface(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .height(64.dp)
                        .fillMaxWidth()
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = selectedTab == index

                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.1f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "tabIconScale"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        onTabSelect(index)
                                        scope.launch {
                                            pagerState.animateScrollToPage(
                                                page = index,
                                                animationSpec = tween(
                                                    durationMillis = 350,
                                                    easing = FastOutSlowInEasing
                                                )
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = iconScale; scaleY = iconScale
                                        }
                                        .height(42.dp)
                                        .fillMaxWidth(if (isSelected) 0.95f else 0.5f)
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(horizontal = if (isSelected) 8.dp else 0.dp)
                                    ) {
                                        Icon(
                                            imageVector = tab.second,
                                            contentDescription = tab.first,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = tab.third,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Visible
                                            )
                                        }
                                    }
                                }
                            }
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

    val backProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isPopping by remember { mutableStateOf(false) }

    var hasAttemptedAutoDetect by remember { mutableStateOf(false) }

    val triggerBackNavigation = {
        if (!isPopping) {
            isPopping = true
            scope.launch {
                backProgress.animateTo(1f, animationSpec = tween(350, easing = FastOutSlowInEasing))
                step -= 1
                kotlinx.coroutines.delay(50.milliseconds)
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
        } catch (_: CancellationException) {
            scope.launch {
                backProgress.animateTo(
                    0f,
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    val screenWidthPx =
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toFloat()

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
                val finalPath = if (path.endsWith("Shifter")) path else "$path/Shifter"
                viewModel.migrateFolder(finalPath) { step = 3 }
            }
        }

    val notifPermLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {

        if (backProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -(screenWidthPx * 0.3f) * (1f - backProgress.value)
                        scaleX = 0.95f + (backProgress.value * 0.05f)
                        scaleY = 0.95f + (backProgress.value * 0.05f)
                    }
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                OnboardingStepContent(
                    step = frozenBgStep,
                    context = context,
                    viewModel = viewModel,
                    onNext = {},
                    launcher = launcher,
                    notifPermLauncher = notifPermLauncher,
                    isBackground = true,
                    hasAttemptedAutoDetect = hasAttemptedAutoDetect,
                    onSetAttemptedAutoDetect = {},
                    onBack = {}
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                .background(MaterialTheme.colorScheme.surface)
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
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    OnboardingStepContent(
                        step = currentStep,
                        context = context,
                        viewModel = viewModel,
                        onNext = { step = it },
                        launcher = launcher,
                        notifPermLauncher = notifPermLauncher,
                        hasAttemptedAutoDetect = hasAttemptedAutoDetect,
                        onSetAttemptedAutoDetect = { hasAttemptedAutoDetect = true },
                        onBack = { triggerBackNavigation() }
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
    launcher: androidx.activity.compose.ManagedActivityResultLauncher<Uri?, Uri?>,
    notifPermLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>,
    isBackground: Boolean = false,
    hasAttemptedAutoDetect: Boolean = false,
    onSetAttemptedAutoDetect: () -> Unit = {},
    onBack: () -> Unit = {}
) {

    val iconScale = remember { Animatable(if (isBackground) 1f else 0.3f) }
    LaunchedEffect(step) {
        if (!isBackground) {
            iconScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                1 -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        LaunchedEffect(Unit) {
                            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    var isDetecting by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer { scaleX = iconScale.value; scaleY = iconScale.value }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_home),
                            contentDescription = "ROM Shifter",
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(
                        "Get Started with",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "ROM Shifter",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "The Ultimate Android app built to make flashing, backing up, and migrating between custom ROMs as painless as possible. As well provides tools for some common things we do after switching to another ROM.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(48.dp))

                    Button(
                        onClick = {
                            if (!hasAttemptedAutoDetect) {
                                isDetecting = true
                                viewModel.autoDetectShifterFolder { success ->
                                    isDetecting = false
                                    onSetAttemptedAutoDetect()
                                    if (success) {
                                        onNext(3)
                                    } else {
                                        onNext(2)
                                    }
                                }
                            } else {
                                onNext(2)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = CircleShape,
                        enabled = !isDetecting
                    ) {
                        if (isDetecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Searching...", style = MaterialTheme.typography.titleMedium)
                        } else {
                            Text("Next", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                2 -> {
                    val savedPath by viewModel.savedPath.collectAsState()
                    var inputPath by remember { mutableStateOf(savedPath) }

                    LaunchedEffect(savedPath) { inputPath = savedPath }

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer { scaleX = iconScale.value; scaleY = iconScale.value }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(
                        "Shifter Directory",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Manually browse or enter the path where you want to store the files.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(48.dp))

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
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.migrateFolder(inputPath) { onNext(3) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = CircleShape
                    ) {
                        Text(
                            "Confirm & Next",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { launcher.launch(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = CircleShape
                    ) {
                        Text(
                            "Browse Storage",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                3 -> {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer { scaleX = iconScale.value; scaleY = iconScale.value }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Made by @ShivamXD6",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "If ROM Shifter helped you, please consider starring the repository on GitHub or supporting the project via donations or sponsors!",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                openUriSafely(
                                    context,
                                    "https://t.me/buildbytes"
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = CircleShape
                        ) { Text("Telegram") }
                        FilledTonalButton(
                            onClick = {
                                openUriSafely(
                                    context,
                                    "https://www.youtube.com/@BuildBytesX"
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = CircleShape
                        ) { Text("YouTube") }
                        FilledTonalButton(
                            onClick = {
                                openUriSafely(
                                    context,
                                    "https://github.com/ShivamXD6/ROM-Shifter/"
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = CircleShape
                        ) { Text("Source") }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                openUriSafely(
                                    context,
                                    "https://github.com/sponsors/ShivamXD6"
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = CircleShape
                        ) {
                            Text(
                                "Sponsor",
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
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
                        ) {
                            Text(
                                "UPI",
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { openUriSafely(context, "https://paypal.me/ShivamXD6") },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = CircleShape
                        ) {
                            Text(
                                "PayPal",
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.finishOnboarding() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = CircleShape
                    ) { Text("Let's Shift!", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
        if (step > 1) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 24.dp, start = 8.dp)
            ) {
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

@Composable
fun NoRootScreen(isChecking: Boolean, onRestart: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(24.dp), contentAlignment = Alignment.Center) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = pulseAlpha)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "No Root", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(56.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Root Access Required", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Please grant root permissions in Magisk/KernelSU to use ROM Shifter. Then, tap restart.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onRestart,
                    enabled = !isChecking,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Checking...", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text("Restart", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun formatChangelog(text: String, linkColor: Color): AnnotatedString {
    val titleLargeStyle = MaterialTheme.typography.titleLarge.toSpanStyle().copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    val titleMediumStyle = MaterialTheme.typography.titleMedium.toSpanStyle().copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    val codeStyle = SpanStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        background = MaterialTheme.colorScheme.surfaceVariant,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val quoteStyle = SpanStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
    )

    return buildAnnotatedString {
        val lines = text.split("\n")

        lines.forEachIndexed { index, line ->
            var currentLine = line
            var lineStyle: SpanStyle? = null
            var prefix = ""

            if (currentLine.matches(Regex("^-{3,}$|^\\*{3,}$|^_{3,}$"))) {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.outlineVariant)) {
                    append("────────────────────────────────")
                }
            } else {
                if (currentLine.startsWith("### ")) {
                    currentLine = currentLine.removePrefix("### ")
                    lineStyle = titleMediumStyle
                } else if (currentLine.startsWith("## ")) {
                    currentLine = currentLine.removePrefix("## ")
                    lineStyle = titleLargeStyle
                } else if (currentLine.startsWith("# ")) {
                    currentLine = currentLine.removePrefix("# ")
                    lineStyle = titleLargeStyle
                }

                if (currentLine.startsWith("> ")) {
                    currentLine = currentLine.removePrefix("> ")
                    val isImportant = currentLine.contains(
                        "Note:",
                        ignoreCase = true
                    ) || currentLine.contains(
                        "Warning:",
                        ignoreCase = true
                    ) || currentLine.contains("Important:", ignoreCase = true)

                    lineStyle = if (isImportant) {
                        quoteStyle.copy(color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                    } else {
                        quoteStyle
                    }
                    prefix = "┃ "
                }

                if (currentLine.trimStart().startsWith("- ") || currentLine.trimStart()
                        .startsWith("* ")
                ) {
                    val indent = currentLine.takeWhile { it.isWhitespace() }
                    currentLine = currentLine.trimStart().substring(2)
                    prefix = "$indent• "
                }

                val lineStart = length
                append(prefix)

                parseInlineStyles(currentLine, codeStyle).forEach { part ->
                    val partStart = length
                    append(part.text)
                    part.styles.forEach { (style, range) ->
                        addStyle(style, partStart + range.first, partStart + range.last)
                    }
                }

                if (lineStyle != null) {
                    addStyle(lineStyle, lineStart, length)
                }
            }

            if (index < lines.size - 1) {
                append("\n")
            }
        }

        val urlRegex = Regex("(https?://\\S+)")
        urlRegex.findAll(this.toAnnotatedString().text).forEach { match ->
            addLink(
                LinkAnnotation.Url(
                    url = match.value,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                ),
                match.range.first,
                match.range.last + 1
            )
        }
    }
}

private data class StyledPart(val text: String, val styles: List<Pair<SpanStyle, IntRange>>)

private fun parseInlineStyles(text: String, codeStyle: SpanStyle): List<StyledPart> {
    val result = mutableListOf<StyledPart>()

    val codeRegex = Regex("`([^`]+)`")
    var lastMatchEnd = 0

    codeRegex.findAll(text).forEach { match ->
        if (match.range.first > lastMatchEnd) {
            result.add(parseBoldItalic(text.substring(lastMatchEnd, match.range.first)))
        }
        result.add(
            StyledPart(
                match.groupValues[1],
                listOf(codeStyle to 0.rangeTo(match.groupValues[1].length))
            )
        )
        lastMatchEnd = match.range.last + 1
    }

    if (lastMatchEnd < text.length) {
        result.add(parseBoldItalic(text.substring(lastMatchEnd)))
    }

    return if (result.isEmpty()) listOf(parseBoldItalic(text)) else result
}

private fun parseBoldItalic(text: String): StyledPart {
    val styles = mutableListOf<Pair<SpanStyle, IntRange>>()

    val boldRegex = Regex("(\\*\\*|__)(.*?)\\1")
    boldRegex.findAll(text).forEach { match ->
        styles.add(SpanStyle(fontWeight = FontWeight.Bold) to match.range.first.rangeTo(match.range.last + 1))
    }

    val italicRegex =
        Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)|(?<!_)_(?!_)(.*?)(?<!_)_(?!_)")
    italicRegex.findAll(text).forEach { match ->
        styles.add(
            SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) to match.range.first.rangeTo(
                match.range.last + 1
            )
        )
    }


    val finalBuilder = AnnotatedString.Builder()
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) || text.startsWith("__", i) -> {
                val marker = text.substring(i, i + 2)
                val end = text.indexOf(marker, i + 2)
                if (end != -1) {
                    finalBuilder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    finalBuilder.append(text[i])
                    i++
                }
            }

            text.startsWith("*", i) || text.startsWith("_", i) -> {
                val marker = text[i].toString()
                val end = text.indexOf(marker, i + 1)
                if (end != -1 && (end + 1 == text.length || text[end + 1] != text[end])) {
                    finalBuilder.withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    finalBuilder.append(text[i])
                    i++
                }
            }

            else -> {
                finalBuilder.append(text[i])
                i++
            }
        }
    }

    return StyledPart(
        finalBuilder.toAnnotatedString().text,
        finalBuilder.toAnnotatedString().spanStyles.map { it.item to it.start.rangeTo(it.end) })
}

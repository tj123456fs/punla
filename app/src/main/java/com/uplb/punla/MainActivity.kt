package com.uplb.punla

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreHoriz
import com.uplb.punla.ui.screens.MoreScreen
import com.uplb.punla.ui.screens.QuickCaptureSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.data.ThemeMode
import com.uplb.punla.data.entity.NotificationEvent
import com.uplb.punla.notification.TrackedNotification
import com.uplb.punla.ui.LogoIntroScreen
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ui.pomodoro.PomodoroPhase
import com.uplb.punla.ui.screens.BudgetScreen
import com.uplb.punla.ui.screens.CampusMapScreen
import com.uplb.punla.ui.screens.ChecklistScreen
import com.uplb.punla.ui.screens.DashboardScreen
import com.uplb.punla.ui.screens.DeadlinesScreen
import com.uplb.punla.ui.screens.GradesScreen
import com.uplb.punla.ui.screens.glassCard
import com.uplb.punla.ui.screens.PomodoroScreen
import com.uplb.punla.ui.screens.ScheduleScreen
import com.uplb.punla.ui.screens.SettingsScreen
import com.uplb.punla.ui.screens.SystemHealthScreen
import com.uplb.punla.ui.screens.StudyAnalysisScreen
import com.uplb.punla.ui.screens.AssistantScreen
import com.uplb.punla.ui.screens.FlashcardsScreen
import com.uplb.punla.ui.screens.QuizScreen
import com.uplb.punla.ui.screens.StudyHubScreen
import androidx.compose.material.icons.filled.AutoAwesome
import com.uplb.punla.ui.theme.appBackground
import com.uplb.punla.ui.theme.punlaDisplayFamily
import com.uplb.punla.ui.theme.PunlaMono
import com.uplb.punla.ui.theme.PunlaTheme
import com.uplb.punla.worker.ClassDayNotificationScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.uplb.punla.ui.screens.CampusFullMapScreen


class MainActivity : ComponentActivity() {
    private val vm: PunlaViewModel by viewModels()
    private val pipModeState = mutableStateOf(false)

    companion object {
        /** Intent extra used by the home-screen widgets to jump straight to
         * a tab (e.g. "budget", "schedule", "deadlines") instead of always
         * opening on the dashboard. */
        const val EXTRA_START_ROUTE = "start_route"
        /** Optional room/building query used by notification navigation. */
        const val EXTRA_MAP_QUERY = "map_query"
    }

    private val notificationPermissionGrantedState = mutableStateOf(true)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        notificationPermissionGrantedState.value = isGranted
        vm.toggleNotifications(isGranted)
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationPermissionGrantedState.value = true
            vm.toggleNotifications(true)
        }
    }

    // Backed by mutableState (not just `intent`) so that a widget tap while
    // MainActivity is already running — which triggers onNewIntent() rather
    // than a fresh onCreate() — still re-navigates instead of being ignored.
    private val startRouteState = mutableStateOf<String?>(null)
    // A repeated tap can request the exact same route string. Snapshot state does
    // not emit when a value is assigned to itself, so keep a monotonically
    // increasing request token to make every external navigation intent one-shot.
    private val startRouteRequestIdState = mutableStateOf(0L)

    private fun recordStartRouteRequest(route: String?) {
        startRouteState.value = route
        if (route != null) startRouteRequestIdState.value = startRouteRequestIdState.value + 1L
    }

    private fun recordNotificationOpen(intent: Intent?) {
        if (intent?.getStringExtra(TrackedNotification.EXTRA_OUTCOME) != "OPENED") return
        val key = intent.getStringExtra(TrackedNotification.EXTRA_KEY) ?: return
        val worker = intent.getStringExtra(TrackedNotification.EXTRA_WORKER) ?: "unknown"
        val type = intent.getStringExtra(TrackedNotification.EXTRA_TYPE) ?: "general"
        // Remove the marker immediately so configuration changes and repeated
        // lifecycle callbacks do not duplicate the same OPENED event.
        intent.removeExtra(TrackedNotification.EXTRA_OUTCOME)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                PunlaRepository(applicationContext).logNotificationEvent(
                    NotificationEvent(
                        notificationKey = key,
                        workerName = worker,
                        notificationType = type,
                        localHour = java.time.LocalDateTime.now().hour,
                        outcome = "OPENED"
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Navigation/opening the app must not fail because optional telemetry could not be stored.
            }
        }
    }

    private fun updatePomodoroPictureInPictureParams(shouldAutoEnter: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(1, 1))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(shouldAutoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        setPictureInPictureParams(builder.build())
    }

    private fun pomodoroCanEnterPictureInPicture(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            vm.pomodoroPictureInPicture &&
            vm.pomodoroState.isRunning

    private fun enterPomodoroPictureInPicture(): Boolean {
        if (!pomodoroCanEnterPictureInPicture() || isInPictureInPictureMode) return false
        return runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(1, 1))
                    .build()
            )
        }.getOrDefault(false)
    }

    override fun onPictureInPictureRequested(): Boolean {
        // Android 11+ can request PiP directly when this activity is being
        // backgrounded. Android 12+ normally auto-enters first; this remains
        // a fallback for launchers/OEMs that dispatch the callback instead.
        return enterPomodoroPictureInPicture() || super.onPictureInPictureRequested()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ uses auto-enter for a smoother gesture transition.
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
            enterPomodoroPictureInPicture()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipModeState.value = isInPictureInPictureMode
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recordStartRouteRequest(intent.getStringExtra(EXTRA_START_ROUTE))
        intent.getStringExtra(EXTRA_MAP_QUERY)?.let(vm::searchOnMap)
        recordNotificationOpen(intent)
    }

    override fun onResume() {
        super.onResume()
        notificationPermissionGrantedState.value = hasNotificationPermission()
        vm.syncPomodoroClock()
        if (vm.notificationsEnabled && vm.classDayNotificationEnabled) {
            ClassDayNotificationScheduler.refresh(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordNotificationOpen(intent)
        // Roadmap A: draw behind the system bars instead of stopping short
        // of them — lets the ink-colored topbar reach the status bar, and
        // gets ahead of the Android 15+ edge-to-edge requirement once
        // targetSdk moves to 35.
        enableEdgeToEdge()
        // Let Navigation/Compose restore their own state after configuration or
        // process recreation. Replaying an old widget/deep-link intent here would
        // unexpectedly kick the user back to the base destination on rotation.
        if (savedInstanceState == null) {
            recordStartRouteRequest(intent?.getStringExtra(EXTRA_START_ROUTE))
        }
        // The map query itself is ViewModel state. Reapplying the same query is
        // harmless on rotation and preserves it if Android recreates the process.
        intent?.getStringExtra(EXTRA_MAP_QUERY)?.let(vm::searchOnMap)
        notificationPermissionGrantedState.value = hasNotificationPermission()

        setContent {
            // Resolves against the live system setting so ThemeMode.SYSTEM
            // reacts immediately if the device switches light/dark (e.g. by
            // schedule) while the app is open, not just at next launch.
            val systemDark = isSystemInDarkTheme()
            val isDark = when (vm.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }
            val startRoute by startRouteState
            val startRouteRequestId by startRouteRequestIdState
            val notificationPermissionGranted by notificationPermissionGrantedState
            val inPictureInPicture by pipModeState
            val pomodoroRunning by remember { derivedStateOf { vm.pomodoroState.isRunning } }
            val pomodoroPiPEnabled = vm.pomodoroPictureInPicture

            LaunchedEffect(pomodoroRunning, pomodoroPiPEnabled) {
                updatePomodoroPictureInPictureParams(pomodoroRunning && pomodoroPiPEnabled)
            }
            val permissionPrefs = remember { getSharedPreferences("punla_permissions", MODE_PRIVATE) }
            var showNotificationRationale by rememberSaveable {
                mutableStateOf(
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !notificationPermissionGranted &&
                        !permissionPrefs.getBoolean("notification_rationale_seen", false)
                )
            }

            // Feature request — logo animation on open. rememberSaveable so
            // a rotation mid-animation doesn't replay it, but a fresh
            // process (i.e. actually opening the app) always does.
            var showIntro by rememberSaveable { mutableStateOf(true) }

            PunlaTheme(darkTheme = isDark, preset = vm.themePreset, customSeedArgb = vm.customSeedColor, fontChoice = vm.fontChoice) {
                if (inPictureInPicture) {
                    PomodoroPictureInPictureContent(vm)
                } else {
                    Crossfade(targetState = showIntro, animationSpec = tween(350), label = "launch_intro") { intro ->
                        if (intro) {
                            LogoIntroScreen(userName = vm.userName, onFinished = { showIntro = false })
                        } else {
                            PunlaApp(
                                vm = vm,
                                startRoute = startRoute,
                                startRouteRequestId = startRouteRequestId,
                                darkTheme = isDark,
                                notificationPermissionGranted = notificationPermissionGranted,
                                onRequestNotificationPermission = { requestNotificationPermission() }
                            )
                        }
                    }
                }

                if (!inPictureInPicture && !showIntro && showNotificationRationale) {
                    AlertDialog(
                        onDismissRequest = {
                            permissionPrefs.edit().putBoolean("notification_rationale_seen", true).apply()
                            vm.toggleNotifications(false)
                            showNotificationRationale = false
                        },
                        title = { Text("Stay ahead of classes and deadlines") },
                        text = {
                            Text("Punla can remind you before class, keep a silent current-class card, alert you about deadlines, and flag budget items that need attention. You can change this anytime in Settings.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                permissionPrefs.edit().putBoolean("notification_rationale_seen", true).apply()
                                showNotificationRationale = false
                                requestNotificationPermission()
                            }) { Text("Enable notifications") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                permissionPrefs.edit().putBoolean("notification_rationale_seen", true).apply()
                                vm.toggleNotifications(false)
                                showNotificationRationale = false
                            }) { Text("Not now") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PomodoroPictureInPictureContent(vm: PunlaViewModel) {
    val state = vm.pomodoroState
    val stagedSeconds = when (state.phase) {
        PomodoroPhase.WORK -> vm.pomodoroWorkMinutes * 60
        PomodoroPhase.SHORT_BREAK -> vm.pomodoroShortBreakMinutes * 60
        PomodoroPhase.LONG_BREAK -> vm.pomodoroLongBreakMinutes * 60
        PomodoroPhase.IDLE -> vm.pomodoroWorkMinutes * 60
    }
    val shownSeconds = if (!state.isRunning && state.remainingSeconds == 0 && state.phase != PomodoroPhase.IDLE) {
        stagedSeconds
    } else {
        state.remainingSeconds
    }.coerceAtLeast(0)
    val label = when (state.phase) {
        PomodoroPhase.WORK -> "FOCUS"
        PomodoroPhase.SHORT_BREAK -> "SHORT BREAK"
        PomodoroPhase.LONG_BREAK -> "LONG BREAK"
        PomodoroPhase.IDLE -> "READY"
    }
    val progress = if (state.totalSecondsForPhase > 0) {
        shownSeconds.toFloat() / state.totalSecondsForPhase.toFloat()
    } else 1f

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 6.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%02d:%02d".format(shownSeconds / 60, shownSeconds % 60),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = PunlaMono,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

// Daily destinations. Existing routes stay available through More and deep links.
private val BOTTOM_TABS = listOf(
    Tab("dashboard", "Today", Icons.Default.Home),
    Tab("student-os", "Plan", Icons.Default.CalendarMonth),
    Tab("study", "Study", Icons.Default.School),
    Tab("more", "More", Icons.Default.MoreHoriz)
)
private val DRAWER_ITEMS = listOf(
    Tab("schedule", "Schedule", Icons.Default.CalendarMonth),
    Tab("deadlines", "Deadlines", Icons.Default.Flag),
    Tab("budget", "Budget", Icons.Default.AttachMoney),
    Tab("grades", "Grades", Icons.Default.Grade),
    Tab("campus", "Campus", Icons.Default.Map),
    Tab("checklist", "Before Classes Start", Icons.Default.Checklist),
    Tab("pomodoro", "Focus", Icons.Default.Timer),
    Tab("flashcards", "Flashcards", Icons.Default.Style),
    Tab("quizzes", "Quizzes", Icons.Default.Help),
    Tab("assistant", "Assistant", Icons.Default.SmartToy),
    Tab("settings", "Settings", Icons.Default.Settings)
)

// Every reachable top-level destination, bottom bar + drawer combined —
// used to validate widget/worker deep-link start routes (see
// EXTRA_START_ROUTE) without hardcoding the route list twice.
private val ALL_DESTINATIONS = BOTTOM_TABS + DRAWER_ITEMS

/** Extracts the route template's base segment, ignoring query args, e.g.
 * "schedule?quickAdd=true" -> "schedule". Used to match against TABS/DRAWER_ITEMS. */
private fun NavBackStackEntry?.baseRoute(): String? =
    this?.destination?.route?.substringBefore('?')

/** True when both sides of a navigation are peer bottom-tab destinations
 * (a tab's own quick-add variant, e.g. "budget?quickAdd=true", still
 * counts — it's the same base route). Lets the NavHost transitions below
 * tell a sibling tab switch apart from a real hierarchical push/pop into a
 * drawer destination, instead of animating both the same way. */
private fun isTabSwitch(from: NavBackStackEntry, to: NavBackStackEntry): Boolean =
    BOTTOM_TABS.any { it.route == from.baseRoute() } && BOTTOM_TABS.any { it.route == to.baseRoute() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PunlaApp(
    vm: PunlaViewModel,
    startRoute: String? = null,
    startRouteRequestId: Long = 0L,
    darkTheme: Boolean = false,
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {}
) {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry.baseRoute()
    val currentTitle = BOTTOM_TABS.firstOrNull { it.route == currentRoute }?.label
        ?: DRAWER_ITEMS.firstOrNull { it.route == currentRoute }?.label
        ?: when (currentRoute) {
            "settings" -> "Settings"
            "system-health" -> "System Health"
            "checklist" -> "Checklist"
            "campus" -> "Campus"
            "campus/fullmap" -> "Campus Map"
            "pomodoro" -> "Focus"
            "study-analysis" -> "Study Analysis"
            "study" -> "Study Hub"
            "flashcards" -> "Flashcards"
            "quizzes" -> "Quizzes"
            "student-os" -> "Plan & Inbox"
            "assistant" -> "Assistant"
            else -> "Punla"
        }
    val onSettings = currentRoute == "settings" || currentRoute == "system-health"
    // Secondary destinations have an explicit route back to the daily tabs.
    val showBackArrow = currentRoute != null && BOTTOM_TABS.none { it.route == currentRoute }
    val selectedTabRoute = if (BOTTOM_TABS.any { it.route == currentRoute }) currentRoute else "more"

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }
    var captureText by rememberSaveable { mutableStateOf("") }
    var captureSaving by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    val osSnapshot by vm.studentOs.state.collectAsStateWithLifecycle()
    val inboxCount = osSnapshot.captures.count { !it.processed }
    val snackbar = remember { SnackbarHostState() }
    val useNavigationRail = LocalConfiguration.current.screenWidthDp >= 600

    // Decorative atmosphere yields to content motion. LazyColumn/LazyRow scrolls
    // bubble nested-scroll events up here. The background renderer uses this signal
    // to ease into a slower virtual clock / lower publish cadence while scrolling,
    // instead of hard-freezing and abruptly resuming the animation.
    var contentInMotion by remember { mutableStateOf(false) }
    val interactionScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!contentInMotion) contentInMotion = true
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                contentInMotion = false
                return Velocity.Zero
            }
        }
    }

    // Navigates to a base route (drawer/bottom-nav taps) without triggering
    // any quick-add form.
    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Navigates to a destination AND flags it to open its "add" form
    // immediately — the Android equivalent of the web app's quick-add arc,
    // which jumps straight to a given tab's create form from anywhere.
    fun quickAddTo(route: String) {
        quickAddOpen = false
        if (route == "student-os") { navController.navigate("student-os?tab=1"); return }
        val quickAddToken = "${System.currentTimeMillis()}-${System.nanoTime()}"
        navController.navigate("$route?quickAdd=true&quickAddToken=$quickAddToken") {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
        }
    }

    // One-shot jump to whatever tab a widget tap asked for (e.g. tapping the
    // Budget widget's body opens straight to "budget" instead of dashboard).
    LaunchedEffect(startRoute, startRouteRequestId) {
        if (startRoute != null && (ALL_DESTINATIONS.any { it.route == startRoute } || startRoute == "student-os?tab=1")) {
            navigateTo(startRoute)
        }
    }

    // The drawer's swipe-anywhere-to-open gesture normally lives happily
    // alongside vertical-scrolling screens, but the campus map is a
    // freely-draggable-in-any-direction MapLibre view — panning it reads as
    // a horizontal swipe just like the drawer's own open gesture, so the
    // drawer would keep hijacking map drags. Disabled on both campus routes
    // (the drawer's still reachable via the hamburger icon, same as always).
    val drawerGesturesEnabled = currentRoute != "campus" && currentRoute != "campus/fullmap"

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.verticalScroll(rememberScrollState()), drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Crossfade(targetState = vm.fontChoice, animationSpec = tween(180), label = "drawerFont") { choice ->
                            Text(
                                "Punla",
                                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = punlaDisplayFamily(choice))
                            )
                        }
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                (BOTTOM_TABS + DRAWER_ITEMS).forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navigateTo(item.route)
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }
        }
    ) {
      Box(Modifier.fillMaxSize().nestedScroll(interactionScrollConnection)) {
        // Keep the animated procedural surface in its own RenderNode-like layer.
        // Background invalidations can then re-record this layer without forcing
        // the foreground navigation/content tree to redraw on every atmosphere frame.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer()
                .appBackground(
                    vm.backgroundStyle,
                    vm.themePreset,
                    darkTheme = darkTheme,
                    animationEnabled = currentRoute != "campus/fullmap",
                    interactionActive = contentInMotion,
                )
        )
        Row(Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    header = {
                        Spacer(Modifier.height(8.dp))
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Punla home",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                ) {
                    BOTTOM_TABS.forEach { tab ->
                        NavigationRailItem(
                            modifier = Modifier.testTag("nav:${tab.route}"),
                            selected = selectedTabRoute == tab.route,
                            onClick = { navigateTo(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }

            Scaffold(
            modifier = Modifier.weight(1f),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Crossfade(targetState = vm.fontChoice, animationSpec = tween(180), label = "topbarFont") { choice ->
                            Text(
                                currentTitle,
                                modifier = Modifier.testTag("destination-title"),
                                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = punlaDisplayFamily(choice))
                            )
                        }
                    },
                    navigationIcon = {
                        if (showBackArrow) {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Open navigation menu",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    },
                    actions = {
                        if (!onSettings) {
                            IconButton(onClick = { navController.navigate("student-os?tab=1") }) {
                                BadgedBox(badge = { if (inboxCount > 0) Badge { Text(if (inboxCount > 99) "99+" else inboxCount.toString()) } }) {
                                    Icon(Icons.Default.Inbox, contentDescription = "Inbox, $inboxCount to review")
                                }
                            }
                            IconButton(onClick = { quickAddOpen = true }, modifier = Modifier.testTag("quick-capture")) {
                                Icon(Icons.Default.Add, contentDescription = "Quick capture")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        // Keep navigation chrome stable over Punla's animated
                        // atmosphere. Reading surfaces should not inherit moving
                        // contrast from the decorative background.
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            // Keep the four main destinations within reach on secondary screens.
            bottomBar = {
                if (!useNavigationRail && currentRoute !in setOf("campus/fullmap", "study-analysis", "system-health", "pomodoro")) {
                    // UX polish plan (glass + nav sections) — the bar now
                    // floats as a rounded, inset "glass" pill instead of a
                    // flush edge-to-edge strip, reusing the same opaque
                    // tint/edge-highlight/shadow stack already proven out on
                    // the Budget/Dashboard cards (`glassCard`), rather than
                    // pulling in a third-party glass library for it — same
                    // zero-dependency call the plan doc made for cards.
                    // `navigationBarsPadding()` lifts the whole pill clear of
                    // the system gesture bar first; the padding() after that
                    // is the actual floating margin. NavigationBar's own
                    // insets/elevation are zeroed out since the wrapping Box
                    // already owns both.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .glassCard(
                                shape = RoundedCornerShape(28.dp),
                                // Opaque on purpose: the default AMBIENT background
                                // must never bleed through navigation labels/icons.
                                tintAlpha = 1f,
                                elevation = 6.dp
                            )
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            BOTTOM_TABS.forEach { tab ->
                                NavigationBarItem(
                                    modifier = Modifier.testTag("nav:${tab.route}"),
                            selected = selectedTabRoute == tab.route,
                                    onClick = { navigateTo(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "dashboard",
                modifier = Modifier.padding(padding),
                // UX polish plan (motion section) — one shared transition
                // used to cover two different kinds of navigation the same
                // way: switching between the 5 sibling bottom tabs, and a
                // real hierarchical push into a drawer destination (Settings/
                // Checklist/Campus/Focus) or a quick-add form. That's what
                // made it look wrong — a tab switch always slid the new
                // screen in "from the right," even when the tapped tab sat
                // to the *left* of the current one, which reads backwards.
                // It was also asymmetric within itself: enterTransition
                // slid but exitTransition didn't; popEnterTransition didn't
                // slide but popExitTransition did.
                //
                // Fix: tell the two cases apart with isTabSwitch() (defined
                // above, near BOTTOM_TABS).
                // - Sibling tab <-> sibling tab: a short fade only. The earlier full-screen
                //   scale added GPU work to already data-rich destinations and could
                //   make a normal tab press feel heavier than it should.
                // - A real push/pop (drawer destination, quick-add form):
                //   a symmetric slide+fade, forward and back mirroring each
                //   other instead of two unrelated-looking motions.
                enterTransition = {
                    if (isTabSwitch(initialState, targetState)) {
                        // Sibling tabs are heavy, data-rich screens. A short fade avoids
                        // scaling/rasterizing the whole destination during every tab tap.
                        fadeIn(tween(160, easing = FastOutSlowInEasing))
                    } else {
                        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 8 }
                    }
                },
                exitTransition = {
                    if (isTabSwitch(initialState, targetState)) {
                        fadeOut(tween(110))
                    } else {
                        fadeOut(tween(160)) +
                            slideOutHorizontally(tween(160)) { -it / 8 }
                    }
                },
                popEnterTransition = {
                    if (isTabSwitch(initialState, targetState)) {
                        fadeIn(tween(160, easing = FastOutSlowInEasing))
                    } else {
                        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 8 }
                    }
                },
                popExitTransition = {
                    if (isTabSwitch(initialState, targetState)) {
                        fadeOut(tween(110))
                    } else {
                        fadeOut(tween(160)) +
                            slideOutHorizontally(tween(160)) { it / 8 }
                    }
                }
            ) {
                composable("student-os?tab={tab}", arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })) { entry ->
                    com.uplb.punla.ui.screens.StudentOsScreen(vm, entry.arguments?.getInt("tab") ?: 0) { route, course ->
                        val separator = if ('?' in route) "&" else "?"
                        val target = if (course != null && route.substringBefore('?') in listOf("pomodoro", "study", "flashcards", "quizzes"))
                            "${route}${separator}course=${android.net.Uri.encode(course)}" else route
                        navController.navigate(target)
                    }
                }
                composable("more") { MoreScreen { navigateTo(it) } }
                composable("dashboard") {
                    DashboardScreen(
                        vm,
                        onOpenNextClassOnMap = { navController.navigate("campus/fullmap") },
                        onOpenSchedule = { navigateTo("schedule") },
                        onOpenBudget = { navigateTo("budget") },
                        onOpenDeadlines = { navigateTo("deadlines") },
                        onOpenChecklist = { navController.navigate("checklist") },
                        onOpenPlan = { navController.navigate("student-os") },
                        onOpenStudy = { navigateTo("study") },
                        onOpenPomodoro = { course ->
                            if (course != null) {
                                navController.navigate("pomodoro?course=${android.net.Uri.encode(course)}")
                            } else {
                                navController.navigate("pomodoro")
                            }
                        }
                    )
                }
                composable(
                    "schedule?quickAdd={quickAdd}&quickAddToken={quickAddToken}",
                    arguments = listOf(
                        navArgument("quickAdd") { type = NavType.BoolType; defaultValue = false },
                        navArgument("quickAddToken") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backEntry ->
                    ScheduleScreen(
                        vm,
                        openFormOnStart = backEntry.arguments?.getBoolean("quickAdd") ?: false,
                        quickAddToken = backEntry.arguments?.getString("quickAddToken").orEmpty(),
                        onStudyHere = { course ->
                            if (course != null) {
                                navController.navigate("pomodoro?course=${android.net.Uri.encode(course)}")
                            } else {
                                navController.navigate("pomodoro")
                            }
                        }
                    )
                }
                composable(
                    "budget?quickAdd={quickAdd}&quickAddToken={quickAddToken}",
                    arguments = listOf(
                        navArgument("quickAdd") { type = NavType.BoolType; defaultValue = false },
                        navArgument("quickAddToken") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backEntry ->
                    BudgetScreen(
                        vm,
                        openFormOnStart = backEntry.arguments?.getBoolean("quickAdd") ?: false,
                        quickAddToken = backEntry.arguments?.getString("quickAddToken").orEmpty()
                    )
                }
                composable(
                    "deadlines?quickAdd={quickAdd}&quickAddToken={quickAddToken}",
                    arguments = listOf(
                        navArgument("quickAdd") { type = NavType.BoolType; defaultValue = false },
                        navArgument("quickAddToken") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backEntry ->
                    DeadlinesScreen(
                        vm,
                        openFormOnStart = backEntry.arguments?.getBoolean("quickAdd") ?: false,
                        quickAddToken = backEntry.arguments?.getString("quickAddToken").orEmpty()
                    )
                }
                composable(
                    "grades?quickAdd={quickAdd}&quickAddToken={quickAddToken}",
                    arguments = listOf(
                        navArgument("quickAdd") { type = NavType.BoolType; defaultValue = false },
                        navArgument("quickAddToken") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backEntry ->
                    GradesScreen(
                        vm,
                        openFormOnStart = backEntry.arguments?.getBoolean("quickAdd") ?: false,
                        quickAddToken = backEntry.arguments?.getString("quickAddToken").orEmpty()
                    )
                }
                composable("campus") {
                    CampusMapScreen(
                        vm = vm,
                        initialSearch = vm.mapSearchQuery,
                        onOpenFullMap = { navController.navigate("campus/fullmap") }
                    )
                }
                composable("campus/fullmap") {
                    CampusFullMapScreen(vm = vm)
                }
                composable("checklist") { ChecklistScreen(vm) }
                composable(
                    "pomodoro?course={course}",
                    arguments = listOf(navArgument("course") { type = NavType.StringType; nullable = true; defaultValue = null })
                ) { backEntry ->
                    PomodoroScreen(
                        vm,
                        preselectedCourse = backEntry.arguments?.getString("course"),
                        onOpenAnalysis = { navController.navigate("study-analysis") }
                    )
                }
                composable("study-analysis") { StudyAnalysisScreen(vm) }
                composable("study?course={course}&section={section}", arguments = listOf(
                    navArgument("course") { type = NavType.StringType; defaultValue = "" },
                    navArgument("section") { type = NavType.StringType; defaultValue = "Overview" }
                )) { entry ->
                    StudyHubScreen(
                        vm = vm,
                        initialCourse = entry.arguments?.getString("course")?.takeIf { it.isNotBlank() },
                        initialSection = entry.arguments?.getString("section"),
                        onOpenPulse = { navController.navigate("student-os?tab=2") },
                        onOpenFlashcards = { course, topicId, overall ->
                            val c = course?.let { android.net.Uri.encode(it) }.orEmpty()
                            val t = topicId?.let { android.net.Uri.encode(it) }.orEmpty()
                            navController.navigate("flashcards?course=$c&topicId=$t&overall=$overall")
                        },
                        onOpenQuizzes = { course, topicId, overall ->
                            val c = course?.let { android.net.Uri.encode(it) }.orEmpty()
                            val t = topicId?.let { android.net.Uri.encode(it) }.orEmpty()
                            navController.navigate("quizzes?course=$c&topicId=$t&overall=$overall")
                        },
                        onOpenFocus = { course ->
                            if (course != null) navController.navigate("pomodoro?course=${android.net.Uri.encode(course)}")
                            else navController.navigate("pomodoro")
                        }
                    )
                }
                composable(
                    "flashcards?course={course}&topicId={topicId}&overall={overall}",
                    arguments = listOf(
                        navArgument("course") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("topicId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("overall") { type = NavType.BoolType; defaultValue = false }
                    )
                ) { backEntry ->
                    FlashcardsScreen(
                        vm,
                        initialCourse = backEntry.arguments?.getString("course")?.takeIf { it.isNotBlank() },
                        initialTopicId = backEntry.arguments?.getString("topicId")?.takeIf { it.isNotBlank() },
                        overallOnly = backEntry.arguments?.getBoolean("overall") ?: false
                    )
                }
                composable(
                    "quizzes?course={course}&topicId={topicId}&overall={overall}",
                    arguments = listOf(
                        navArgument("course") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("topicId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("overall") { type = NavType.BoolType; defaultValue = false }
                    )
                ) { backEntry ->
                    QuizScreen(
                        vm,
                        initialCourse = backEntry.arguments?.getString("course")?.takeIf { it.isNotBlank() },
                        initialTopicId = backEntry.arguments?.getString("topicId")?.takeIf { it.isNotBlank() },
                        overallOnly = backEntry.arguments?.getBoolean("overall") ?: false
                    )
                }
                composable("assistant") {
                    AssistantScreen(
                        vm = vm,
                        onOpenPlanningAssistant = { navController.navigate("student-os?tab=5") },
                        onOpenPomodoro = { course ->
                            if (course != null) navController.navigate("pomodoro?course=${android.net.Uri.encode(course)}")
                            else navController.navigate("pomodoro")
                        }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        vm = vm,
                        notificationPermissionGranted = notificationPermissionGranted,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onOpenSystemHealth = { navController.navigate("system-health") }
                    )
                }
                composable("system-health") {
                    SystemHealthScreen()
                }
            }
        }
        }

        if (quickAddOpen) QuickCaptureSheet(
            text = captureText, onTextChange = { captureText = it; captureError = null },
            saving = captureSaving, error = captureError, courses = osSnapshot.context.courses.map { it.code },
            onDismiss = { quickAddOpen = false }, onStructuredAdd = { quickAddTo(it) },
            onSave = {
                captureSaving = true
                vm.studentOs.capture(captureText) { error ->
                    captureSaving = false
                    captureError = error
                    if (error == null) {
                        captureText = ""
                        quickAddOpen = false
                        scope.launch {
                            if (snackbar.showSnackbar("Saved to Inbox", actionLabel = "Review") == SnackbarResult.ActionPerformed)
                                navController.navigate("student-os?tab=1")
                        }
                    }
                }
            }
        )

      }
    }
}

private fun nextThemeMode(current: ThemeMode): ThemeMode = when (current) {
    ThemeMode.SYSTEM -> ThemeMode.LIGHT
    ThemeMode.LIGHT -> ThemeMode.DARK
    ThemeMode.DARK -> ThemeMode.SYSTEM
}

private fun themeModeIcon(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
    ThemeMode.LIGHT -> Icons.Default.LightMode
    ThemeMode.DARK -> Icons.Default.DarkMode
}

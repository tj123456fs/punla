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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.uplb.punla.ui.screens.StudyAnalysisScreen
import com.uplb.punla.ui.screens.AssistantScreen
import com.uplb.punla.ui.screens.FlashcardsScreen
import com.uplb.punla.ui.screens.QuizScreen
import com.uplb.punla.ui.screens.StudyHubScreen
import com.uplb.punla.ui.theme.appBackground
import com.uplb.punla.ui.theme.PunlaDisplay
import com.uplb.punla.ui.theme.PunlaMono
import com.uplb.punla.ui.theme.PunlaTheme
import com.uplb.punla.worker.BackupNudgeWorker
import com.uplb.punla.worker.ClassReminderWorker
import com.uplb.punla.worker.StudyNudgeWorker
import com.uplb.punla.worker.ClassDayNotificationScheduler
import com.uplb.punla.worker.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
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

    private fun recordNotificationOpen(intent: Intent?) {
        if (intent?.getStringExtra(TrackedNotification.EXTRA_OUTCOME) != "OPENED") return
        val key = intent.getStringExtra(TrackedNotification.EXTRA_KEY) ?: return
        val worker = intent.getStringExtra(TrackedNotification.EXTRA_WORKER) ?: "unknown"
        val type = intent.getStringExtra(TrackedNotification.EXTRA_TYPE) ?: "general"
        // Remove the marker immediately so configuration changes and repeated
        // lifecycle callbacks do not duplicate the same OPENED event.
        intent.removeExtra(TrackedNotification.EXTRA_OUTCOME)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                PunlaRepository(applicationContext).logNotificationEvent(
                    NotificationEvent(
                        notificationKey = key,
                        workerName = worker,
                        notificationType = type,
                        localHour = java.time.LocalDateTime.now().hour,
                        outcome = "OPENED"
                    )
                )
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
        startRouteState.value = intent.getStringExtra(EXTRA_START_ROUTE)
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
        startRouteState.value = intent?.getStringExtra(EXTRA_START_ROUTE)
        intent?.getStringExtra(EXTRA_MAP_QUERY)?.let(vm::searchOnMap)
        notificationPermissionGrantedState.value = hasNotificationPermission()
        
        // Daily deadline, budget, and checklist checks share one
        // locally learned delivery hour when enough interaction history exists.
        ReminderScheduler.scheduleDaily(this)

        // Class-start reminders need a much tighter cadence than the daily
        // deadline check — 15 minutes is WorkManager's minimum periodic
        // interval, which conveniently matches the reminder window.
        val classReminderRequest = PeriodicWorkRequestBuilder<ClassReminderWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "class_reminder_work",
            ExistingPeriodicWorkPolicy.KEEP,
            classReminderRequest
        )

        // Contextual study cues (before/after class + one evening queue summary).
        val studyNudgeRequest = PeriodicWorkRequestBuilder<StudyNudgeWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "study_nudge_work", ExistingPeriodicWorkPolicy.KEEP, studyNudgeRequest
        )

        // One low-priority card evolves from "leave soon" to current class,
        // free time, and end-of-day. A system chronometer renders the live
        // countdown without waking Punla every minute.
        if (vm.notificationsEnabled && vm.classDayNotificationEnabled) {
            ClassDayNotificationScheduler.ensureScheduled(this)
        } else {
            ClassDayNotificationScheduler.cancel(this)
        }

        // Roadmap #6 — weekly check for whether it's time to nudge a backup
        // (the worker itself decides whether a nudge is actually due).
        val backupNudgeRequest = PeriodicWorkRequestBuilder<BackupNudgeWorker>(7, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "backup_nudge_work",
            ExistingPeriodicWorkPolicy.KEEP,
            backupNudgeRequest
        )

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
            val notificationPermissionGranted by notificationPermissionGrantedState
            val inPictureInPicture by pipModeState
            val pomodoroRunning = vm.pomodoroState.isRunning
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

// UX polish plan (nav section) — hybrid drawer/bottom-bar switch. Bottom
// bar caps at 5 items before losing label space, so the 6 former "TABS"
// split: the 5 daily-use destinations move to a bottom bar, Campus (visited
// far less often — once per class-hunt, not multiple times a day) moves to
// the drawer. Campus still has a one-tap path off the Dashboard's existing
// "next class on map" shortcut card, so it isn't losing quick access, just
// losing bottom-bar-level prominence.
private val BOTTOM_TABS = listOf(
    Tab("dashboard", "Home", Icons.Default.Home),
    Tab("schedule", "Schedule", Icons.Default.CalendarMonth),
    Tab("deadlines", "Deadlines", Icons.Default.Flag),
    Tab("budget", "Budget", Icons.Default.AttachMoney),
    Tab("grades", "Grades", Icons.Default.Grade)
)

// Drawer now holds only lower-frequency destinations: Campus (moved out of
// the bottom bar above), Checklist and Settings (inherently low-frequency),
// and Focus (already has a Dashboard shortcut card, so it doesn't need
// bottom-bar-level prominence either).
private val DRAWER_ITEMS = listOf(
    Tab("campus", "Campus", Icons.Default.Map),
    Tab("checklist", "Before Classes Start", Icons.Default.Checklist),
    Tab("pomodoro", "Focus", Icons.Default.Timer),
    Tab("study", "Study Hub", Icons.Default.School),
    Tab("flashcards", "Flashcards", Icons.Default.Style),
    Tab("quizzes", "Quizzes", Icons.Default.Help),
    Tab("assistant", "Assistant", Icons.Default.SmartToy),
    Tab("settings", "Settings", Icons.Default.Settings)
)

// Every reachable top-level destination, bottom bar + drawer combined —
// used to validate widget/worker deep-link start routes (see
// EXTRA_START_ROUTE) without hardcoding the route list twice.
private val ALL_DESTINATIONS = BOTTOM_TABS + DRAWER_ITEMS

// Routes with their own dedicated FAB already (Budget/Deadlines/Grades each
// have an in-screen "+" for adding). The global quick-add speed dial is
// hidden on those to avoid two FABs stacking in the same corner. Pomodoro
// has no add-form of its own — its FAB slot doesn't apply, so it's hidden
// here too rather than showing an unrelated speed dial over the timer.
private val ROUTES_WITH_OWN_FAB = setOf("budget", "deadlines", "grades", "checklist", "campus/fullmap", "pomodoro", "study", "flashcards", "quizzes", "assistant")

private data class QuickAddAction(
    val kind: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)

private val QUICK_ADD_ACTIONS = listOf(
    QuickAddAction("class", "Add class", Icons.Default.CalendarMonth, "schedule"),
    QuickAddAction("expense", "Add expense", Icons.Default.AttachMoney, "budget"),
    QuickAddAction("deadline", "Add deadline", Icons.Default.Flag, "deadlines"),
    QuickAddAction("grade", "Add grade", Icons.Default.Grade, "grades")
)

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
    darkTheme: Boolean = false,
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {}
) {
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry.baseRoute()
    val currentTitle = BOTTOM_TABS.firstOrNull { it.route == currentRoute }?.label
        ?: when (currentRoute) {
            "settings" -> "Settings"
            "checklist" -> "Checklist"
            "campus" -> "Campus"
            "campus/fullmap" -> "Campus Map"
            "pomodoro" -> "Focus"
            "study-analysis" -> "Study Analysis"
            "study" -> "Study Hub"
            "flashcards" -> "Flashcards"
            "quizzes" -> "Quizzes"
            "assistant" -> "Assistant"
            else -> "Punla"
        }
    val onSettings = currentRoute == "settings"
    // Drawer-only destinations (reachable via the drawer, not a bottom tab)
    // get a Back arrow instead of the hamburger menu, same as Settings —
    // there's no bottom-tab "home" to return to via the drawer itself.
    // The full campus map is reached by drilling in from the Campus screen
    // rather than the drawer, but the same logic applies: no bottom-tab
    // "home" to swipe back to, so it needs an explicit Back arrow too.
    // Study Analysis is a drill-down from Pomodoro, same story.
    // Drawer destinations are top-level destinations and keep the hamburger menu.
    // Only true drill-down screens use a Back arrow; this keeps Quizzes/Study visible
    // from Flashcards instead of trapping the user behind a back-only top bar.
    val showBackArrow = currentRoute == "campus/fullmap" || currentRoute == "study-analysis"

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }
    val useNavigationRail = LocalConfiguration.current.screenWidthDp >= 600

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
        navController.navigate("$route?quickAdd=true") {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
        }
    }

    // One-shot jump to whatever tab a widget tap asked for (e.g. tapping the
    // Budget widget's body opens straight to "budget" instead of dashboard).
    LaunchedEffect(startRoute) {
        if (startRoute != null && ALL_DESTINATIONS.any { it.route == startRoute }) {
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
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Punla",
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = PunlaDisplay)
                        )
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                DRAWER_ITEMS.forEach { item ->
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
      Box(Modifier.fillMaxSize().appBackground(vm.backgroundStyle, vm.themePreset, darkTheme = darkTheme)) {
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
                            selected = currentRoute == tab.route,
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
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            currentTitle,
                            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = PunlaDisplay)
                        )
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
                            IconButton(onClick = { navigateTo("settings") }) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            IconButton(onClick = { vm.updateThemeMode(nextThemeMode(vm.themeMode)) }) {
                                Icon(
                                    themeModeIcon(vm.themeMode),
                                    contentDescription = "Theme: ${vm.themeMode.name.lowercase()} (tap to change)",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            // UX polish plan (nav section) — hybrid drawer/bottom-bar switch.
            // Only shown on the 5 daily-use destinations; drawer-only screens
            // (Campus, Checklist, Focus, Settings, and their drill-downs)
            // keep the existing Back-arrow top bar with no bottom bar at all,
            // same as before this change.
            bottomBar = {
                if (!useNavigationRail && BOTTOM_TABS.any { it.route == currentRoute }) {
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
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .glassCard(
                                shape = RoundedCornerShape(28.dp),
                                tintAlpha = 0.9f,
                                elevation = 8.dp
                            )
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            BOTTOM_TABS.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
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
            // Quick-add speed dial moved into Scaffold's own FAB slot instead
            // of a manually-aligned overlay Box — Scaffold offsets this above
            // the new bottom bar automatically, so it doesn't need its own
            // bottom-bar-aware padding math.
            floatingActionButton = {
                if (currentRoute !in ROUTES_WITH_OWN_FAB) {
                    QuickAddFab(
                        open = quickAddOpen,
                        onToggle = { quickAddOpen = !quickAddOpen },
                        onAction = { action -> quickAddTo(action.route) }
                    )
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
                // - Sibling tab <-> sibling tab: Material's "fade through" —
                //   outgoing fades out in place, incoming fades/scales in.
                //   No slide, so there's no direction to get wrong.
                // - A real push/pop (drawer destination, quick-add form):
                //   a symmetric slide+fade, forward and back mirroring each
                //   other instead of two unrelated-looking motions.
                enterTransition = {
                    if (isTabSwitch(initialState, targetState)) {
                        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.98f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                    } else {
                        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 8 }
                    }
                },
                exitTransition = {
                    if (isTabSwitch(initialState, targetState)) {
                        fadeOut(tween(160))
                    } else {
                        fadeOut(tween(160)) +
                            slideOutHorizontally(tween(160)) { -it / 8 }
                    }
                },
                popEnterTransition = {
                    if (isTabSwitch(initialState, targetState)) {
                        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.98f, animationSpec = tween(220, easing = FastOutSlowInEasing))
                    } else {
                        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 8 }
                    }
                },
                popExitTransition = {
                    if (isTabSwitch(initialState, targetState)) {
                        fadeOut(tween(160))
                    } else {
                        fadeOut(tween(160)) +
                            slideOutHorizontally(tween(160)) { it / 8 }
                    }
                }
            ) {
                composable("dashboard") {
                    DashboardScreen(
                        vm,
                        onOpenNextClassOnMap = { navController.navigate("campus/fullmap") },
                        onOpenSchedule = { navigateTo("schedule") },
                        onOpenBudget = { navigateTo("budget") },
                        onOpenDeadlines = { navigateTo("deadlines") },
                        onOpenChecklist = { navController.navigate("checklist") },
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
                    "schedule?quickAdd={quickAdd}",
                    arguments = listOf(navArgument("quickAdd") { type = NavType.BoolType; defaultValue = false })
                ) { backEntry ->
                    ScheduleScreen(
                        vm,
                        openFormOnStart = backEntry.arguments?.getBoolean("quickAdd") ?: false,
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
                    "budget?quickAdd={quickAdd}",
                    arguments = listOf(navArgument("quickAdd") { type = NavType.BoolType; defaultValue = false })
                ) { backEntry ->
                    BudgetScreen(vm, openFormOnStart = backEntry.arguments?.getBoolean("quickAdd") ?: false)
                }
                composable(
                    "deadlines?quickAdd={quickAdd}",
                    arguments = listOf(navArgument("quickAdd") { type = NavType.BoolType; defaultValue = false })
                ) { backEntry ->
                    DeadlinesScreen(vm, openFormOnStart = backEntry.arguments?.getBoolean("quickAdd") ?: false)
                }
                composable(
                    "grades?quickAdd={quickAdd}",
                    arguments = listOf(navArgument("quickAdd") { type = NavType.BoolType; defaultValue = false })
                ) { backEntry ->
                    GradesScreen(vm, openFormOnStart = backEntry.arguments?.getBoolean("quickAdd") ?: false)
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
                composable("study") {
                    StudyHubScreen(
                        vm = vm,
                        onOpenFlashcards = { navigateTo("flashcards") },
                        onOpenQuizzes = { navigateTo("quizzes") },
                        onOpenFocus = { course ->
                            if (course != null) navController.navigate("pomodoro?course=${android.net.Uri.encode(course)}")
                            else navController.navigate("pomodoro")
                        }
                    )
                }
                composable("flashcards") { FlashcardsScreen(vm) }
                composable("quizzes") { QuizScreen(vm) }
                composable("assistant") {
                    AssistantScreen(
                        vm = vm,
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
                        onRequestNotificationPermission = onRequestNotificationPermission
                    )
                }
            }
        }
        }

        // Scrim behind the open quick-add speed dial — tapping anywhere
        // outside the action bubbles dismisses the menu.
        AnimatedVisibility(
            visible = quickAddOpen,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { quickAddOpen = false }
            )
        }

      }
    }
}

/**
 * Bottom-right speed-dial FAB: tapping the main "+" fans out one small
 * labeled FAB per quick-add destination (mirrors the web app's arc of
 * quick-add bubbles), each of which jumps straight to that tab's add form.
 */
@Composable
private fun QuickAddFab(
    open: Boolean,
    onToggle: () -> Unit,
    onAction: (QuickAddAction) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    // The lone "+" glyph doubles as a close affordance: rotating it 45°
    // reads as an "×" without needing a second icon asset or swap-flicker.
    val fabRotation by animateFloatAsState(
        targetValue = if (open) 45f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "quickAddFabRotation"
    )

    Column(horizontalAlignment = Alignment.End) {
        QUICK_ADD_ACTIONS.asReversed().forEachIndexed { index, action ->
            // Item nearest the FAB opens first; each subsequent bubble is
            // staggered slightly behind it for a cascading fan-out.
            val stagger = index * 40
            AnimatedVisibility(
                visible = open,
                enter = fadeIn(tween(180, delayMillis = stagger)) +
                    slideInVertically(tween(220, delayMillis = stagger), initialOffsetY = { it / 2 }) +
                    scaleIn(tween(220, delayMillis = stagger), initialScale = 0.6f),
                exit = fadeOut(tween(120)) +
                    slideOutVertically(tween(150), targetOffsetY = { it / 2 }) +
                    scaleOut(tween(150), targetScale = 0.6f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 14.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.small,
                        shadowElevation = 3.dp,
                        tonalElevation = 2.dp,
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Text(
                            action.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = PunlaMono, fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAction(action)
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 3.dp,
                            pressedElevation = 5.dp
                        )
                    ) {
                        Icon(action.icon, contentDescription = action.label)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 8.dp
            )
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = if (open) "Close quick add" else "Quick add",
                modifier = Modifier.graphicsLayer { rotationZ = fabRotation }
            )
        }
    }
}

/** System -> Light -> Dark -> System, tapped via the top bar icon. */
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

package com.uplb.punla.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uplb.punla.data.CampusDirectory
import com.uplb.punla.data.entity.AttendanceStatus
import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.data.entity.allowedAbsences
import com.uplb.punla.data.LocationFailure
import com.uplb.punla.data.WalkingRoute
import com.uplb.punla.data.fetchOneShotLocation
import com.uplb.punla.data.fetchWalkingRoute
import com.uplb.punla.data.fmtDistance
import com.uplb.punla.data.hasLocationPermission
import com.uplb.punla.data.hasFineLocationPermission
import com.uplb.punla.data.haversineMeters
import com.uplb.punla.data.openAppLocationSettings
import com.uplb.punla.data.shouldShowLocationRationale
import com.uplb.punla.data.walkingEtaMinutes
import kotlin.math.roundToInt
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ui.theme.PunlaDisplay
import com.uplb.punla.ui.theme.PunlaMono
import com.uplb.punla.ui.theme.LocalPunlaPalette
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun DashboardScreen(
    vm: PunlaViewModel,
    onOpenNextClassOnMap: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    onOpenBudget: () -> Unit = {},
    onOpenDeadlines: () -> Unit = {},
    onOpenChecklist: () -> Unit = {},
    onOpenPomodoro: (String?) -> Unit = {}
) {
    val classes by vm.classes.collectAsState()
    val deadlines by vm.deadlines.collectAsState()
    val attendanceRecords by vm.attendanceRecords.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val expenseRules by vm.expenseRules.collectAsState()
    val checklistItems by vm.checklistItems.collectAsState()
    val studySessions by vm.studySessions.collectAsState()
    val studyStreak by vm.currentStudyStreak.collectAsState()
    // Roadmap C — gates "No classes scheduled" / "Nothing due" / weekly
    // empty state so they don't flash for one frame before Room's first
    // real emission lands on a cold launch.
    val dataReady by vm.isDataReady.collectAsState()

    val nextClass by vm.nextClassFlow.collectAsState(initial = null)
    val nextDeadline by vm.nextDeadlineFlow.collectAsState(initial = null)
    val haptics = LocalHapticFeedback.current
    val screenGutter = punlaScreenHorizontalPadding()

    // ---- GPS \u2194 next class correlation ----
    val context = LocalContext.current
    var userLoc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locating by remember { mutableStateOf(false) }
    var locateFailure by remember { mutableStateOf<LocationFailure?>(null) }
    val nextClassBuilding = remember(nextClass) {
        nextClass?.let { CampusDirectory.findBuildingForRoom(it.room) }
    }

    // Real walking route to the next class, upgrading the instant
    // straight-line estimate below once it resolves — same throttled
    // approach as CampusFullMapScreen.kt (refetch only after ~20m of
    // movement or a destination change, not on every location update; keep
    // the last good route on a failed fetch rather than reverting to a
    // straight line over one transient network hiccup).
    var nextClassRoute by remember(nextClassBuilding) { mutableStateOf<WalkingRoute?>(null) }
    var lastRouteFetchLoc by remember(nextClassBuilding) { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(userLoc, nextClassBuilding) {
        val loc = userLoc
        val dest = nextClassBuilding
        if (loc == null || dest == null) {
            nextClassRoute = null
            lastRouteFetchLoc = null
            return@LaunchedEffect
        }
        val movedFarEnough = lastRouteFetchLoc?.let {
            haversineMeters(it.first, it.second, loc.first, loc.second) > 20.0
        } ?: true
        if (movedFarEnough) {
            lastRouteFetchLoc = loc
            fetchWalkingRoute(loc, dest.lat to dest.lon)?.let { nextClassRoute = it }
        }
    }

    fun requestNextClassFix() {
        locating = true
        locateFailure = null
        fetchOneShotLocation(
            context,
            onResult = { lat, lon, _ -> userLoc = lat to lon; locating = false },
            onError = { reason -> locating = false; locateFailure = reason }
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it } || hasLocationPermission(context)) requestNextClassFix() else {
            locating = false
            locateFailure = LocationFailure.PERMISSION_DENIED
        }
    }

    var viewMode by remember { mutableStateOf(0) } // 0 = Today, 1 = This Week

    val budget = vm.monthlyBudget
    val now = LocalDate.now()
    val spent = expenses.filter {
        val d = runCatching { LocalDate.parse(it.date) }.getOrNull()
        d != null && d.year == now.year && d.monthValue == now.monthValue
    }.sumOf { it.amount }
    val remaining = budget - spent
    val progress = if (budget > 0) (spent / budget).coerceIn(0.0, 1.0).toFloat() else 0f

    // Roadmap - Dashboard Redesign #3: budget pace, lifted from BudgetScreen's
    // proven math so the dashboard can tease "trending right or wrong" without
    // duplicating a full pace card.
    val daysInMonth = java.time.YearMonth.now().lengthOfMonth()
    val dailyAvg = if (now.dayOfMonth > 0) spent / now.dayOfMonth else 0.0
    val budgetDailyLimit = if (daysInMonth > 0) budget / daysInMonth else 0.0
    val isOverPace = dailyAvg > budgetDailyLimit
    val monthEnd = java.time.YearMonth.from(now).atEndOfMonth()
    val upcomingFixedCommitments = remember(expenseRules, now) {
        vm.repo.projectedFixedCommitmentsFromRules(expenseRules, now, monthEnd)
    }
    val budgetDaysRemaining = (ChronoUnit.DAYS.between(now, monthEnd) + 1).coerceAtLeast(1)
    val safeToday = if (budget > 0.0) (remaining - upcomingFixedCommitments) / budgetDaysRemaining else 0.0

    // Roadmap - Dashboard Redesign #1: quick-glance stat row derived counts.
    // All sourced from state already collected above - no new queries.
    val dayAbbrevMap = remember {
        mapOf(
            java.time.DayOfWeek.MONDAY to "Mon", java.time.DayOfWeek.TUESDAY to "Tue",
            java.time.DayOfWeek.WEDNESDAY to "Wed", java.time.DayOfWeek.THURSDAY to "Thu",
            java.time.DayOfWeek.FRIDAY to "Fri", java.time.DayOfWeek.SATURDAY to "Sat",
            java.time.DayOfWeek.SUNDAY to "Sun"
        )
    }
    val classesToday = remember(classes) {
        val todayAbbrev = dayAbbrevMap[LocalDate.now().dayOfWeek]
        classes.filter { it.day == todayAbbrev }
    }
    val deadlinesThisWeek = remember(deadlines) {
        deadlines.filter {
            !it.done && runCatching {
                val days = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it.due))
                days in 0..7
            }.getOrDefault(false)
        }
    }
    val nearLimitClasses = remember(classes) {
        classes.count { it.absences >= it.allowedAbsences() - 1 }
    }

    // Free-Time Study Suggestions (Phase 1) — a genuine free-slot + upcoming
    // deadline match for today (or tomorrow). Dismissing it for the day is
    // tracked via a SharedPreferences timestamp, same pattern as the backup
    // nudge, so it doesn't reappear every time the Dashboard is opened.
    val studySuggestion = remember(classes, deadlines, studySessions, vm.pomodoroWorkMinutes, studyStreak) {
        com.uplb.punla.ui.pomodoro.suggestStudySlotTodayOrTomorrow(
            classes = classes,
            deadlines = deadlines,
            pomodoroWorkMinutes = vm.pomodoroWorkMinutes,
            sessions = studySessions,
            modelState = vm.repo.studySlotModelState,
            currentStreak = studyStreak
        )
    }
    val suggestionDismissedToday = remember(vm.studySuggestionDismissedAt) {
        val dismissedAt = vm.studySuggestionDismissedAt
        dismissedAt != null && java.time.Instant.ofEpochMilli(dismissedAt)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
    }
    val activeSuggestion = studySuggestion.takeIf { !suggestionDismissedToday }
    LaunchedEffect(activeSuggestion?.id) {
        activeSuggestion?.let(vm::recordStudySuggestionShown)
    }

    // "Before Classes Start" card inputs.
    val daysUntilClasses = remember { vm.repo.daysUntilClassesStart() }
    val checklistChecked = checklistItems.count { it.checked }
    val checklistAllDone = checklistItems.isNotEmpty() && checklistChecked == checklistItems.size

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(horizontal = screenGutter, vertical = 12.dp)
    ) {
        // Welcome Header & Greeting
        item {
            val greeting = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
                in 0..11 -> "Good morning"
                in 12..17 -> "Good afternoon"
                else -> "Good evening"
            }
            val displayGreeting = if (vm.userName.isNotBlank()) {
                "$greeting, ${vm.userName}! 🌱"
            } else {
                "Planting season has begun. 🌱"
            }
            val today = remember { LocalDate.now() }
            val dateLabel = remember(today) {
                today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
            }
            // Greeting card — merges what used to be a bare headline sitting
            // above the quote card into one card, so the whole "welcome
            // back" moment reads as a single unit on open.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        dateLabel.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        displayGreeting,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = PunlaDisplay),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "\"${vm.getQuoteOfTheDay()}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Roadmap Pomodoro 1.6 — compact entry point into the focus timer,
        // same Card/shadow/border treatment as the other dashboard cards.
        // Extended by Study Habits 2.4 with a thin, optional habit strip
        // underneath — today's progress toward the daily goal plus the
        // current streak. Purely informational: a missed streak just reads
        // as "0", never a nag.
        item {
            val todayMinutes by vm.todayStudyMinutes.collectAsState()
            val streak = studyStreak
            val dailyGoal = vm.dailyStudyGoalMinutes
            val goalProgress = if (dailyGoal > 0) (todayMinutes.toFloat() / dailyGoal).coerceIn(0f, 1f) else 0f

            // UX polish plan (glass section) — second proof-of-concept card
            // for the shared opaque glass treatment, alongside Budget's
            // SpendingInsightsCard.
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clickable {
                        activeSuggestion?.let(vm::acceptStudySuggestion)
                        onOpenPomodoro(activeSuggestion?.course)
                    },
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = LocalPunlaPalette.current.leaf)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        if (activeSuggestion != null) {
                            val s = activeSuggestion
                            Text(
                                "You have a free slot ${s.dayLabel}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                "${fmtTime(s.slotStart)}\u2013${fmtTime(s.slotEnd)} \u00b7 start a focus session for \u201c${s.deadline.title}\u201d? (due ${s.deadline.due})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "Start a focus session",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                "Run a Pomodoro timer, tagged to a class if you like.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (activeSuggestion != null) {
                        IconButton(onClick = { vm.dismissStudySuggestion(activeSuggestion) }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss suggestion",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                if (todayMinutes > 0 || streak > 0) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { goalProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = LocalPunlaPalette.current.leaf,
                        trackColor = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "$todayMinutes / $dailyGoal min today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (streak > 0) {
                            Text(
                                "\uD83D\uDD25 $streak-day streak",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Quick-glance stat row
        item {
            DashboardStatsRow(
                classesToday = classesToday.size,
                deadlinesThisWeek = deadlinesThisWeek.size,
                nearLimitClasses = nearLimitClasses
            )
            Spacer(Modifier.height(10.dp))
        }

        // "Before Classes Start" card — only surfaces while there's still
        // something to prepare for: hidden once classes have started, and
        // once everything on the checklist is done.
        if (daysUntilClasses >= 0 && !checklistAllDone) {
            item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f))
                            .clickable { onOpenChecklist() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (checklistItems.isEmpty()) "Before classes start"
                                    else "$checklistChecked/${checklistItems.size} requirements done",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    when {
                                        daysUntilClasses == 0L -> "Classes start today"
                                        daysUntilClasses == 1L -> "1 day until classes start"
                                        else -> "$daysUntilClasses days until classes start"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

        // View Mode Switcher
        item {
            SegmentedControl(
                options = listOf("Today", "This Week"),
                selected = viewMode,
                onSelect = { viewMode = it },
                modifier = Modifier.padding(bottom = 14.dp)
            )
        }

        if (viewMode == 0) {
            // TODAY VIEW

            // Next Class Card
            item {
                SectionLabel("Next class", icon = Icons.Default.CalendarMonth, actionLabel = "Schedule", onAction = onOpenSchedule)
                val isLab = nextClass?.type == "lab"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLab) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        val accentColor = if (nextClass?.type == "lab") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        AccentBar(accentColor)
                        Column(Modifier.padding(14.dp).fillMaxWidth()) {
                            if (nextClass == null) {
                                if (dataReady) {
                                    EmptyState(
                                        icon = Icons.Default.CalendarMonth,
                                        message = "No classes scheduled"
                                    )
                                }
                            } else {
                                val c = nextClass!!
                                val sh = runCatching { LocalTime.parse(c.start, DateTimeFormatter.ofPattern("HH:mm")).format(DateTimeFormatter.ofPattern("h:mm a")) }.getOrDefault(c.start)
                                val eh = runCatching { LocalTime.parse(c.end, DateTimeFormatter.ofPattern("HH:mm")).format(DateTimeFormatter.ofPattern("h:mm a")) }.getOrDefault(c.end)
                                Text(
                                    c.code,
                                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = PunlaDisplay)
                                )
                                Text(
                                    "$sh – $eh · ${c.room ?: "Room TBA"}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = PunlaMono),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (nextClassBuilding != null) {
                                    Spacer(Modifier.height(8.dp))
                                    val loc = userLoc
                                    if (loc != null) {
                                        val meters = nextClassRoute?.distanceMeters
                                            ?: haversineMeters(loc.first, loc.second, nextClassBuilding.lat, nextClassBuilding.lon)
                                        val etaMinutes = nextClassRoute?.let { (it.durationSeconds / 60.0).roundToInt() }
                                            ?: walkingEtaMinutes(meters)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.NearMe,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "${fmtDistance(meters)} · ~$etaMinutes min walk to ${nextClassBuilding.name}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        val permanentlyDenied = locateFailure == LocationFailure.PERMISSION_DENIED &&
                                            !shouldShowLocationRationale(context)
                                        TextButton(
                                            onClick = {
                                                when {
                                                    hasLocationPermission(context) -> requestNextClassFix()
                                                    permanentlyDenied -> openAppLocationSettings(context)
                                                    else -> locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                when {
                                                    locating -> "Locating…"
                                                    permanentlyDenied -> "Enable location in Settings"
                                                    locateFailure == LocationFailure.TIMEOUT -> "No GPS fix — tap to retry"
                                                    locateFailure == LocationFailure.NO_FIX -> "Couldn't get location — tap to retry"
                                                    else -> "How far is it?"
                                                },
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    if (userLoc != null && !hasFineLocationPermission(context)) {
                                        TextButton(
                                            onClick = { locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Enable precise location", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    TextButton(
                                        onClick = onOpenNextClassOnMap,
                                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("View on map", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                // Quick per-occurrence attendance logging. Only show
                                // this for a class occurring today; future weekly rows
                                // should not be marked early by accident.
                                val todayDay = when (LocalDate.now().dayOfWeek) {
                                    java.time.DayOfWeek.MONDAY -> "Mon"
                                    java.time.DayOfWeek.TUESDAY -> "Tue"
                                    java.time.DayOfWeek.WEDNESDAY -> "Wed"
                                    java.time.DayOfWeek.THURSDAY -> "Thu"
                                    java.time.DayOfWeek.FRIDAY -> "Fri"
                                    java.time.DayOfWeek.SATURDAY -> "Sat"
                                    java.time.DayOfWeek.SUNDAY -> "Sun"
                                }
                                if (c.day == todayDay) {
                                    val todayRecord = attendanceRecords.firstOrNull {
                                        it.sessionId == c.id &&
                                            it.occurrenceDate == LocalDate.now().toString() &&
                                            it.scheduledStart == c.start
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Attendance · ${c.absences}/${c.allowedAbsences()} absences used",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                vm.logAttendance(c, AttendanceStatus.ATTENDED, source = "dashboard")
                                            },
                                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (todayRecord?.status == AttendanceStatus.ATTENDED) "Attended ✓" else "Attended",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                vm.logAttendance(c, AttendanceStatus.ABSENT, source = "dashboard")
                                            },
                                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (todayRecord?.status == AttendanceStatus.ABSENT) "Absent ✓" else "Absent",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Budget Remaining Card
            item {
                SectionLabel("Budget", icon = Icons.Default.AttachMoney, actionLabel = "Open", onAction = onOpenBudget)
                val overBudget = remaining < 0
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (overBudget) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        val accentColor = if (overBudget) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        AccentBar(accentColor)
                        Column(Modifier.padding(14.dp).fillMaxWidth()) {
                            if (budget <= 0.0) {
                                EmptyState(
                                    icon = Icons.Default.AttachMoney,
                                    message = "No budget set"
                                )
                            } else {
                                PesoText(
                                    remaining,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                                    color = if (overBudget) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = if (overBudget) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outline,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Spent ₱${spent.toInt()} of ₱${budget.toInt()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (safeToday >= 0.0) "Safe today: ₱${"%,.0f".format(safeToday)}"
                                    else "Safe today: pause discretionary spending",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (safeToday < 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                if (upcomingFixedCommitments > 0.0) {
                                    Text(
                                        "₱${"%,.0f".format(upcomingFixedCommitments)} reserved for upcoming fixed bills",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Pace: ₱${"%,.0f".format(dailyAvg)}/day (₱${"%,.0f".format(budgetDailyLimit)}/day budgeted)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isOverPace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Next Deadline Card
            item {
                SectionLabel("Next deadline", icon = Icons.Default.Flag, actionLabel = "View all", onAction = onOpenDeadlines)
                val days = nextDeadline?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(it.due)) }.getOrNull() }
                val fillContainer = when {
                    days == null -> MaterialTheme.colorScheme.primaryContainer
                    days <= 3 -> MaterialTheme.colorScheme.secondaryContainer
                    days <= 7 -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
                    colors = CardDefaults.cardColors(containerColor = fillContainer)
                ) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        val accentColor = when {
                            nextDeadline == null -> MaterialTheme.colorScheme.primary
                            days == null -> MaterialTheme.colorScheme.primary
                            days <= 3 -> MaterialTheme.colorScheme.error
                            days <= 7 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                        AccentBar(accentColor)
                        Column(Modifier.padding(14.dp).fillMaxWidth()) {
                            if (nextDeadline == null) {
                                if (dataReady) {
                                    EmptyState(
                                        icon = Icons.Default.Celebration,
                                        message = "Nothing due — you're clear"
                                    )
                                }
                            } else {
                                val d = nextDeadline!!
                                val dayLabel = when {
                                    days == null -> d.due
                                    days < 0 -> "${-days}d overdue"
                                    days == 0L -> "Due today"
                                    days == 1L -> "Due tomorrow"
                                    else -> "$days days left"
                                }
                                Text(
                                    d.title,
                                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = PunlaDisplay)
                                )
                                Text(
                                    "${d.course ?: d.type} · $dayLabel · ${d.priority} Priority",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // THIS WEEK VIEW
            val DAYS_OF_WEEK = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val DAY_LABEL_FULL = mapOf(
                "Mon" to "Monday", "Tue" to "Tuesday", "Wed" to "Wednesday",
                "Thu" to "Thursday", "Fri" to "Friday", "Sat" to "Saturday",
                "Sun" to "Sunday"
            )

            DAYS_OF_WEEK.forEach { d ->
                val dayClasses = classes.filter { it.day == d }.sortedBy { it.start }
                val dayDeadlinesFiltered = deadlines.filter { !it.done && runCatching {
                    val date = LocalDate.parse(it.due)
                    val map = mapOf(
                        java.time.DayOfWeek.MONDAY to "Mon", java.time.DayOfWeek.TUESDAY to "Tue",
                        java.time.DayOfWeek.WEDNESDAY to "Wed", java.time.DayOfWeek.THURSDAY to "Thu",
                        java.time.DayOfWeek.FRIDAY to "Fri", java.time.DayOfWeek.SATURDAY to "Sat",
                        java.time.DayOfWeek.SUNDAY to "Sun"
                    )
                    map[date.dayOfWeek] == d
                }.getOrDefault(false) }

                if (dayClasses.isNotEmpty() || dayDeadlinesFiltered.isNotEmpty()) {
                    item {
                        SectionLabel(DAY_LABEL_FULL[d] ?: d)
                    }
                    items(dayClasses, key = { it.id }) { c ->
                        ClassItemCompact(c)
                    }
                    items(dayDeadlinesFiltered, key = { it.id }) { dl ->
                        DeadlineItemCompact(dl)
                    }
                }
            }

            val hasAnyItems = classes.isNotEmpty() || deadlines.any { !it.done }
            if (!hasAnyItems && dataReady) {
                item {
                    EmptyState(
                        icon = Icons.Default.CalendarMonth,
                        message = "Your weekly schedule is completely clear!"
                    )
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun ClassItemCompact(c: ClassSession) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .shadow(0.5.dp, MaterialTheme.shapes.small, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.02f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.02f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            val accentColor = if (c.type == "lab") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            AccentBar(accentColor)
            Row(
                Modifier
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(c.code, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = PunlaDisplay, fontWeight = FontWeight.SemiBold))
                    Text(c.room ?: "Room TBA", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${c.start}–${c.end}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = PunlaMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DeadlineItemCompact(d: Deadline) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .shadow(0.5.dp, MaterialTheme.shapes.small, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.02f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.02f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            val days = runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(d.due)) }.getOrNull()
            val accentColor = when {
                days == null -> MaterialTheme.colorScheme.primary
                days <= 3 -> MaterialTheme.colorScheme.error
                days <= 7 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
            AccentBar(accentColor)
            Row(
                Modifier
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(d.title, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = PunlaDisplay, fontWeight = FontWeight.SemiBold))
                    Text(d.course ?: d.type, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Tag(
                    d.due,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    onContainer = MaterialTheme.colorScheme.primary,
                    mono = true
                )
            }
        }
    }
}

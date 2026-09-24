# Pomodoro Timer, Study Habits & Session Analysis — Implementation Guide

This is a build guide for a coding agent (or future-you) to implement three
connected features on top of the existing Punla codebase, in order:

1. **Pomodoro Timer** — run focus/break cycles, optionally tagged to a class/course.
2. **Study Habits** — daily/weekly study goals, streaks, per-course totals.
3. **Session Analysis** — a stats screen that turns logged sessions into insight.

Each phase produces a working, shippable slice. Phase 2 and 3 both depend on
Phase 1's data (a completed `StudySession` row), so build them in order.

Everything below follows the patterns already in the codebase — Room entity →
DAO → `PunlaRepository` property/method → `PunlaViewModel` observable state →
Compose screen — the same shape as `ClassSession`/`Deadline`/`Expense`. Read
the existing files named alongside each step before writing the new one; match
their conventions (SharedPreferences-style settings live on `PunlaRepository`
as `var` properties, Room-backed lists are exposed as `Flow`s, IDs are
`UUID.randomUUID().toString()`, day/date strings follow the same `"HH:mm"` /
ISO `yyyy-MM-dd` conventions used by `ClassSession`/`Deadline`).

---

## Phase 1 — Pomodoro Timer

### 1.1 Data model

New file: `app/src/main/java/com/uplb/punla/data/entity/StudySession.kt`

```kotlin
package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One completed (or abandoned) Pomodoro focus block. Breaks are NOT stored
 * as rows — only "work" intervals count as study time. courseCode links
 * loosely to ClassSession.code / GradeCourse.code (a plain string, not a
 * foreign key) so a session can still be tagged after its class is deleted
 * or a semester is archived.
 */
@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseCode: String? = null,      // e.g. "MATH 20", null = "Untagged"
    val startedAt: Long,                 // epoch millis
    val endedAt: Long,                   // epoch millis
    val plannedMinutes: Int,             // the work-interval length it was run at
    val actualSeconds: Int,              // real elapsed focus time (see 1.3 pause handling)
    val completed: Boolean,              // true = ran to 00:00 naturally, false = stopped early
    val cyclesInSession: Int = 1         // which pomodoro # this was within its session run (see 1.4)
)

/** User-tunable durations, persisted via PunlaRepository (see 1.2) rather
 * than as a Room row — this is a settings object, not a log. */
data class PomodoroSettings(
    val workMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val cyclesBeforeLongBreak: Int = 4,
    val autoStartNext: Boolean = false
)
```

New file: `app/src/main/java/com/uplb/punla/data/dao/StudySessionDao.kt`

```kotlin
package com.uplb.punla.data.dao

import androidx.room.*
import com.uplb.punla.data.entity.StudySession
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE startedAt >= :sinceEpochMillis ORDER BY startedAt DESC")
    fun observeSince(sinceEpochMillis: Long): Flow<List<StudySession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: StudySession)

    @Delete
    suspend fun delete(session: StudySession)

    @Query("DELETE FROM study_sessions")
    suspend fun clearAll()
}
```

Register both in `PunlaDatabase.kt`:
- Add `StudySession::class` to the `entities` array.
- Add `abstract fun studySessionDao(): StudySessionDao`.
- **Bump `version` from 4 to 5.** The DB already uses
  `.fallbackToDestructiveMigration()`, so no `Migration` object is required —
  existing local data for other tables survives a schema-hash mismatch
  destructively only if you skip the version bump; bumping it triggers Room's
  normal (non-destructive-looking but here-configured-destructive) upgrade
  path cleanly instead of crashing at startup.

### 1.2 Repository additions (`PunlaRepository.kt`)

Follow the existing `themePreset` / `fontChoice` pattern for settings, and the
existing `addExpense` / DAO-passthrough pattern for the log:

```kotlin
// ---- Pomodoro settings ----
var pomodoroWorkMinutes: Int
    get() = prefs.getInt("pomo_work_min", 25)
    set(value) = prefs.edit().putInt("pomo_work_min", value).apply()

var pomodoroShortBreakMinutes: Int
    get() = prefs.getInt("pomo_short_break_min", 5)
    set(value) = prefs.edit().putInt("pomo_short_break_min", value).apply()

var pomodoroLongBreakMinutes: Int
    get() = prefs.getInt("pomo_long_break_min", 15)
    set(value) = prefs.edit().putInt("pomo_long_break_min", value).apply()

var pomodoroCyclesBeforeLongBreak: Int
    get() = prefs.getInt("pomo_cycles_before_long", 4)
    set(value) = prefs.edit().putInt("pomo_cycles_before_long", value).apply()

var pomodoroAutoStartNext: Boolean
    get() = prefs.getBoolean("pomo_auto_start_next", false)
    set(value) = prefs.edit().putBoolean("pomo_auto_start_next", value).apply()

// ---- Study session log ----
fun observeStudySessions(): Flow<List<StudySession>> = db.studySessionDao().observeAll()

suspend fun logStudySession(session: StudySession) = db.studySessionDao().upsert(session)

suspend fun deleteStudySession(session: StudySession) = db.studySessionDao().delete(session)
```

### 1.3 Timer engine

Keep the timer's tick loop **in the ViewModel**, not in a `Service` or
`Worker` — Punla's Pomodoro is a foreground-only feature (like a stopwatch),
not something that needs to survive process death mid-tick. Do, however, use
a real wall-clock deadline rather than a naive decrementing counter, so the
countdown stays correct if the screen turns off, a call interrupts the app,
or the process is briefly backgrounded (Compose recomposition pausing a
`delay()` loop is a common source of drifting timers).

In `PunlaViewModel.kt`, add a small state machine:

```kotlin
enum class PomodoroPhase { IDLE, WORK, SHORT_BREAK, LONG_BREAK }

data class PomodoroUiState(
    val phase: PomodoroPhase = PomodoroPhase.IDLE,
    val remainingSeconds: Int = 0,
    val totalSecondsForPhase: Int = 0,
    val isRunning: Boolean = false,
    val cycleCount: Int = 0,          // completed WORK phases this run
    val courseCode: String? = null
)

var pomodoroState by mutableStateOf(PomodoroUiState())
    private set

private var pomodoroJob: kotlinx.coroutines.Job? = null
private var phaseDeadline: Long = 0L   // System.currentTimeMillis() target
private var phaseStartedAt: Long = 0L  // for actualSeconds on early stop

fun startPomodoroWork(courseCode: String?) {
    val minutes = repo.pomodoroWorkMinutes
    beginPhase(PomodoroPhase.WORK, minutes * 60, courseCode)
}

private fun beginPhase(phase: PomodoroPhase, seconds: Int, courseCode: String?) {
    pomodoroJob?.cancel()
    phaseStartedAt = System.currentTimeMillis()
    phaseDeadline = phaseStartedAt + seconds * 1000L
    pomodoroState = pomodoroState.copy(
        phase = phase, remainingSeconds = seconds, totalSecondsForPhase = seconds,
        isRunning = true, courseCode = courseCode ?: pomodoroState.courseCode
    )
    tickPomodoro()
}

private fun tickPomodoro() {
    pomodoroJob = viewModelScope.launch {
        while (pomodoroState.isRunning) {
            val remainingMs = phaseDeadline - System.currentTimeMillis()
            if (remainingMs <= 0) {
                onPhaseComplete()
                break
            }
            pomodoroState = pomodoroState.copy(remainingSeconds = (remainingMs / 1000).toInt())
            kotlinx.coroutines.delay(250) // sub-second poll, cheap, keeps UI smooth
        }
    }
}

fun pausePomodoro() {
    pomodoroJob?.cancel()
    pomodoroState = pomodoroState.copy(isRunning = false)
}

fun resumePomodoro() {
    phaseDeadline = System.currentTimeMillis() + pomodoroState.remainingSeconds * 1000L
    pomodoroState = pomodoroState.copy(isRunning = true)
    tickPomodoro()
}

/** Stops early. Logs a StudySession with completed=false if the phase was
 * WORK and at least 60s of real elapsed time had passed (skip logging
 * accidental taps under a minute — not a meaningful session). */
fun stopPomodoro() {
    pomodoroJob?.cancel()
    if (pomodoroState.phase == PomodoroPhase.WORK) {
        val actual = ((System.currentTimeMillis() - phaseStartedAt) / 1000).toInt()
        if (actual >= 60) logCompletedOrStoppedWork(actual, completed = false)
    }
    pomodoroState = PomodoroUiState()
}

private fun onPhaseComplete() {
    if (pomodoroState.phase == PomodoroPhase.WORK) {
        logCompletedOrStoppedWork(pomodoroState.totalSecondsForPhase, completed = true)
    }
    notifyPhaseComplete(pomodoroState.phase) // see 1.5

    val nextCycle = if (pomodoroState.phase == PomodoroPhase.WORK) pomodoroState.cycleCount + 1 else pomodoroState.cycleCount
    val nextPhase = when (pomodoroState.phase) {
        PomodoroPhase.WORK ->
            if (nextCycle % repo.pomodoroCyclesBeforeLongBreak == 0) PomodoroPhase.LONG_BREAK
            else PomodoroPhase.SHORT_BREAK
        else -> PomodoroPhase.WORK
    }
    pomodoroState = pomodoroState.copy(cycleCount = nextCycle, isRunning = false)

    if (repo.pomodoroAutoStartNext) {
        val seconds = when (nextPhase) {
            PomodoroPhase.WORK -> repo.pomodoroWorkMinutes
            PomodoroPhase.SHORT_BREAK -> repo.pomodoroShortBreakMinutes
            PomodoroPhase.LONG_BREAK -> repo.pomodoroLongBreakMinutes
            PomodoroPhase.IDLE -> 0
        } * 60
        beginPhase(nextPhase, seconds, pomodoroState.courseCode)
    } else {
        pomodoroState = pomodoroState.copy(phase = nextPhase, remainingSeconds = 0, totalSecondsForPhase = 0)
    }
}

private fun logCompletedOrStoppedWork(actualSeconds: Int, completed: Boolean) {
    viewModelScope.launch {
        repo.logStudySession(
            StudySession(
                courseCode = pomodoroState.courseCode,
                startedAt = phaseStartedAt,
                endedAt = System.currentTimeMillis(),
                plannedMinutes = repo.pomodoroWorkMinutes,
                actualSeconds = actualSeconds,
                completed = completed,
                cyclesInSession = pomodoroState.cycleCount + 1
            )
        )
    }
}
```

Notes on this design:
- **`courseCode` is a plain string, not a picked `ClassSession`.** Pull the
  option list from `vm.classes.distinctBy { it.code }` plus a manually-typed
  option, the same "loose tag" approach `Deadline.course` already uses
  elsewhere in this codebase — don't introduce a foreign key.
- The 60-second minimum before logging an early stop prevents junk rows from
  accidental start/stop taps polluting Phase 3's analysis.
- `pomodoroJob` must be cancelled in `onCleared()` if the ViewModel can be
  cleared mid-timer (e.g. process death) — add `override fun onCleared() { pomodoroJob?.cancel() }`
  if not already present.

### 1.4 Notifications on phase change

Reuse the existing notification-channel + permission pattern from
`ClassReminderWorker.kt` rather than inventing a new one — create the channel
once in `MainActivity`'s existing notification-channel setup (search for
where `NotificationChannel` is created for class/deadline reminders and add
a sibling `"pomodoro"` channel), then post directly from the ViewModel (no
`WorkManager` job needed here since this fires while the app is foregrounded):

```kotlin
private fun notifyPhaseComplete(justFinishedPhase: PomodoroPhase) {
    if (!repo.notificationsEnabled) return
    val (title, body) = when (justFinishedPhase) {
        PomodoroPhase.WORK -> "Focus block done" to "Time for a break."
        PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> "Break's over" to "Ready for another round?"
        PomodoroPhase.IDLE -> return
    }
    // Build + post via NotificationCompat exactly as ClassReminderWorker does,
    // guarded by the same POST_NOTIFICATIONS permission check on API 33+.
}
```

Also give the countdown a **sound/vibration** on phase change even if the
notification itself is suppressed by `notificationsEnabled = false` — a
Pomodoro timer that silently fails to alert you at 00:00 is a broken
Pomodoro timer. Use `HapticFeedbackType.LongPress` (already used elsewhere,
e.g. `ScheduleScreen.kt`'s save button) at minimum; a short tone via
`ToneGenerator` or a bundled notification sound is a nice-to-have.

### 1.5 UI — `PomodoroScreen.kt`

New file: `app/src/main/java/com/uplb/punla/ui/screens/PomodoroScreen.kt`,
registered as a new nav route `"pomodoro"` in `MainActivity.kt`:
- Add `Tab("pomodoro", "Focus", Icons.Default.Timer)` to the `TABS` list (or
  `DRAWER_ITEMS` if it shouldn't take a bottom-tab slot — check current tab
  count first; 5 tabs is already a lot for a bottom bar, so **prefer adding
  it to `DRAWER_ITEMS` only**, opened from the dashboard instead — see 1.6).
- Add the matching `composable("pomodoro") { PomodoroScreen(vm) }` inside the
  existing `NavHost`.

Screen contents, in order:
1. **Course picker** (only enabled while `phase == IDLE`) — a
   `PunlaDropdownField` (reuse the one from `ScheduleScreen.kt`) listing
   distinct course codes from `vm.classes`, plus "No course".
2. **Big countdown ring/number** — `remainingSeconds` formatted `MM:SS`,
   `totalSecondsForPhase` driving a circular progress indicator. Color it by
   phase using `LocalPunlaPalette.current` (e.g. `leaf` for WORK, `mango` for
   breaks) so it reads as "focus vs. rest" at a glance.
3. **Phase label** — "Focus", "Short break", "Long break".
4. **Controls** — Start / Pause / Resume / Stop, mirroring the
   `Button`/`OutlinedButton` pair styling from `ClassFormCard`'s
   save/cancel row.
5. **Cycle dots** — `cyclesBeforeLongBreak` small dots, filled up to
   `cycleCount % cyclesBeforeLongBreak`, so the person can see progress
   toward their next long break.
6. **Settings affordance** — durations editable inline (three
   `PunlaField`-style number inputs) or linked out to a "Pomodoro" card added
   to `SettingsScreen.kt`'s existing card list (prefer Settings, to keep this
   screen's layout uncluttered — follow the `APPEARANCE`/`FONT` card pattern
   already in that file).

### 1.6 Dashboard entry point

Add a compact "Start a focus session" card to `DashboardScreen.kt`'s
`LazyColumn`, near the top (after the greeting card from the earlier change,
before the stats row) — same `Card` + `shadow` + `border` treatment as the
other dashboard cards, tapping through to `onOpenPomodoro: () -> Unit`
(threaded through the same way `onOpenChecklist` already is).

---

## Phase 2 — Study Habits

Builds entirely on `StudySession` rows from Phase 1 — no new entity is
strictly required, but goals need one place to live.

### 2.1 Study goals (repository settings, not a new table)

```kotlin
// PunlaRepository.kt
var dailyStudyGoalMinutes: Int
    get() = prefs.getInt("daily_study_goal_min", 60)
    set(value) = prefs.edit().putInt("daily_study_goal_min", value).apply()

var weeklyStudyGoalMinutes: Int
    get() = prefs.getInt("weekly_study_goal_min", 420) // 7 * 60
    set(value) = prefs.edit().putInt("weekly_study_goal_min", value).apply()
```

Surface these as a "Study Goals" card in `SettingsScreen.kt`, same pattern as
the Font/Appearance cards — two numeric inputs.

### 2.2 Derived habit stats (repository, computed — not stored)

Add pure functions to `PunlaRepository.kt` (or a new
`data/StudyStatsEngine.kt` if it grows past ~5 functions — mirror how
`RecurrenceEngine.kt` was split out of the repository once deadline
recurrence logic got large):

```kotlin
/** Total actual study seconds for a given local calendar date. */
fun studySecondsOn(sessions: List<StudySession>, date: LocalDate): Int

/** Consecutive-day streak counting backward from today; a day "counts" once
 * dailyStudyGoalMinutes worth of actualSeconds has been logged on it. */
fun currentStudyStreak(sessions: List<StudySession>): Int

/** True if today's logged study time already meets dailyStudyGoalMinutes. */
fun todayGoalMet(sessions: List<StudySession>): Boolean
```

Use `java.time.LocalDate` derived from `startedAt` (via
`Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()`)
for all "which day did this happen on" bucketing — don't do millis-per-day
arithmetic, it breaks around DST.

### 2.3 ViewModel wiring

```kotlin
val studySessions: StateFlow<List<StudySession>> = repo.observeStudySessions()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

var dailyStudyGoalMinutes by mutableStateOf(repo.dailyStudyGoalMinutes)
    private set
fun updateDailyStudyGoal(minutes: Int) { repo.dailyStudyGoalMinutes = minutes; dailyStudyGoalMinutes = minutes }
// ...same pair for weekly
```

### 2.4 Dashboard habit surfacing

Extend the dashboard "Start a focus session" card (1.6) with a thin habit
strip underneath: today's studied-minutes vs. goal (a small linear
`LinearProgressIndicator`, same visual language as the budget screen's
existing progress bars — check `BudgetScreen.kt` for that component before
building a new one), plus the current streak as "🔥 4-day streak" text.
Keep this genuinely optional to look at — don't block or nag; a missed
streak should read as neutral information, not a guilt trip (this matches
the app's existing tone — see `daysUntilClassesStart`/backup-nudge copy for
the house style: encouraging, never scolding).

---

## Phase 3 — Study Session Analysis

New screen: `app/src/main/java/com/uplb/punla/ui/screens/StudyAnalysisScreen.kt`,
route `"study-analysis"`, opened from a button on `PomodoroScreen.kt` (a
history icon in its top area) rather than from the drawer — it's a drill-down
from the timer, not a top-level destination.

No charting library is in `build.gradle.kts` and none should be added for
this — everything below is small enough to hand-draw with Compose `Canvas`,
consistent with how `CampusMapView.kt`/`MapMarkerIcon.kt` already do custom
drawing in this codebase, and it avoids a new dependency for ~150 lines of
bar/line drawing.

### 3.1 Time range selector

A segmented control at the top: **Week / Month / All time** (reuse
`PunlaDropdownField` or a simple `SegmentedButton` row if the Material3
version in this project's BOM supports it — check
`androidx.compose.material3` API surface for `SegmentedButton` availability
in compose-bom `2024.06.00` before relying on it; fall back to a plain `Row`
of toggle `FilterChip`s if not present).

### 3.2 Summary row

Four stat tiles (reuse `DashboardStatsRow`'s tile composable from
`DashboardScreen.kt` if it's extractable, otherwise copy its shape):
- **Total focus time** for the selected range (sum of `actualSeconds`, formatted `Xh Ym`)
- **Sessions completed** vs **sessions started** (completion rate, e.g. "18/21 · 86%")
- **Average session length**
- **Current streak** (from 2.2)

### 3.3 Time-per-course breakdown

A horizontal bar per distinct `courseCode` (untagged sessions grouped under
"Other"), sorted descending by total `actualSeconds`, bar length scaled to
the max — a simple `Canvas` row per course, or a `Row` of `Box`es with
`fillMaxWidth(fraction = ...)`, colored by cycling through the current
`LocalPunlaPalette`'s accent colors (`leaf`, `maroon`, `mango`, etc.) so it's
reskinned automatically by the existing theme system rather than hardcoding
hex values.

### 3.4 Daily activity — a GitHub-style heatmap strip

A `Canvas`-drawn grid: one column per day (last 7/30/90 depending on range
selector), cell opacity/fill intensity scaled by that day's studied minutes
relative to `dailyStudyGoalMinutes` (0 → outline-only cell, 1.0+ → full
`leaf` fill). This is the highest-value, lowest-effort visualization here —
prioritize it if time is short and the rest of Phase 3 gets trimmed.

### 3.5 Session log list

Below the visuals, a plain reverse-chronological list (reuse list-row
styling from `DeadlinesScreen.kt` or `BudgetScreen.kt`'s expense list) of
individual `StudySession` rows: date, course tag, duration, and a small
"stopped early" indicator when `completed == false`. Support swipe-to-delete
or a long-press delete, consistent with how expenses/deadlines are removed
elsewhere in the app — check the exact affordance used in
`BudgetScreen.kt`/`DeadlinesScreen.kt` and match it rather than introducing a
third deletion gesture.

### 3.6 Backup/export

`BackupManager.kt` already serializes the other tables for export/import —
add `StudySession` to whatever JSON shape it builds (mirror exactly how
`Expense`/`Deadline` are folded in) so study history survives the existing
backup/restore flow instead of being silently excluded.

---

## Testing checklist before calling any phase done

- [ ] Timer stays accurate after backgrounding the app for 2+ minutes mid-work-phase (deadline-based, not naive decrement — see 1.3).
- [ ] Stopping a session under 60s does **not** create a `StudySession` row.
- [ ] Completing a WORK phase naturally always logs `completed = true`.
- [ ] Long break triggers exactly every `cyclesBeforeLongBreak` WORK phases, not off-by-one.
- [ ] Notification fires even if the phase completes while the app is in the background (foreground-only design from 1.3 means this **won't** currently work — if background alerting matters, revisit using `AlarmManager.setExactAndAllowWhileIdle` to schedule the phase-end notification up front rather than relying on a live coroutine, and cancel/reschedule it on pause/resume/stop).
- [ ] `PunlaDatabase` version bump doesn't throw on a device with an existing v4 local DB (test by installing the current shipped version first, then upgrading over it).
- [ ] Study streak correctly resets after a genuinely missed day, and correctly ignores today (today isn't "missed" until it's over).
- [ ] Deleting all classes doesn't crash the course picker (falls back to "No course" only).
- [ ] Analysis screen's empty state (zero sessions logged yet) reads as an invitation, not an error — match the "No classes scheduled" / "Nothing due" empty-state tone used elsewhere (`DashboardScreen.kt`'s `dataReady` gating).

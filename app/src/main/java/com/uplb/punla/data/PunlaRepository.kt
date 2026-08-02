package com.uplb.punla.data

import android.content.Context
import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.data.entity.Expense
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** SYSTEM follows the device's light/dark setting; LIGHT/DARK pin it regardless of the device. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Curated color presets, plus CUSTOM for a user-picked accent seed color. */
enum class ThemePreset { FIELD_NOTEBOOK, OCEAN, SUNSET, ORCHID, SLATE, CUSTOM }

/** How the app's ambient background renders. MINIMAL is a flat theme-color
 * fill, AMBIENT is the drifting-blob wash, STARFIELD is a twinkling star
 * field, PAPER_GRAIN is a static notebook-grain dot texture, RAIN is
 * falling diagonal streaks. Widgets mirror whichever style is picked by
 * rendering one frozen frame of it as a bitmap — see WIDGET_BACKGROUNDS.md,
 * Glance has no live animation surface. */
enum class BackgroundStyle { MINIMAL, AMBIENT, STARFIELD, PAPER_GRAIN, RAIN }

/** Which bundled font family(ies) drive the app's whole type ramp.
 * DEFAULT keeps the original look (Fraunces serif for headings, Inter sans
 * for body text); the rest pick a single family for both so the app reads
 * as more uniform. SYSTEM defers entirely to the device's default font. */
enum class FontChoice { DEFAULT, SANS, SERIF, MONO, SYSTEM }

/** Weekly Budgeting feature (WEEKLY_BUDGET_INSTRUCTIONS.md #2) — which
 * budget period(s) the Budget screen and widget show. MONTHLY is the
 * default so an upgrading install sees no change until they opt into the
 * weekly view, same reasoning as [BackgroundStyle]'s AMBIENT default. */
enum class BudgetPeriod { WEEKLY, MONTHLY, BOTH }

/** Shared across the schedule-related lookups below so we're not rebuilding
 * the same map/formatter on every call. */
private val DAY_ABBREV: Map<DayOfWeek, String> = mapOf(
    DayOfWeek.MONDAY to "Mon", DayOfWeek.TUESDAY to "Tue", DayOfWeek.WEDNESDAY to "Wed",
    DayOfWeek.THURSDAY to "Thu", DayOfWeek.FRIDAY to "Fri", DayOfWeek.SATURDAY to "Sat",
    DayOfWeek.SUNDAY to "Sun"
)
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Central place for the app + widgets to read "next class" / "budget balance" /
 * "next deadline" without duplicating logic (mirrors budgetPercent(), daysUntil()
 * etc. from the original web app).
 */
class PunlaRepository(context: Context) {
    private val db = PunlaDatabase.get(context)
    private val prefs = context.getSharedPreferences("punla_prefs", Context.MODE_PRIVATE)
    private val secureSecrets = SecureSecretStore(context)

    // ---- Settings ----
    var monthlyBudget: Double
        get() = prefs.getFloat("budget", 0f).toDouble()
        set(value) = prefs.edit().putFloat("budget", value.toFloat()).apply()

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(value) = prefs.edit().putString("user_name", value).apply()

    var chedTarget: Double?
        get() = if (prefs.contains("ched_target")) prefs.getFloat("ched_target", 0f).toDouble() else null
        set(value) {
            if (value == null) prefs.edit().remove("ched_target").apply()
            else prefs.edit().putFloat("ched_target", value.toFloat()).apply()
        }

    var themeMode: ThemeMode
        get() = when (prefs.getString("theme_mode", null)) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
        set(value) = prefs.edit().putString("theme_mode", value.name.lowercase()).apply()

    var themePreset: ThemePreset
        get() = when (prefs.getString("theme_preset", null)) {
            "ocean" -> ThemePreset.OCEAN
            "sunset" -> ThemePreset.SUNSET
            "orchid" -> ThemePreset.ORCHID
            "slate" -> ThemePreset.SLATE
            "custom" -> ThemePreset.CUSTOM
            else -> ThemePreset.FIELD_NOTEBOOK
        }
        set(value) = prefs.edit().putString("theme_preset", value.name.lowercase()).apply()

    /** ARGB int of the user-picked seed color; null until they set one. */
    var customSeedColor: Int?
        get() = if (prefs.contains("custom_seed_color")) prefs.getInt("custom_seed_color", 0) else null
        set(value) {
            if (value == null) prefs.edit().remove("custom_seed_color").apply()
            else prefs.edit().putInt("custom_seed_color", value).apply()
        }

    /** Defaults to AMBIENT — that's what the app unconditionally rendered
     * before this setting existed, so an upgrading install sees no visual
     * change until they actually open Settings and pick something else. */
    var backgroundStyle: BackgroundStyle
        get() = when (prefs.getString("background_style", null)) {
            "minimal" -> BackgroundStyle.MINIMAL
            "starfield" -> BackgroundStyle.STARFIELD
            "paper_grain" -> BackgroundStyle.PAPER_GRAIN
            "rain" -> BackgroundStyle.RAIN
            else -> BackgroundStyle.AMBIENT
        }
        set(value) = prefs.edit().putString("background_style", value.name.lowercase()).apply()

    var fontChoice: FontChoice
        get() = when (prefs.getString("font_choice", null)) {
            "sans" -> FontChoice.SANS
            "serif" -> FontChoice.SERIF
            "mono" -> FontChoice.MONO
            "system" -> FontChoice.SYSTEM
            else -> FontChoice.DEFAULT
        }
        set(value) = prefs.edit().putString("font_choice", value.name.lowercase()).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications_enabled", true)
        set(value) = prefs.edit().putBoolean("notifications_enabled", value).apply()

    // ---- Weekly budget (Weekly Budgeting feature) ----

    var budgetPeriod: BudgetPeriod
        get() = when (prefs.getString("budget_period", null)) {
            "weekly" -> BudgetPeriod.WEEKLY
            "both" -> BudgetPeriod.BOTH
            else -> BudgetPeriod.MONTHLY
        }
        set(value) = prefs.edit().putString("budget_period", value.name.lowercase()).apply()

    /** User-configurable start of the "week" for weekly budgeting — per the
     * plan doc, someone budgeting around an allowance/stipend may want the
     * week to start whenever money actually arrives rather than a calendar
     * Monday. Defaults to Monday (ISO 8601 convention). */
    var weekStartDay: DayOfWeek
        get() = runCatching {
            DayOfWeek.valueOf(prefs.getString("week_start_day", null) ?: "")
        }.getOrDefault(DayOfWeek.MONDAY)
        set(value) = prefs.edit().putString("week_start_day", value.name).apply()

    /** Explicit weekly budget figure the person set themselves; null means
     * it's derived automatically from [monthlyBudget] instead (see
     * [weeklyBudgetBaseFromList]). Answers open question #1 from the plan
     * doc: independently editable when set, auto-derived otherwise. */
    var weeklyBudgetOverride: Double?
        get() = if (prefs.contains("weekly_budget_override")) prefs.getFloat("weekly_budget_override", 0f).toDouble() else null
        set(value) {
            if (value == null) prefs.edit().remove("weekly_budget_override").apply()
            else prefs.edit().putFloat("weekly_budget_override", value.toFloat()).apply()
        }

    /** Off by default — unused weekly budget resets each week rather than
     * carrying forward, per the plan doc's recommendation (simpler to
     * reason about, harder to "game" than a quietly-accumulating balance). */
    var weeklyRolloverEnabled: Boolean
        get() = prefs.getBoolean("weekly_rollover_enabled", false)
        set(value) = prefs.edit().putBoolean("weekly_rollover_enabled", value).apply()

    /** Stashed locally until a backend /subscribe endpoint exists to send it to. */
    var fcmToken: String?
        get() = prefs.getString("fcm_token", null)
        set(value) = prefs.edit().putString("fcm_token", value).apply()

    // ---- Backup nudges (roadmap #6) ----

    /** Epoch millis of the last successful [BackupManager.exportTo], or null
     * if the person has never exported a backup. */
    var lastBackupAt: Long?
        get() = if (prefs.contains("last_backup_at")) prefs.getLong("last_backup_at", 0L) else null
        set(value) {
            if (value == null) prefs.edit().remove("last_backup_at").apply()
            else prefs.edit().putLong("last_backup_at", value).apply()
        }

    /** Epoch millis [BackupNudgeWorker] last actually posted a nudge
     * notification — tracked separately from [lastBackupAt] so the worker
     * doesn't re-nudge every day just because the backup is still stale. */
    var lastBackupNudgeAt: Long?
        get() = if (prefs.contains("last_backup_nudge_at")) prefs.getLong("last_backup_nudge_at", 0L) else null
        set(value) {
            if (value == null) prefs.edit().remove("last_backup_nudge_at").apply()
            else prefs.edit().putLong("last_backup_nudge_at", value).apply()
        }

    // ---- Personal intelligence / assistant settings ----

    var termStartDate: LocalDate
        get() = runCatching { LocalDate.parse(prefs.getString("term_start_date", null)) }
            .getOrDefault(classesStartDate)
        set(value) = prefs.edit().putString("term_start_date", value.toString()).apply()

    var termEndDate: LocalDate
        get() = runCatching { LocalDate.parse(prefs.getString("term_end_date", null)) }
            .getOrDefault(termStartDate.plusWeeks(15).minusDays(1))
        set(value) = prefs.edit().putString("term_end_date", value.toString()).apply()

    var cloudAssistantEnabled: Boolean
        get() = prefs.getBoolean("cloud_assistant_enabled", false)
        set(value) = prefs.edit().putBoolean("cloud_assistant_enabled", value).apply()

    var assistantModel: String
        get() = prefs.getString("assistant_model", "claude-haiku-4-5") ?: "claude-haiku-4-5"
        set(value) = prefs.edit().putString("assistant_model", value.trim()).apply()

    val assistantDailyCallLimit: Int get() = 10

    fun consumeAssistantCall(): Boolean {
        val today = LocalDate.now().toString()
        val storedDate = prefs.getString("assistant_call_date", null)
        val count = if (storedDate == today) prefs.getInt("assistant_call_count", 0) else 0
        if (count >= assistantDailyCallLimit) return false
        prefs.edit()
            .putString("assistant_call_date", today)
            .putInt("assistant_call_count", count + 1)
            .apply()
        return true
    }

    fun assistantCallsUsedToday(): Int =
        if (prefs.getString("assistant_call_date", null) == LocalDate.now().toString())
            prefs.getInt("assistant_call_count", 0)
        else 0

    var assistantApiKey: String?
        get() = secureSecrets.getAssistantApiKey()
        set(value) = secureSecrets.setAssistantApiKey(value.orEmpty())

    var preferredReminderHour: Int?
        get() = if (prefs.contains("preferred_reminder_hour")) prefs.getInt("preferred_reminder_hour", 19) else null
        set(value) {
            if (value == null) prefs.edit().remove("preferred_reminder_hour").apply()
            else prefs.edit().putInt("preferred_reminder_hour", value.coerceIn(0, 23)).apply()
        }

    var dismissedExpensePatternKeys: Set<String>
        get() = prefs.getStringSet("dismissed_expense_pattern_keys", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("dismissed_expense_pattern_keys", value).apply()

    var pendingStudySuggestionId: String?
        get() = prefs.getString("pending_study_suggestion_id", null)
        set(value) = prefs.edit().putString("pending_study_suggestion_id", value).apply()

    var pendingStudySuggestionFeatures: String?
        get() = prefs.getString("pending_study_suggestion_features", null)
        set(value) = prefs.edit().putString("pending_study_suggestion_features", value).apply()

    var studySlotModelState: com.uplb.punla.ml.StudySlotModelState
        get() {
            val weights = prefs.getString("study_slot_model_weights", null)
                ?.split(',')?.mapNotNull { it.toDoubleOrNull() }
                ?.takeIf { it.size == com.uplb.punla.ml.StudySlotModelState.FEATURE_COUNT }
                ?: List(com.uplb.punla.ml.StudySlotModelState.FEATURE_COUNT) { 0.0 }
            return com.uplb.punla.ml.StudySlotModelState(
                weights = weights,
                bias = java.lang.Double.longBitsToDouble(prefs.getLong("study_slot_model_bias", 0L)),
                sampleCount = prefs.getInt("study_slot_model_samples", 0),
                version = prefs.getInt("study_slot_model_version", 1)
            )
        }
        set(value) {
            prefs.edit()
                .putString("study_slot_model_weights", value.weights.joinToString(","))
                .putLong("study_slot_model_bias", java.lang.Double.doubleToRawLongBits(value.bias))
                .putInt("study_slot_model_samples", value.sampleCount)
                .putInt("study_slot_model_version", value.version)
                .apply()
        }

    // ---- Free-time study suggestion (Free-Time Study Suggestions, Phase 1) ----

    /** Epoch millis the person last dismissed the Dashboard's free-slot
     * study suggestion — if that was today, don't show it again until
     * tomorrow. Same pattern as [lastBackupNudgeAt]; no new Room table. */
    var lastStudySuggestionDismissedAt: Long?
        get() = if (prefs.contains("last_study_suggestion_dismissed_at")) prefs.getLong("last_study_suggestion_dismissed_at", 0L) else null
        set(value) {
            if (value == null) prefs.edit().remove("last_study_suggestion_dismissed_at").apply()
            else prefs.edit().putLong("last_study_suggestion_dismissed_at", value).apply()
        }

    // ---- Budget low-balance nudges ----

    /** Epoch millis [BudgetWorker][com.uplb.punla.worker.BudgetWorker] last
     * posted a nudge notification, tracked purely for display/debugging —
     * threshold state below is what actually gates re-firing. */
    var lastBudgetNudgeAt: Long?
        get() = if (prefs.contains("last_budget_nudge_at")) prefs.getLong("last_budget_nudge_at", 0L) else null
        set(value) {
            if (value == null) prefs.edit().remove("last_budget_nudge_at").apply()
            else prefs.edit().putLong("last_budget_nudge_at", value).apply()
        }

    /** Last threshold (0, 80, or 100) nudged for [lastBudgetNudgeMonth] — 0
     * means nothing's been nudged yet this month. Kept alongside the month
     * it applies to so a new month effectively resets it without needing a
     * separate scheduled reset. */
    var lastBudgetNudgeThreshold: Int
        get() = prefs.getInt("last_budget_nudge_threshold", 0)
        set(value) = prefs.edit().putInt("last_budget_nudge_threshold", value).apply()

    /** "yyyy-MM" the threshold above was recorded for. */
    var lastBudgetNudgeMonth: String?
        get() = prefs.getString("last_budget_nudge_month", null)
        set(value) = prefs.edit().putString("last_budget_nudge_month", value).apply()

    /** Same idea as [lastBudgetNudgeThreshold]/[lastBudgetNudgeMonth] above,
     * but for the weekly figure — tracked separately since the two periods
     * can cross their thresholds at different times. Keyed by week-start
     * ISO date rather than "yyyy-MM" so it naturally resets every week. */
    var lastWeeklyBudgetNudgeThreshold: Int
        get() = prefs.getInt("last_weekly_budget_nudge_threshold", 0)
        set(value) = prefs.edit().putInt("last_weekly_budget_nudge_threshold", value).apply()

    var lastWeeklyBudgetNudgeWeekStart: String?
        get() = prefs.getString("last_weekly_budget_nudge_week_start", null)
        set(value) = prefs.edit().putString("last_weekly_budget_nudge_week_start", value).apply()

    // ---- Pre-enrollment checklist nudges ----

    /** ISO date the next term's classes start. Hardcoded for now (mirrors
     * STANDARD_TERM_WEEKS in ClassSession.kt being a flat UP constant) —
     * would move to per-School data alongside termWeeks if/when the
     * multi-university work lands. */
    val classesStartDate: LocalDate get() = LocalDate.of(2026, 8, 3)

    /** Epoch millis [com.uplb.punla.worker.ChecklistReminderWorker] last
     * posted a nudge, tracked purely for display/debugging. */
    var lastChecklistNudgeAt: Long?
        get() = if (prefs.contains("last_checklist_nudge_at")) prefs.getLong("last_checklist_nudge_at", 0L) else null
        set(value) {
            if (value == null) prefs.edit().remove("last_checklist_nudge_at").apply()
            else prefs.edit().putLong("last_checklist_nudge_at", value).apply()
        }

    /** Days-until-classes value last nudged at, so the worker only fires
     * once per threshold crossing (14/7/3/1/0 days out) instead of once a
     * day regardless of whether anything changed. */
    var lastChecklistNudgeDaysOut: Long
        get() = if (prefs.contains("last_checklist_nudge_days_out")) prefs.getLong("last_checklist_nudge_days_out", Long.MAX_VALUE) else Long.MAX_VALUE
        set(value) = prefs.edit().putLong("last_checklist_nudge_days_out", value).apply()

    /** Resolves the stored preference against the device's current setting. */
    fun isDarkTheme(systemInDarkTheme: Boolean): Boolean = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemInDarkTheme
    }

    /** Same resolution as [isDarkTheme], but reads the system setting from
     * [Context.getResources] instead of taking `isSystemInDarkTheme()` as a
     * parameter — Glance widgets run outside the app's own Compose
     * composition, so that composable isn't reachable from `provideGlance`. */
    fun isDarkModeActive(context: Context): Boolean = isDarkTheme(
        systemInDarkTheme = (context.resources.configuration.uiMode
            and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    )

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

    /** Keep the active timer visible in Android Picture-in-Picture when the
     * user leaves Punla. Enabled by default because it is useful during PDFs,
     * lecture videos, and note-taking, but remains fully optional. */
    var pomodoroPictureInPicture: Boolean
        get() = prefs.getBoolean("pomo_picture_in_picture", true)
        set(value) = prefs.edit().putBoolean("pomo_picture_in_picture", value).apply()

    /** Shows a silent, ongoing Android notification whose system chronometer
     * counts down to the active phase deadline. This remains accurate without
     * waking the app every second. */
    var pomodoroTimerNotification: Boolean
        get() = prefs.getBoolean("pomo_timer_notification", true)
        set(value) = prefs.edit().putBoolean("pomo_timer_notification", value).apply()

    var pomodoroAlarmSoundEnabled: Boolean
        get() = prefs.getBoolean("pomo_alarm_sound_enabled", true)
        set(value) = prefs.edit().putBoolean("pomo_alarm_sound_enabled", value).apply()

    var pomodoroAlarmVibrationEnabled: Boolean
        get() = prefs.getBoolean("pomo_alarm_vibration_enabled", true)
        set(value) = prefs.edit().putBoolean("pomo_alarm_vibration_enabled", value).apply()

    /** Null means the device's current default alarm sound. */
    var pomodoroWorkSoundUri: String?
        get() = prefs.getString("pomo_work_sound_uri", null)
        set(value) {
            if (value == null) prefs.edit().remove("pomo_work_sound_uri").apply()
            else prefs.edit().putString("pomo_work_sound_uri", value).apply()
        }

    /** Null means the device's current default alarm sound. */
    var pomodoroBreakSoundUri: String?
        get() = prefs.getString("pomo_break_sound_uri", null)
        set(value) {
            if (value == null) prefs.edit().remove("pomo_break_sound_uri").apply()
            else prefs.edit().putString("pomo_break_sound_uri", value).apply()
        }

    // ---- Pomodoro runtime snapshot ----
    // Kept separate from the user-editable settings above. The countdown uses
    // an absolute wall-clock deadline, so reopening the app after switching
    // apps (or after Android recreates the process) can recover the correct
    // remaining time instead of resetting to IDLE.
    var pomodoroRuntimePhase: String?
        get() = prefs.getString("pomo_runtime_phase", null)
        set(value) {
            if (value == null) prefs.edit().remove("pomo_runtime_phase").apply()
            else prefs.edit().putString("pomo_runtime_phase", value).apply()
        }

    var pomodoroRuntimeDeadline: Long
        get() = prefs.getLong("pomo_runtime_deadline", 0L)
        set(value) = prefs.edit().putLong("pomo_runtime_deadline", value).apply()

    var pomodoroRuntimeStartedAt: Long
        get() = prefs.getLong("pomo_runtime_started_at", 0L)
        set(value) = prefs.edit().putLong("pomo_runtime_started_at", value).apply()

    var pomodoroRuntimeRemainingSeconds: Int
        get() = prefs.getInt("pomo_runtime_remaining_seconds", 0)
        set(value) = prefs.edit().putInt("pomo_runtime_remaining_seconds", value).apply()

    var pomodoroRuntimeTotalSeconds: Int
        get() = prefs.getInt("pomo_runtime_total_seconds", 0)
        set(value) = prefs.edit().putInt("pomo_runtime_total_seconds", value).apply()

    var pomodoroRuntimeRunning: Boolean
        get() = prefs.getBoolean("pomo_runtime_running", false)
        set(value) = prefs.edit().putBoolean("pomo_runtime_running", value).apply()

    var pomodoroRuntimeCycleCount: Int
        get() = prefs.getInt("pomo_runtime_cycle_count", 0)
        set(value) = prefs.edit().putInt("pomo_runtime_cycle_count", value).apply()

    var pomodoroRuntimeCourseCode: String?
        get() = prefs.getString("pomo_runtime_course_code", null)
        set(value) {
            if (value == null) prefs.edit().remove("pomo_runtime_course_code").apply()
            else prefs.edit().putString("pomo_runtime_course_code", value).apply()
        }

    /** Deadline most recently claimed by either the in-app clock or the
     * AlarmManager receiver. This makes completion idempotent when both wake
     * at nearly the same millisecond. */
    var pomodoroLastHandledDeadline: Long
        get() = prefs.getLong("pomo_last_handled_deadline", 0L)
        set(value) = prefs.edit().putLong("pomo_last_handled_deadline", value).apply()

    fun savePomodoroRuntime(
        phase: String,
        deadline: Long,
        startedAt: Long,
        remainingSeconds: Int,
        totalSeconds: Int,
        running: Boolean,
        cycleCount: Int,
        courseCode: String?
    ) {
        prefs.edit()
            .putString("pomo_runtime_phase", phase)
            .putLong("pomo_runtime_deadline", deadline)
            .putLong("pomo_runtime_started_at", startedAt)
            .putInt("pomo_runtime_remaining_seconds", remainingSeconds)
            .putInt("pomo_runtime_total_seconds", totalSeconds)
            .putBoolean("pomo_runtime_running", running)
            .putInt("pomo_runtime_cycle_count", cycleCount)
            .apply {
                if (courseCode == null) remove("pomo_runtime_course_code")
                else putString("pomo_runtime_course_code", courseCode)
            }
            .apply()
    }

    fun clearPomodoroRuntime() {
        prefs.edit()
            .remove("pomo_runtime_phase")
            .remove("pomo_runtime_deadline")
            .remove("pomo_runtime_started_at")
            .remove("pomo_runtime_remaining_seconds")
            .remove("pomo_runtime_total_seconds")
            .remove("pomo_runtime_running")
            .remove("pomo_runtime_cycle_count")
            .remove("pomo_runtime_course_code")
            .apply()
    }

    // ---- Study session log ----
    fun observeStudySessions(): kotlinx.coroutines.flow.Flow<List<com.uplb.punla.data.entity.StudySession>> =
        db.studySessionDao().observeAll()

    suspend fun logStudySession(session: com.uplb.punla.data.entity.StudySession) =
        db.studySessionDao().upsert(session)

    suspend fun deleteStudySession(session: com.uplb.punla.data.entity.StudySession) =
        db.studySessionDao().delete(session)

    fun observeStudySuggestionEvents(): kotlinx.coroutines.flow.Flow<List<com.uplb.punla.data.entity.StudySuggestionEvent>> =
        db.intelligenceDao().observeStudySuggestionEvents()

    suspend fun logStudySuggestionEvent(event: com.uplb.punla.data.entity.StudySuggestionEvent) =
        db.intelligenceDao().insertStudySuggestionEvent(event)

    suspend fun latestStudySuggestionEvent(suggestionId: String) =
        db.intelligenceDao().latestStudySuggestionEvent(suggestionId)

    suspend fun clearStudySuggestionEvents() = db.intelligenceDao().clearStudySuggestionEvents()

    fun observeNotificationEvents(): kotlinx.coroutines.flow.Flow<List<com.uplb.punla.data.entity.NotificationEvent>> =
        db.intelligenceDao().observeNotificationEvents()

    suspend fun logNotificationEvent(event: com.uplb.punla.data.entity.NotificationEvent) =
        db.intelligenceDao().insertNotificationEvent(event)

    suspend fun clearNotificationEvents() = db.intelligenceDao().clearNotificationEvents()

    // ---- Study goals (roadmap Study Habits 2.1) ----
    var dailyStudyGoalMinutes: Int
        get() = prefs.getInt("daily_study_goal_min", 60)
        set(value) = prefs.edit().putInt("daily_study_goal_min", value).apply()

    var weeklyStudyGoalMinutes: Int
        get() = prefs.getInt("weekly_study_goal_min", 420) // 7 * 60
        set(value) = prefs.edit().putInt("weekly_study_goal_min", value).apply()

    // ---- Derived habit stats (roadmap Study Habits 2.2) — pure functions
    // over an already-loaded session list, computed rather than stored, so
    // the ViewModel can recompute them cheaply whenever studySessions emits.
    // LocalDate bucketing goes through the system zone rather than raw
    // millis-per-day math so it doesn't drift across DST.

    /** Total actual study seconds for a given local calendar date. */
    fun studySecondsOn(sessions: List<com.uplb.punla.data.entity.StudySession>, date: LocalDate): Int =
        sessions.filter { sessionLocalDate(it.startedAt) == date }.sumOf { it.actualSeconds }

    /** Consecutive-day streak counting backward from today; a day "counts"
     * once [dailyStudyGoalMinutes] worth of actualSeconds has been logged on
     * it. Today isn't required to count yet (it's still in progress), so an
     * empty-so-far today doesn't break a streak built on prior days. */
    fun currentStudyStreak(sessions: List<com.uplb.punla.data.entity.StudySession>): Int {
        if (sessions.isEmpty()) return 0
        val goalSeconds = dailyStudyGoalMinutes * 60
        val today = LocalDate.now()
        var streak = 0
        var day = today
        while (true) {
            val metGoal = studySecondsOn(sessions, day) >= goalSeconds
            if (day == today) {
                // Today doesn't need to be met yet to keep the streak alive;
                // it just doesn't count toward it until it is.
                if (metGoal) streak++
            } else {
                if (!metGoal) break
                streak++
            }
            day = day.minusDays(1)
        }
        return streak
    }

    /** True if today's logged study time already meets [dailyStudyGoalMinutes]. */
    fun todayGoalMet(sessions: List<com.uplb.punla.data.entity.StudySession>): Boolean =
        studySecondsOn(sessions, LocalDate.now()) >= dailyStudyGoalMinutes * 60

    private fun sessionLocalDate(epochMillis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    // ---- Schedule ----
    suspend fun allClasses(): List<ClassSession> = db.classSessionDao().getAll()

    suspend fun nextClass(now: LocalDateTime = LocalDateTime.now()): ClassSession? {
        return nextClassFromList(allClasses(), now)
    }

    fun nextClassFromList(classes: List<ClassSession>, now: LocalDateTime = LocalDateTime.now()): ClassSession? {
        if (classes.isEmpty()) return null
        val today = now.dayOfWeek
        val order = DayOfWeek.entries.dropWhile { it != today } + DayOfWeek.entries.takeWhile { it != today }
        for (day in order) {
            val abbrev = DAY_ABBREV[day] ?: continue
            val todaysClasses = classes.filter { it.day == abbrev }
                .sortedBy { it.start }
            for (c in todaysClasses) {
                val start = runCatching { LocalTime.parse(c.start, TIME_FORMAT) }.getOrNull() ?: continue
                if (day != today || start >= now.toLocalTime()) {
                    return c
                }
            }
        }
        return null
    }

    /**
     * All of today's classes that haven't ended yet, in start-time order —
     * used by the Next Class widget's "today's classes" list so it can show
     * more than just the single next one.
     */
    fun todaysRemainingClassesFromList(
        classes: List<ClassSession>,
        now: LocalDateTime = LocalDateTime.now()
    ): List<ClassSession> {
        val todayAbbrev = DAY_ABBREV[now.dayOfWeek] ?: return emptyList()
        return classes.filter { it.day == todayAbbrev }
            .filter { c ->
                val end = runCatching { LocalTime.parse(c.end, TIME_FORMAT) }.getOrNull()
                end == null || end > now.toLocalTime()
            }
            .sortedBy { it.start }
    }

    suspend fun todaysRemainingClasses(now: LocalDateTime = LocalDateTime.now()): List<ClassSession> =
        todaysRemainingClassesFromList(allClasses(), now)

    /** True if [now] falls between the class's start and end today. */
    fun isOngoing(classSession: ClassSession, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val start = runCatching { LocalTime.parse(classSession.start, TIME_FORMAT) }.getOrNull() ?: return false
        val end = runCatching { LocalTime.parse(classSession.end, TIME_FORMAT) }.getOrNull() ?: return false
        val t = now.toLocalTime()
        return t >= start && t < end
    }

    /**
     * All of today's classes starting within the next [windowMinutes] minutes
     * (inclusive of "starting right now"). Mirrors the web app's
     * checkReminders() (index.html ~line 783) class-start check.
     */
    fun classesStartingSoon(
        classes: List<ClassSession>,
        now: LocalDateTime = LocalDateTime.now(),
        windowMinutes: Long = 15
    ): List<ClassSession> {
        val todayAbbrev = DAY_ABBREV[now.dayOfWeek] ?: return emptyList()
        return classes.filter { it.day == todayAbbrev }.filter { c ->
            val start = runCatching {
                LocalTime.parse(c.start, TIME_FORMAT)
            }.getOrNull() ?: return@filter false
            val diffMin = java.time.Duration.between(now.toLocalTime(), start).toMinutes()
            diffMin in 0..windowMinutes
        }
    }

    // ---- Expenses / budget ----
    suspend fun budgetSpentThisMonth(): Double {
        val now = LocalDate.now()
        val yearMonth = "%04d-%02d".format(now.year, now.monthValue)
        return db.expenseDao().sumForMonth(yearMonth)
    }

    suspend fun budgetRemaining(): Double = monthlyBudget - budgetSpentThisMonth()

    /**
     * Last 7 days of spend (today inclusive), oldest to newest, with zero
     * days filled in so the Budget widget's mini bar chart always has 7
     * entries to render — a day with no expenses still shows a bar, just a
     * very short one, instead of collapsing the row.
     */
    suspend fun spendingLast7Days(): List<Pair<LocalDate, Double>> {
        val today = LocalDate.now()
        val start = today.minusDays(6) // 7 days inclusive of today
        val rows = db.expenseDao().sumByDaySince(start.toString())
            .associate { LocalDate.parse(it.date) to it.total }
        return (0..6).map { offset ->
            val d = start.plusDays(offset.toLong())
            d to (rows[d] ?: 0.0)
        }
    }

    /** Inserts a one-off expense. Used by the Budget widget's quick-add button
     * so it doesn't need to go through PunlaViewModel. Caller is responsible
     * for refreshing widgets afterward via [com.uplb.punla.widget.WidgetRefresher]. */
    suspend fun addExpense(expense: Expense) = db.expenseDao().upsert(expense)

    // ---- Weekly budget (Weekly Budgeting feature) — pure functions over an
    // already-loaded expense list, same shape as nextClassFromList() etc.
    // above: the Budget screen calls these directly against its reactive
    // Room Flow (no extra queries), and the widget's suspend wrappers below
    // fetch the list once via [allExpenses] and call the same functions, so
    // the two never drift apart.

    /** The most recent occurrence of [weekStartDay] on/before [referenceDate]
     * — the start of the "current week" for weekly budgeting. */
    fun currentWeekStart(referenceDate: LocalDate = LocalDate.now()): LocalDate {
        var d = referenceDate
        while (d.dayOfWeek != weekStartDay) d = d.minusDays(1)
        return d
    }

    /** Every [weekStartDay]-to-[weekStartDay] week whose *start* date falls
     * within [ym] — used to spread the monthly budget across the weeks that
     * belong to that month. A week is attributed to the month its start
     * falls in, even if most of its days spill into the next month. */
    private fun weeksStartingIn(ym: YearMonth): List<LocalDate> {
        var d = ym.atDay(1)
        while (d.dayOfWeek != weekStartDay) d = d.plusDays(1)
        val weeks = mutableListOf<LocalDate>()
        while (!d.isAfter(ym.atEndOfMonth())) {
            weeks.add(d)
            d = d.plusWeeks(1)
        }
        return weeks.ifEmpty { listOf(ym.atDay(1)) }
    }

    /** Sum of [expenses] dated within [start]..[end] inclusive, optionally
     * excluding fixed/recurring bills (see [Expense.isFixed], plan doc #4.3). */
    fun sumInRange(expenses: List<Expense>, start: LocalDate, end: LocalDate, excludeFixed: Boolean = false): Double =
        expenses.filter { e ->
            (!excludeFixed || !e.isFixed) &&
                runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { it in start..end } == true
        }.sumOf { it.amount }

    /**
     * The auto-derived weekly figure when [weeklyBudgetOverride] isn't set:
     * the remaining monthly budget as of the start of [weekStart], split
     * across the weeks of that month still to come (including this one).
     * Per the plan doc: overspending early in the month visibly tightens
     * later weeks' derived figure instead of resetting to a flat
     * `monthlyBudget / weeksInMonth` every week.
     */
    fun weeklyBudgetDerivedFromList(expenses: List<Expense>, weekStart: LocalDate): Double {
        if (monthlyBudget <= 0) return 0.0
        val ym = YearMonth.from(weekStart)
        val weeks = weeksStartingIn(ym)
        val indexInMonth = weeks.indexOf(weekStart).coerceAtLeast(0)
        val weeksRemaining = (weeks.size - indexInMonth).coerceAtLeast(1)
        val monthStart = ym.atDay(1)
        val spentBeforeThisWeek = if (weekStart > monthStart) {
            sumInRange(expenses, monthStart, weekStart.minusDays(1))
        } else 0.0
        return (monthlyBudget - spentBeforeThisWeek) / weeksRemaining
    }

    /** [weeklyBudgetOverride] if the person set one explicitly, otherwise
     * [weeklyBudgetDerivedFromList] — the weekly figure *before* rollover. */
    fun weeklyBudgetBaseFromList(expenses: List<Expense>, weekStart: LocalDate = currentWeekStart()): Double =
        weeklyBudgetOverride ?: weeklyBudgetDerivedFromList(expenses, weekStart)

    /**
     * Carry-forward from the immediately preceding week when
     * [weeklyRolloverEnabled] is on: last week's unspent budget (or
     * overspend, as a negative number) rolls into this week. Only looks one
     * week back — a deliberately stateless, one-hop calculation rather than
     * a persisted ledger that could quietly accumulate across many weeks,
     * in the spirit of the plan doc's "harder to game" reasoning for
     * defaulting this off. Returns 0 when rollover is disabled.
     */
    fun weeklyRolloverCarryFromList(expenses: List<Expense>, weekStart: LocalDate): Double {
        if (!weeklyRolloverEnabled) return 0.0
        val prevWeekStart = weekStart.minusWeeks(1)
        val prevBase = weeklyBudgetBaseFromList(expenses, prevWeekStart)
        val prevSpent = sumInRange(expenses, prevWeekStart, prevWeekStart.plusDays(6), excludeFixed = true)
        return prevBase - prevSpent
    }

    /** The weekly figure actually shown/used: base (override or derived)
     * plus rollover carry, if enabled. */
    fun weeklyBudgetAmountFromList(expenses: List<Expense>, weekStart: LocalDate = currentWeekStart()): Double =
        weeklyBudgetBaseFromList(expenses, weekStart) + weeklyRolloverCarryFromList(expenses, weekStart)

    /** This week's discretionary spend (fixed/recurring bills excluded). */
    fun weeklyBudgetSpentFromList(expenses: List<Expense>, weekStart: LocalDate = currentWeekStart()): Double =
        sumInRange(expenses, weekStart, weekStart.plusDays(6), excludeFixed = true)

    /** All logged expenses, for callers (like the widget) that don't already
     * hold a reactive list the way the Budget screen's Room Flow does. */
    suspend fun allExpenses(): List<Expense> = db.expenseDao().getAll()

    /** Suspend wrapper of [weeklyBudgetAmountFromList] for non-Compose
     * callers (the Glance widget) that need to fetch the list first. */
    suspend fun weeklyBudgetAmount(referenceDate: LocalDate = LocalDate.now()): Double {
        val weekStart = currentWeekStart(referenceDate)
        return weeklyBudgetAmountFromList(allExpenses(), weekStart)
    }

    /** Suspend wrapper of [weeklyBudgetSpentFromList], see above. */
    suspend fun weeklyBudgetSpent(referenceDate: LocalDate = LocalDate.now()): Double {
        val weekStart = currentWeekStart(referenceDate)
        return weeklyBudgetSpentFromList(allExpenses(), weekStart)
    }

    // ---- Deadlines ----
    suspend fun getDeadlines(): List<Deadline> = db.deadlineDao().getAll()

    suspend fun nextDeadline(): Deadline? =
        getDeadlines()
            .filter { !it.done }
            .minByOrNull { it.due }
    fun daysUntil(dueIso: String): Long {
        val due = runCatching { LocalDate.parse(dueIso) }.getOrNull() ?: return Long.MAX_VALUE
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), due)
    }

    // ---- Pre-enrollment checklist ----
    suspend fun getChecklistItems(): List<com.uplb.punla.data.entity.ChecklistItem> = db.checklistDao().getAll()

    /** Days remaining until [classesStartDate] (negative once classes have started). */
    fun daysUntilClassesStart(): Long =
        java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), classesStartDate)
}

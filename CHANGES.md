# Punla Android — Change Log

This build merges two parallel sessions that both started from the same
base and independently implemented the same roadmap items. Rather than
keep two divergent copies, this is one consolidated codebase: the newer
session's refactors were kept where they were a strict improvement, and
one regression it introduced was fixed by restoring the older session's
behavior.

---

## Session 1 (shared baseline, both branches identical here)

- **Build warnings** — `DeadlinesScreen.kt` now uses the `AutoMirrored`
  arrow icons; `ScheduleScreen.kt` dropped the unused `onNavigateToMap`
  parameter.
- **Bottom nav bar removed** — `MainActivity.kt` navigation is now
  side-drawer only.
- **Quick Add FAB rebuilt** — rotating "+" → "×" icon, dismiss scrim,
  staggered fan-out, haptics on tap.
- **Home screen widgets not loading** — all three `appwidget-provider`
  XML files were missing `android:initialLayout`; added
  `res/layout/widget_loading.xml` and wired it in.
- **Theme toggle fixed** — `MainActivity` now reads the Compose-observable
  `vm.themeMode` instead of a plain `SharedPreferences`-backed var, so the
  theme icon actually triggers recomposition.

## Session 2 (roadmap items #3–#6 + UI polish C/D — two independent takes, merged)

Both later sessions implemented the same four roadmap items and two
polish items. Where one session's version was a clear improvement over
the other (cleaner architecture, direct SQL updates instead of
copy+upsert, better docs), that version was kept. Where the newer
session had regressed a working behavior from the older one, the older
behavior was restored.

- **#3 — Schedule conflict detection (warn-only)**: `ScheduleScreen.kt`
  recomputes an overlap check live as the day/start/end fields change and
  shows a warning banner ("You can still save — just double check it's
  intentional") — saving is **never blocked**. One of the two sessions
  had reverted this to a blocking check with stale copy; the warn-only
  behavior has been restored here, along with preserving the class's
  `absences` count when editing (a session-2 form rebuild had been
  silently resetting it to 0 on every edit — fixed).
- **#4 — Attendance tracking**: `ClassSession.absences`, Room v2→v3
  bump, and `allowedAbsences()` (UP's 20%-of-meetings drop rule, kept as
  an entity extension function). Uses the newer session's direct
  `incrementAbsence`/`decrementAbsence` SQL queries in
  `ClassSessionDao.kt` (avoids a full row re-upsert from the UI layer),
  its more compact icon-based counter row on `ScheduleScreen`'s
  `ClassCard`, and its Dashboard "Mark absent" quick action.
- **#5 — Spending trends**: `BudgetScreen.kt`'s "Spending Trend" card,
  using the newer session's `Canvas`-based bar chart (highlights the
  current month, floors zero-spend months to a visible sliver) rather
  than the older `Box`/`Row`-based bars.
- **#6 — Backup nudges**: `BackupNudgeWorker` (renamed from
  `BackupReminderWorker`), with `PunlaRepository.lastBackupNudgeAt`
  added so the worker doesn't re-nudge every day just because the backup
  is still stale — a genuine improvement over the older session's
  simpler daily check. The notification still deep-links into Settings;
  a session-2 regression that stopped `MainActivity` from accepting
  `"settings"` as a valid `startRoute` (so the notification tap silently
  did nothing) has been fixed by restoring that condition.

### UI polish

- **C — Cold-start empty-state flash**: kept the newer session's
  `isDataReady` naming and more defensive doc comments; behavior is the
  same as the older session's `initialDataLoaded` — every "No X yet"
  message is withheld until Room's first real emission on every screen
  (Dashboard, Schedule, Budget, Deadlines).
- **D — Haptics on confirm actions**: kept on all the same actions
  (checking off a deadline, saving a class/expense/grade, marking an
  absence).

---

## Session 3 — Free-Time Finder & Budget Low-Balance Notification

Implemented per `free-time-and-budget-nudge-plan.md`, scoped against this
merged codebase.

- **Free-Time Finder**: `ScheduleScreen.kt` gained a pure `freeSlotsFor()`
  function (gaps ≥30 min between a day's classes, within a 7am–8pm
  window) and a `FreeTimeRow` chip row rendered under the day pills in
  list view, using the existing `Tag` component. No DB/schema changes.
- **Budget Low-Balance Notification**: new `BudgetWorker.kt` (mirrors
  `BackupNudgeWorker.kt`'s shape exactly — permission check, notify with
  a `"budget"` deep link, no-op early returns) registered in
  `MainActivity` as a 24-hour `PeriodicWorkRequestBuilder`, same as the
  other workers. `PunlaRepository.kt` gained `lastBudgetNudgeAt`,
  `lastBudgetNudgeThreshold`, and `lastBudgetNudgeMonth` — the last one
  wasn't in the original plan; it's used to naturally reset the
  threshold at the start of a new month (if the stored month doesn't
  match the current one, the threshold is treated as 0) instead of
  needing a separate scheduled reset job. Nudges once at 80%, once more
  at 100%, per month.

- **Logo animation on open**: new `LogoIntroScreen.kt` — the leaf mark
  (reusing `ic_launcher_foreground`, same Ink/LeafLight tones as the
  launcher icon) grows in with a soft spring bounce and fades in, then the
  "Punla" wordmark fades in underneath using `headlineLarge`, which was
  already styled to match the web app's unused `.splash-name` CSS. Wired
  into `MainActivity`'s `setContent` via `Crossfade` and a
  `rememberSaveable` flag, so it plays once per process (a fresh app open)
  but doesn't replay on rotation.

## Not compile-checked

No Android SDK / Gradle access in this environment, so this merge is a
manual read-through + brace/paren balance check, not a real build. Run
`./gradlew assembleDebug` before trusting it fully.

---

## Session 4 — Pomodoro Timer (Phase 1 of POMODORO_STUDY_HABITS.md)

- **`StudySession` entity + `StudySessionDao`**, registered in
  `PunlaDatabase.kt` (`version` bumped 4 → 5, relies on the existing
  `fallbackToDestructiveMigration()`, no `Migration` object needed).
- **`PunlaRepository.kt`**: Pomodoro duration settings
  (`pomodoroWorkMinutes`/`pomodoroShortBreakMinutes`/
  `pomodoroLongBreakMinutes`/`pomodoroCyclesBeforeLongBreak`/
  `pomodoroAutoStartNext`) and `StudySession` log passthroughs
  (`observeStudySessions`/`logStudySession`/`deleteStudySession`).
- **`PunlaViewModel.kt`**: deadline-based timer state machine
  (`PomodoroPhase`/`PomodoroUiState` in new `ui/pomodoro/PomodoroState.kt`)
  — start/pause/resume/stop, auto-advance through WORK → SHORT_BREAK/
  LONG_BREAK, logs a `StudySession` on natural completion or on an early
  stop past 60s, notifies via a new `"punla_pomodoro_channel"` (same
  lazy-create-channel + `POST_NOTIFICATIONS` guard pattern as
  `ClassReminderWorker`), tapping the notification deep-links to the
  Focus screen. `onCleared()` now cancels the timer job.
- **`PomodoroScreen.kt`** (new): course picker (locked while running),
  countdown ring, phase label, cycle-progress dots, Start/Pause/Resume/Stop
  controls — wired as a new `"pomodoro"` drawer item ("Focus") and NavHost
  route in `MainActivity.kt`.
- **Dashboard entry point**: a "Start a focus session" card in
  `DashboardScreen.kt`, right after the greeting card.
- **Settings**: a new "POMODORO" card in `SettingsScreen.kt` for editing
  the three durations, cycle count, and auto-start-next toggle.

Not yet built: Phase 2 (Study Habits streaks/goals) and Phase 3 (Session
Analysis screen) from the same guide — only Phase 1 (the timer itself) was
in scope for this pass.

### Also checked this session
- **App-open greeting**: `LogoIntroScreen` (Session 3) is already wired
  correctly into `MainActivity`'s `setContent` — it plays once per cold
  launch via a `Crossfade` before `PunlaApp`. Nothing was broken here; if
  it's not showing on a device, the most likely cause is running a build
  from before Session 3 rather than a wiring bug.

---

## Session 5 — Weekly Budgeting (`WEEKLY_BUDGET_INSTRUCTIONS.md`)

- **`Expense` entity**: new `isFixed: Boolean = false` (rent, tuition,
  subscriptions — excluded from weekly discretionary totals and the
  weekly pace calc; monthly figures are unchanged and still include
  fixed expenses). Also added to `ExpenseRule` so a recurring rule's
  auto-generated occurrences (`RecurrenceEngine.kt`) inherit the flag,
  not just the first manually-entered instance. `PunlaDatabase.kt`
  bumped 5 → 6, relies on the existing `fallbackToDestructiveMigration()`.
- **`PunlaRepository.kt`**: new `BudgetPeriod` enum (`WEEKLY, MONTHLY,
  BOTH`), defaulting to `MONTHLY` so an upgrading install sees no change
  until it opts in — same reasoning as `BackgroundStyle`'s `AMBIENT`
  default. New settings: `budgetPeriod`, `weekStartDay` (default Monday),
  `weeklyBudgetOverride` (nullable — null means auto-derived),
  `weeklyRolloverEnabled` (default off). New pure functions
  (`currentWeekStart`, `weeklyBudgetDerivedFromList`,
  `weeklyRolloverCarryFromList`, `weeklyBudgetAmountFromList`,
  `weeklyBudgetSpentFromList`, `sumInRange`) shaped like the existing
  `nextClassFromList()`-style helpers — the Budget screen calls them
  directly against its already-collected Room `Flow`, and the widget's
  suspend wrappers (`weeklyBudgetAmount()`/`weeklyBudgetSpent()`) fetch
  the list once and call the same functions, so the two never compute it
  two different ways.
- **`PunlaViewModel.kt`**: Compose-observable mirrors and update
  functions for the four settings above, each refreshing widgets on
  change (same pattern as `updateBackgroundStyle`).
- **Settings screen**: new period picker (reusing `BackgroundStyleOptionRow`
  rather than a new composable), a "Week starts on" dropdown (reusing
  `PunlaDropdownField`), an optional weekly-budget override field, and a
  rollover switch — all in the existing Planner Config card, shown only
  when the period isn't monthly-only.
- **Budget screen**: a "REMAINING THIS WEEK" card (extracted into a
  shared `RemainingCard` composable, also used to keep the monthly card
  pixel-identical to before) renders above "REMAINING THIS MONTH" per
  the plan's combined framing, each conditioned on the period setting. A
  weekly pace card sits above the existing ₱/day monthly pace card, using
  the plan's %-of-budget-vs-%-of-week-elapsed framing instead. The
  expense form gained a "Fixed / recurring bill" checkbox, and fixed
  expenses show a small "Fixed" tag in the list.
- **Widget**: shows the weekly figure, monthly figure, or both stacked
  ("₱850 left this week" / "₱2,100 left this month") depending on the
  period setting; the weekly fetch is skipped entirely when the period
  is monthly-only, so nothing changes for the common case.
- **`BudgetWorker.kt`**: split into `checkMonthly()`/`checkWeekly()`,
  run independently based on the period setting, reusing the same
  notification channel — new `lastWeeklyBudgetNudgeThreshold`/
  `lastWeeklyBudgetNudgeWeekStart` prefs (keyed by week-start date so it
  naturally resets every week) mirror the existing monthly ones. Given
  distinct notification IDs so a monthly and weekly nudge firing in the
  same run don't overwrite each other.
- **`BackupManager.kt`**: `isFixed` added to the `Expense`/`ExpenseRule`
  JSON (de)serialization, and the four new settings added to the backup
  export/restore, so a restore doesn't silently drop them.

### Decisions on the plan doc's open questions
- **Weekly figure editable vs. derived**: implemented both — an explicit
  `weeklyBudgetOverride` if set, otherwise auto-derived from what's left
  of the monthly budget divided by the remaining weeks in the month
  (so overspending early in the month visibly tightens later weeks
  instead of resetting to a flat number each week, per the plan's
  reasoning).
- **Rollover accumulation**: implemented as a stateless, one-week
  lookback (this week's carry = last week's budget minus last week's
  spend) rather than a persisted ledger that could accumulate
  indefinitely across many weeks — closer in spirit to the plan's
  "harder to game" reasoning for defaulting rollover off, and avoids
  needing a background job to "close out" each week.
- **Category treatment**: left the existing fixed category list as-is;
  the plan didn't call for new categories, and `isFixed` is an
  orthogonal per-expense flag rather than a category.
- **Trend chart / category breakdown**: left monthly-only (unchanged).
  The plan doc itself flags a weekly trend view as the lowest-priority,
  "once core numbers are solid" item — out of scope for this pass.

## Not compile-checked

No Android SDK / Gradle access in this environment — this was a manual
read-through plus a brace/paren balance check across every touched file,
not a real build. Run `./gradlew assembleDebug` before trusting it fully.

---

## Session 6 — Free-Time Study Suggestions (Phase 1 of `STUDY_SUGGESTIONS_AND_STREAKS.md`)

Phase 2 of the same guide (streaks/goals) turned out to already be built
— `currentStudyStreak`, the daily-goal progress bar, both already live on
the Dashboard and Study Analysis screen — just with a goal-met definition
of "study day" rather than "any session counts." Left as-is; only Phase 1
(the free-slot suggestion) was actually missing.

- **`ui/pomodoro/StudySuggestion.kt`** (new): pure `suggestStudySlot()` —
  matches a free-slot gap against an upcoming deadline (due 0–3 days out,
  the same window the Dashboard already uses for its red accent). Reuses
  `ScheduleScreen.kt`'s existing `freeSlotsFor()`/`minutesBetween()`
  (changed `private` → `internal`) instead of duplicating the gap math.
  `suggestStudySlotTodayOrTomorrow()` tries today first, falls back to
  tomorrow so a fully-booked today doesn't hide a good gap tomorrow.
- **Dashboard**: the existing "Start a focus session" card (Session 4)
  relabels itself when a genuine match exists — "You have a free slot
  today · 2:00–3:30 · start a focus session for 'Problem Set 3'?" — with
  an X to dismiss for the day. Falls back to the generic card otherwise.
- **Dismiss/snooze**: `PunlaRepository.lastStudySuggestionDismissedAt`,
  same SharedPreferences-timestamp pattern as `lastBackupNudgeAt` — no
  new Room table.
- **Deep link to Pomodoro**: tapping the suggestion navigates to
  `pomodoro?course={code}` with the course pre-picked in the dropdown.
  `PomodoroScreen.kt` gained a `preselectedCourse` param and includes it
  in `courseOptions` even if no class row shares that exact code (a
  deadline's course string doesn't have to match one).
- **Schedule screen**: an optional "Study here?" chip next to the
  free-time row, for people who browse the day view instead of the
  Dashboard card. Wired through a new `onStudyHere` callback param.

## Session 7 — UX Polish: Bottom Bar + Opaque Glass Cards (`UX_POLISH_NAV_GLASS_MOTION.md`)

Scoped to steps 1–3 of the plan's suggested build order (nav switch,
opaque glass treatment). Motion/animation polish (steps 4–5) and a real
blur library for the bottom bar specifically (step 6, contingent on the
plain-tint version not feeling distinctive enough) were left for a later
pass — the latter needs a README pasted in and an explicit dependency
choice per `BRIEFING_AI_AGENTS_NEW_LIBRARIES.md`'s checklist first.

- **Quick check**: `CHANGES.md` Session 1's "Bottom nav bar removed" entry
  has no stated reasoning, confirming the plan doc's own guess — not a
  blocker to reintroducing one.
- **Bottom bar (`MainActivity.kt`)**: the old 6-item `TABS` split into a
  5-item `BOTTOM_TABS` (Home, Schedule, Deadlines, Budget, Grades) shown
  in a `NavigationBar` in Scaffold's `bottomBar` slot, and `DRAWER_ITEMS`
  narrowed to Campus, Checklist, Focus, Settings — the low-frequency
  stuff. Campus already has a Dashboard shortcut card
  (`onOpenNextClassOnMap`, Dashboard Redesign era), which answers the
  plan doc's open question about losing one-tap access. Quick-add FAB
  moved from a manually-aligned overlay `Box` into Scaffold's own
  `floatingActionButton` slot so it auto-offsets above the new bar.
  `currentTitle`/`showBackArrow`/the widget deep-link route check
  (`EXTRA_START_ROUTE`) all updated for the new route split.
- **Opaque glass cards (`PunlaWidgets.kt`)**: new `Modifier.glassCard()` +
  `GlassCard` composable — 0.78 tint alpha (plan's 0.6–0.85 opaque range),
  a vertical-gradient top-edge highlight, the same soft-shadow pattern
  `BudgetScreen.kt`'s `SpendingInsightsCard` already used. Deliberately
  zero new dependencies and no blur, per the plan's own recommendation to
  start there first. Applied to two proof-of-concept spots:
  `SpendingInsightsCard` in `BudgetScreen.kt` (the plan doc's own
  reference card) and the Dashboard's focus-session card (Session 4/6) —
  not rolled out to every card yet, matching the plan's "verify one
  before rolling out everywhere" approach.

## Session 8 — Real Glass Library, Bottom-Nav Animation, Motion Polish
(`GLASSMORPHISM_BOTTOM_NAV_AGENT_BRIEFING.md`, `BRIEFING_AI_AGENTS_NEW_LIBRARIES.md`,
`UX_POLISH_NAV_GLASS_MOTION.md` steps 4–5)

Picks up exactly where Session 7 stopped: the zero-dependency opaque glass
treatment (steps 1–3) was already done; this session does the pieces Session
7 explicitly deferred — the real blur library for the bottom bar specifically,
plus the motion-polish steps.

- **Real LiquidGlass library, per the briefing doc's checklist**: fetched and
  read `https://github.com/Abdullajon1881/LiquidGlass`'s actual README before
  writing any code (not from memory — the briefing's own instruction). New
  `ui/screens/GlassBottomBar.kt` uses the library's real, documented
  primitives — `rememberLiquidGlassProviderState()`, `Modifier.liquidGlassProvider()`,
  `Modifier.liquidGlass()`, `GlassStyle`, `GlassShape`, `GlassRefraction`,
  `GlassHighlight`, `.tinted()` — configured opaque per the briefing's exact
  instruction (minimal blur radius, 0.80 tint alpha, thin top edge highlight,
  saturation/chromatic aberration left neutral so the 5 labels stay legible).
  No tier is forced; `LocalLiquidGlassTier` is left untouched so the library's
  own SHADER/BLUR/SCRIM auto-detection runs as intended.
  - **Two honest gaps, documented in the file itself**: (1) the README names
    a ready-made `GlassBottomBar` component but never publishes its parameter
    signature, and this environment's browser is blocked by GitHub's
    robots.txt from reading the source tree directly — rather than invent a
    signature, the bar is built from the *documented* lower-level primitives
    instead, with a note to swap to the real `GlassBottomBar` later if its
    actual signature is a superset of what's here. (2) the README's code
    samples never show an `import` line, so the package name used
    (`io.github.abdullajon1881.liquidglass`) is inferred from the Maven
    groupId convention, not verified — flagged as the first thing to check
    if a real build reports an unresolved import.
  - **Not yet buildable as-is**: the library's own README states Maven
    Central publishing is configured but not live — consuming it requires
    cloning the repo and running `./gradlew publishToMavenLocal` once.
    `settings.gradle.kts` gained a `mavenLocal()` repository entry and
    `app/build.gradle.kts` gained the dependency line, both commented with
    this requirement. This can't be verified end-to-end in this environment
    (no network access for Gradle here either) — flagging for whoever runs
    the real build, same spirit as this file's existing "Not compile-checked"
    notes.
  - `MainActivity.kt`: one `LiquidGlassProviderState` created per `PunlaApp`
    (`rememberPunlaGlassProviderState()`), applied to the existing
    `appBackground()` Box via `.punlaGlassBackdrop()`; the `bottomBar` slot's
    plain Material `NavigationBar` was replaced with the new `GlassBottomBar`,
    reading the same state. Scaffold's `containerColor = Color.Transparent`
    (already in place) is what lets the glass bar actually sample the
    ambient background behind it.
- **Bottom-nav tab-switch animation (step 5)**: evaluated pulling in a small
  dedicated library (e.g. AnimatedNavigationBar) as the plan doc suggests, but
  decided against it — a generic bottom-nav-animation library ships its own
  bar chrome/shape, which would fight visually with the glass container just
  wired in above. Instead, `GlassBottomBar.kt` hand-rolls a sliding pill
  indicator (tracks each tab's on-screen bounds via `onGloballyPositioned`,
  animates between them with `animateDpAsState` + a bouncy spring) and a
  per-icon selection bounce (`animateFloatAsState`) — native, zero new
  dependency, consistent with the plan doc's own fallback position.
- **Motion polish — checkmark animation**: `DeadlinesScreen.kt`'s done/undone
  toggle icon was an instant swap; now uses `AnimatedContent` with a
  scale+fade transition (bouncy spring on the way in), matching the plan
  doc's own example micro-interaction ("a checkmark animating in on task
  completion") almost verbatim. Native `androidx.compose.animation` only.
- **Not done — `SharedTransitionScope` shared-element morphs (step 4)**:
  the plan doc calls for a class card morphing into its detail/edit view and
  a deadline card expanding into its edit form. Checked this codebase's
  current toolchain against that API's real requirements: `SharedTransitionScope`/
  `sharedElement()`/`sharedBounds()` need Compose Foundation ≥1.7.0
  (compose-bom ≥ 2024.09.00), which in turn needs Kotlin 2.0+ and the
  `org.jetbrains.kotlin.plugin.compose` Gradle plugin — a different model
  from this project's current `composeOptions.kotlinCompilerExtensionVersion`
  pinned against Kotlin 1.9.24. That's a real toolchain migration, not a
  drop-in library add — it touches Room's KSP version, the Firebase BOM
  (currently pinned to 33.1.2 specifically to dodge a Kotlin-metadata
  mismatch against 1.9.24 — see Session 4-era comment in
  `app/build.gradle.kts`), MapLibre's Kotlin compatibility, and
  material-kolor's. Rather than make that call silently inside a UI-polish
  pass, it's left as an explicit, separately-scoped decision — same spirit
  as Session 7 deferring the real blur library until a dependency choice
  could be made deliberately. `ScheduleScreen.kt`'s inline `ClassFormCard`
  (already rendered in the same composition as the tapped `ClassCard`, not a
  separate Dialog/window — good news, this *would* support the shared-element
  pattern once the toolchain allows it) and `DeadlinesScreen.kt`'s edit flow
  are both structurally ready for this the moment that decision is made.

## Session 9 — Revert Real LiquidGlass Bottom Bar

Reverted Session 8's `Abdullajon1881/LiquidGlass` integration — the
publish-to-Maven-local friction (cloning a third-party repo and running
`./gradlew publishToMavenLocal` on every machine that builds this project,
indefinitely, until/unless that library ever publishes for real) was judged
not worth it for what's still a first-pass visual treatment.

Reverted:
- Deleted `ui/screens/GlassBottomBar.kt`.
- `MainActivity.kt`: bottom bar is back to the plain Material `NavigationBar`
  from before Session 8 (imports, provider state, and `appBackground()`
  wiring all removed).
- `app/build.gradle.kts`: removed the `liquidglass-compose` dependency line.
- `settings.gradle.kts`: removed the `mavenLocal()` repository entry.

Net effect: back to Session 7's state — zero-dependency opaque
tint/border/shadow glass cards on Dashboard and Budget, plain `NavigationBar`
bottom bar (Material 3's own built-in selection-indicator animation, no
custom one). The hand-rolled sliding-pill/icon-bounce tab-switch animation
from Session 8 lived inside `GlassBottomBar.kt` itself and went with it —
didn't try to salvage it into the plain `NavigationBar` separately, since
that felt like reopening the same scope this revert is trying to close.
`DeadlinesScreen.kt`'s checkmark pop animation survives untouched — it's in
a different file and never depended on the LiquidGlass library, just plain
`androidx.compose.animation`. If a real blur/glass library is wanted for the
bottom bar later, `skydoves/Cloudy` is on Maven Central already (per
`UX_POLISH_NAV_GLASS_MOTION.md`'s own alternatives list) and wouldn't have
this local-publish problem.

## Session 10 — Full Pass on the GPS System

Covers all three directions from the earlier check-in: fix the
bugs/duplication, make it more reliable, and add a new capability
(continuous tracking). Touches `data/LocationUtils.kt` (the shared
foundation) plus all three of its call sites: `CampusMapScreen.kt`,
`CampusFullMapScreen.kt`, `DashboardScreen.kt`.

**Bugs/duplication fixed:**
- `CampusMapScreen.kt` had its own hand-rolled fused-location-client call
  instead of the shared `fetchOneShotLocation()` — despite that shared
  function's own doc comment claiming it was already used there. Replaced
  with a real call to the shared helper. Its inline permission check also
  only looked at `ACCESS_COARSE_LOCATION`; now uses the shared
  `hasLocationPermission()` (checks either FINE or COARSE).
- `CampusFullMapScreen.kt` kept a private `hasLocationPermission(context)`
  that shadowed the shared one under the exact same name — same logic,
  duplicated. Deleted; it now calls the shared one directly.
- `CampusFullMapScreen.kt`'s FAB recenter-camera-on-tap logic had a real
  latent bug: it read `userLoc` synchronously right after firing the async
  `requestFix()`, so it recentered on the *previous* fix, not the one just
  requested (and did nothing at all on the very first tap, before any fix
  existed). Fixed by moving the recenter into the fix's own result
  callback, gated by a `recenterCamera` flag so it only happens on an
  explicit FAB tap, not on every background update from the new continuous
  stream (see below).

**Reliability improvements (all in `fetchOneShotLocation`, `LocationUtils.kt`):**
- **Timeout**: previously a one-shot fetch could sit in a "Locating…" state
  forever if GPS never resolved (e.g. indoors). Now times out after 15s
  (configurable), cancels the underlying request, and reports
  `LocationFailure.TIMEOUT`.
- **Last-known-location fast path**: `fetchOneShotLocation` now checks
  Play Services' cached `lastLocation` first and delivers it immediately
  (`isLastKnown = true`) if it's under 5 minutes old, so the UI has
  something to show right away instead of waiting on a fresh fix — then
  delivers the fresh fix (`isLastKnown = false`) once it arrives.
- **Distinguishable error reasons**: `onError` used to be a bare `() -> Unit`
  with every screen showing the same generic "Couldn't get your location."
  Now reports `LocationFailure.PERMISSION_DENIED` / `.TIMEOUT` / `.NO_FIX`,
  and all three screens show a message that actually matches what went
  wrong.
- **Permanently-denied permission dead end**: if location permission is
  denied twice (or with "Don't ask again"), Android's permission dialog
  silently stops appearing — the old "Locate me" buttons would just
  re-launch a request that could never show anything. New
  `shouldShowLocationRationale()` + `openAppLocationSettings()` in
  `LocationUtils.kt` let all three screens detect that case and offer a
  direct deep-link into the app's Settings page instead.

**New capability — continuous tracking on the full map:**
- New `rememberLiveLocation()` in `LocationUtils.kt`: continuous
  `requestLocationUpdates`/`removeLocationUpdates`, lifecycle-safe via
  `DisposableEffect`, used only by `CampusFullMapScreen.kt`. Closes a real
  gap there — the on-map "you are here" puck was already continuous (via
  MapLibre's own `LocationComponent`), but the *numbers* next to it
  (distance/ETA to next class, the straight-line polyline) only refreshed
  on initial load or a manual FAB tap, so they could visibly lag behind a
  puck that was still moving. Deliberately **not** used on
  `CampusMapScreen.kt`'s compact list or the Dashboard card — both are
  glanced at rather than kept open while walking, so continuous GPS there
  would just cost battery for no real benefit.

**Not compile-checked** — same caveat as every other session: manual
read-through plus a brace/paren balance check across all four touched
files, not a real build. `fetchOneShotLocation`'s signature changed (added
`isLastKnown: Boolean` to `onResult`, changed `onError` from `() -> Unit` to
`(LocationFailure) -> Unit`) — double-checked all three call sites were
updated to match and grepped for any leftover old-signature call sites;
found none, but worth a real compile to be sure.

## Session 11 — Real Walking Routes + Multi-Stop Path Optimizing

Both directions confirmed in the earlier check-in: real walking directions
along actual paths (not the straight "as the crow flies" line the map used
to draw), and multi-stop ordering for visiting several buildings in one
trip. Builds on Session 10's GPS work — reuses its `fetchOneShotLocation`,
continuous `rememberLiveLocation`, and error-handling patterns rather than
introducing a second, different way of doing things.

**Research done before writing any routing code**: checked whether UPLB's
campus actually has usable path data to route against — OSM's own wiki page
for the university confirms decent building/road mapping coverage, and the
OSRM public demo server (`router.project-osrm.org`) genuinely serves a
`foot` walking profile, free and keyless. No account, no API key, no new
Gradle dependency — matches how the rest of this app (OpenFreeMap tiles,
MapLibre) already avoids vendor lock-in. Its own usage note ("non-commercial
use, ~1 request/second, no uptime guarantee") shaped several of the
decisions below.

**New files:**
- `data/RoutingApi.kt` — `fetchWalkingRoute(from, to)`: calls OSRM's foot
  profile, returns an ordered list of real route points + real distance/
  duration, or `null` on absolutely any failure (offline, demo server down,
  malformed response) — this is the app's first outbound network call it
  makes itself (everything else, Firebase and map tiles, is a library doing
  its own networking), so every caller treats `null` as "fall back to the
  straight line," never a crash.
- `data/RouteOptimizer.kt` — `optimizeStopOrder(from, stops)`: nearest-
  neighbor construction + 2-opt improvement, the standard heuristic
  combination for small practical routing problems. Not exact TSP (that's
  NP-hard and unnecessary at "half a dozen campus buildings" scale) — this
  removes the obviously silly orderings (backtracking, crossing your own
  path) and runs instantly. Deliberately orders using cheap straight-line
  distances rather than real walking-route distances for every pair
  (O(n²) calls against a free rate-limited server for negligible accuracy
  gain at this scale); real routes are only fetched for the n-1 sequential
  legs of the order actually chosen. Also holds the shared `RouteStop` /
  `RoutePlanLeg` / `RoutePlan` types.

**Single-destination routing (Dashboard + full map):**
- Both `DashboardScreen.kt`'s "how far is it" card and
  `CampusFullMapScreen.kt`'s next-class indicator now fetch a real walking
  route and use its distance/duration once it resolves, with the existing
  straight-line haversine estimate as the instant fallback shown before that
  first fetch completes (or permanently, if it never does). Refetches are
  throttled to "moved >20m since the last fetch, or the destination
  changed" — not on every 5-second tick from Session 10's continuous
  tracking, which would otherwise hammer the demo server for a route that
  hasn't meaningfully changed. On a failed refetch, the previous real route
  is kept rather than reverting to a straight line over one network hiccup.
- `CampusFullMapScreen.kt` now draws the real route's actual points as the
  polyline instead of always a 2-point straight line, falling back to the
  straight line when no real route is available yet.
- Fixed a real latent bug found while touching this code: the FAB's
  recenter-camera-on-tap logic read `userLoc` synchronously right after
  firing the async `requestFix()`, so it recentered on the *previous* fix
  (and did nothing on the very first tap, before any fix existed yet).
  Moved the recenter into the fix's own result callback, gated by a
  `recenterCamera` flag so it only fires on an explicit tap, not on every
  background update from the continuous stream.

**Multi-stop path optimizing (new capability):**
- `CampusMapScreen.kt`: each building row now has an add/remove toggle to
  mark it as a stop. A "N stops selected" bar appears with "Clear" and
  "Plan route" — the latter runs `optimizeStopOrder`, then sequentially
  (not in parallel, per the demo server's own courtesy note) fetches a real
  walking route for each leg, packages the result as a `RoutePlan`, and
  opens the full map to show it.
- `PunlaViewModel.kt` gained shared `routePlan` state — a genuine object
  graph (legs, each carrying an already-fetched `WalkingRoute`), not
  something worth serializing into a nav-route string argument, so it lives
  in the ViewModel rather than as a navigation argument.
- `CampusFullMapScreen.kt` renders an active plan as one continuous
  polyline (falling back to a straight line for any individual leg whose
  fetch failed) plus a summary card — ordered stop list, total distance,
  total ETA, and a "Clear route" action — replacing the single next-class
  card while a plan is active.
- `MainActivity.kt`: `CampusMapScreen` now takes `vm` (needed to write the
  plan) — updated its one call site in the nav graph accordingly.

**Not compile-checked** — same caveat as every session: manual read-through
plus a brace/paren balance check across all seven touched/new files
(`data/RoutingApi.kt`, `data/RouteOptimizer.kt`, `ui/PunlaViewModel.kt`,
`ui/screens/CampusFullMapScreen.kt`, `ui/screens/CampusMapScreen.kt`,
`ui/screens/DashboardScreen.kt`, `MainActivity.kt`), not a real build. Also
grepped every new symbol (`fetchWalkingRoute`, `optimizeStopOrder`,
`RoutePlan`/`RoutePlanLeg`/`RouteStop`, `vm.routePlan`/`setRoutePlan`) to
confirm each is defined exactly once and every call site matches its real
signature — found no mismatches, but this is still a genuinely new piece of
this app (its first self-made network call) and deserves a real device test
before trusting it, particularly around what happens with flaky campus
Wi-Fi/data.




## Session 12 — Floating Glass Nav Bar + Fixed Tab-Switch Animations

Two fixes, both scoped to what was already built from `UX_POLISH_NAV_GLASS_MOTION.md`
(the bottom bar and the NavHost transitions) — no new screens, no new
dependencies.

**Bottom bar now floats (`MainActivity.kt`):** the flush, edge-to-edge
`NavigationBar` is now wrapped in a `Box` that insets it from the screen
edges (`navigationBarsPadding()` to clear the system gesture bar, then a
16dp/10dp margin) and applies the existing `glassCard` modifier — the same
opaque tint/edge-highlight/shadow stack already proven out on the
Budget/Dashboard cards — with a taller 8dp shadow so it actually reads as
lifted off the page, and a 28dp fully-rounded pill shape. `NavigationBar`
itself is now `containerColor = Color.Transparent` / `tonalElevation = 0.dp`
/ `windowInsets = WindowInsets(0,0,0,0)` since the wrapping `Box` now owns
both the tint and the inset. Reuses the plan doc's zero-dependency glass
approach rather than pulling in one of the third-party libraries from
`GLASSMORPHISM_BOTTOM_NAV_AGENT_BRIEFING.md` — consistent with the same
build-vs-borrow call already made for the Budget/Dashboard cards.
`glassCard()` gained an `elevation: Dp = 1.dp` parameter (old default
preserved) so the nav bar could ask for a stronger shadow without touching
its existing callers.

**Fixed the tab-switch animation (`MainActivity.kt`):** the single shared
`enterTransition`/`exitTransition`/`popEnterTransition`/`popExitTransition`
set was applying one fixed slide direction to two different kinds of
navigation — switching between the 5 sibling bottom tabs, and a real
hierarchical push into a drawer destination (Settings/Checklist/Campus/
Focus) or a quick-add form. A tab switch always slid the incoming screen in
"from the right," even when the tapped tab sat to the *left* of the current
one, which read as backwards. The set was also asymmetric within itself:
`enterTransition` slid but `exitTransition` didn't; `popEnterTransition`
didn't slide but `popExitTransition` did.

Fix: new `isTabSwitch(from, to)` helper (next to `BOTTOM_TABS`) checks
whether both sides of a navigation are peer bottom-tab routes (a tab's own
quick-add variant counts, since it shares the same base route). All four
transition lambdas now branch on it:
- **Sibling tab ↔ sibling tab**: Material's "fade through" pattern —
  outgoing fades out in place, incoming fades and scales in from 0.98. No
  slide at all, so there's no direction to get wrong for peer destinations
  that don't have a real left/right relationship to begin with.
- **Everything else** (push/pop into a drawer destination or quick-add
  form): a symmetric slide+fade — forward slides in from the right/exits
  to the left, back mirrors it exactly — instead of the old mix of a
  sliding side and a static side.

**Not compile-checked** — same caveat as every session: manual read-through
plus a brace/paren balance check across both touched files
(`MainActivity.kt`, `ui/screens/PunlaWidgets.kt`), not a real build. Worth
confirming on-device that the floating pill's 28dp corner radius and 8dp
shadow don't clip awkwardly against very short device nav-gesture insets,
and that the fade-through tab transition doesn't feel too subtle next to
the more energetic push/pop slide — both are easy one-line tweaks
(shape/elevation values, `scaleIn`'s `initialScale`) if they don't feel
right once seen on a real device.

## Session 12b — Diagnosing "still shows a straight line"

Investigated a report that the map still draws a straight line despite
Session 11's real-routing work. Traced the whole path end to end
(`CampusMapScreen.kt`'s "Plan route" → `RouteOptimizer.kt` →
`RoutingApi.kt` → `CampusFullMapScreen.kt`'s `displayRoutePoints`) and the
logic itself checks out — coordinate order, JSON parsing, and the
plan/next-class/straight-line fallback priority are all correct on a
read-through.

**Two findings:**

1. **`fetchWalkingRoute()` had no logging at all.** By design (see its own
   doc comment) it returns `null` on *any* failure — no connectivity, the
   demo server down/rate-limited, a genuine "no route between these
   points" — and every caller silently falls back to the straight line.
   That's the right behavior for the user, but it also means a real
   failure and "just hasn't fetched yet" looked identical, with nothing
   anywhere to check. Added three `Log.w("RoutingApi", ...)` calls (HTTP
   failure, non-"Ok" OSRM response code, and the exception path) — no
   behavior change, just makes the reason checkable in Logcat if a route
   still comes back straight. This was flagged as untested against a real
   network in Session 11's own notes, so this is the first real way to
   see what's actually happening on-device.
2. **`CampusMapScreen.kt`'s own "View on map" preview (the per-building
   `ModalBottomSheet` → `CampusMapView`) never drew a route line at
   all** — real or straight — before or after Session 11. It only ever
   places a single marker centered on the tapped building. If that's the
   view being tested, "still straight" doesn't apply there; it's a
   separate, not-yet-built gap (that preview was never in scope for the
   routing work — only the full map and the multi-stop plan were).

**Not compile-checked** — same caveat as always: read-through only, one
file touched (`data/RoutingApi.kt`), logging-only change.

## Session 12c — GitHub Actions Build Workflow

Added `.github/workflows/build.yml` so a debug APK can be built entirely on
GitHub's servers and downloaded from the Actions tab — no Android Studio,
no local Gradle/SDK setup needed.

**Note for next session: this repo/zip has no Gradle wrapper.** There's no
`gradlew`, `gradlew.bat`, or `gradle/wrapper/gradle-wrapper.jar` anywhere in
it — only `gradle/wrapper/gradle-wrapper.properties` survived (pins Gradle
8.13). That breaks the usual `./gradlew build` boilerplate everyone copies
into Android CI workflows, and it'll also break a local Termux build the
same way. Worked around it here by having the workflow install Gradle 8.13
directly via `gradle/actions/setup-gradle` and running `gradle
assembleDebug` (no wrapper involved at all). The real fix, whenever there's
a Gradle install handy (Termux, Gitpod, anywhere): run `gradle wrapper
--gradle-version 8.13` once from the project root and commit the three
generated files — after that `./gradlew` works everywhere again and this
workaround stops being necessary (though it'd keep working fine either way).

Also pinned `platforms;android-34` / `build-tools;34.0.0` explicitly via
`sdkmanager` rather than trusting whatever's preinstalled on the runner —
GitHub's `ubuntu-latest` image only guarantees API 34+ tooling going
forward, so this keeps the build from depending on an assumption that could
change under it.

**Not compile-checked** — this one can't really be "compile-checked" any
other way than actually running it, since it's the CI definition itself.
Push it and check the Actions tab; if `sdkmanager --licenses` prompts
interactively instead of accepting the piped `yes`, that's the first thing
to look at.

## Session 13 — Real Walking-Time Route Ordering

Upgraded the multi-stop route planner's ordering from straight-line
(haversine) distance to real walking *time*, prompted directly by a
screenshot of the campus map: three selected stops (Baker Hall Pool,
Graduate School Building, Biological Sciences Building) whose route crosses
the river, where Narra Bridge is the only crossing for a whole stretch —
exactly the kind of geography where "closest as the crow flies" and
"closest to actually walk to" diverge, and straight-line ordering can pick
a genuinely worse sequence than the obvious one.

**`data/RoutingApi.kt`** — added `fetchWalkingMatrix(points)`, using OSRM's
Table service (`/table/v1/foot/...`, same demo server and profile as the
existing `fetchWalkingRoute`) to fetch real walking duration *and* distance
between every pair in one HTTP call, instead of the O(n²) burst that doing
it via `fetchWalkingRoute` per pair would mean. Same "never throws, `null`
on any failure" contract as the rest of this file. A `null` entry in OSRM's
response (a pair it found no path between) maps to a new `UNREACHABLE_COST`
constant (999,999) rather than crashing the parse or leaving a gap.

**`data/RouteOptimizer.kt`** — refactored the nearest-neighbor + 2-opt
search into a shared private `bestVisitOrder(stopCount, cost)`, parameterized
by an arbitrary pairwise cost function, so the same search can be scored two
ways:
- `optimizeStopOrderReal(stops, matrix)` — real seconds from the new
  matrix. **Preferred path.**
- `optimizeStopOrder(from, stops)` — unchanged straight-line haversine
  distance. Same function as before, now just the fallback.

**`ui/screens/CampusMapScreen.kt`** — `planRoute()` now fetches the matrix
first (`listOf(userLoc) + stops`, in that order, so matrix index `0` is the
user and `1..n` line up with `stops[0..]`), uses `optimizeStopOrderReal`
when that succeeds, and only falls back to the old `optimizeStopOrder` if
the matrix call comes back `null` (logged via `Log.w("CampusMapScreen", ...)`
so a silent fallback is checkable in Logcat, same pattern Session 12b added
for `RoutingApi.kt`). Net new network cost per "Plan route" tap: exactly one
extra HTTP call (the table request) — the per-leg `fetchWalkingRoute` calls
for drawing the actual polyline are unchanged, still one per leg, still
sequential.

Deliberately did **not** touch the per-building "View on map" preview
(`ModalBottomSheet` → `CampusMapView`) that Session 12b flagged as never
drawing any route — that's a single-destination view with nothing to order,
unrelated to this multi-stop work.

**Not compile-checked** — same caveat as every session: manual read-through
only. Three files touched (`data/RoutingApi.kt`, `data/RouteOptimizer.kt`,
`ui/screens/CampusMapScreen.kt`), no new dependencies, no Gradle/manifest
changes. Worth an on-device check in particular: OSRM's public Table
service endpoint has a lower default max-locations limit than most people
expect from a demo server — fine for this app's realistic stop counts (a
handful), but if stop lists ever grow into the dozens this will start
failing (gracefully, via the existing fallback) and would need chunking or
a self-hosted OSRM instance.

## Session 14 — Fix Session 13 CI compile failure

Session 13's push failed CI at `compileDebugKotlin` — exactly the "smart-cast
across a coroutine body" class of error `PROJECT_STATUS.md` already warns
about, in a new spot.

**`ui/screens/CampusMapScreen.kt`**, `planRoute()`: `var fromPoint = loc`
was inferring `fromPoint: Pair<Double, Double>?` (nullable) even though
`loc` is non-null at that point (proven by `optimizeStopOrder(loc, stops)`
two lines above compiling fine). A `var` declared without an explicit type
doesn't reliably keep a `val`'s smart-cast narrowing — only `val loc = userLoc`
right after the null-check is the part of this pattern Kotlin guarantees.
Fixed by giving `fromPoint` an explicit non-null type:

```kotlin
var fromPoint: Pair<Double, Double> = loc
```

**Not compile-checked** — same caveat as always, this fix included. If this
specific class of error shows up again anywhere else, the general fix is
the same: any `var` initialized from a smart-cast nullable value should get
an explicit non-null type annotation rather than relying on inference.

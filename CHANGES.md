## Session 35H — Main-thread scheduler merge

- Moved persistent worker scheduling/recovery off the Android main thread.
- Boot/time/package recovery now uses `goAsync()` plus a background coroutine.
- System Health job repair no longer blocks the interaction frame.
- Preserved Phase 0D Battery/Idle, restore-verification, and 7-day stability validation features.
- Removed duplicate Activity-level cold-start scheduling; Application owns the startup repair path.
- Version 2.9.6 (30).

# Session 35D — Scroll-aware animation smoothing

- Replaced the hard animated-background freeze during scrolling with adaptive slow motion.
- Added a continuous virtual animation clock so background motion does not stop/jump around scroll gestures.
- Eases toward ~32% background playback speed while scrolling and back to full speed afterward.
- Caps decorative background publishing to ~12 FPS during active scrolling while preserving the existing idle cadence.
- Clamps large resume-time deltas to prevent atmosphere jumps after app sleep/backgrounding.
- Bumped app version to 2.9.3 (27).
- No database migration and no new dependencies.

# Session 35C — Phase 0B System Health

- Added a local System Health screen for notifications, exact alarms, background restrictions, battery optimization, WorkManager jobs, database integrity, backup freshness, and diagnostics.
- Added in-app diagnostic log viewing, text export, and confirmed clearing.
- Added direct Android settings shortcuts for actionable reliability problems.
- Standard notification channels are now created at application startup so channel health is immediately inspectable.
- Bumped app version to 2.9.2 (26).
- No database migration and no new dependencies.

# Session 35B — Smooth interaction pass

- Isolated the animated procedural background into its own `graphicsLayer` so atmosphere redraws do not re-record the foreground app tree.
- Pauses decorative background animation during active Compose scroll/fling gestures, then resumes afterward.
- Vsync-aligned the existing 15–25 FPS background ticker with `withFrameNanos`.
- Migrated Compose screen Flow subscriptions to lifecycle-aware collection (`collectAsStateWithLifecycle`).
- Moved/serialized Glance widget refresh work off the UI dispatcher.
- Cached Settings background preview rasters and made the theme chooser lazy.
- Memoized palette/color-scheme/typography resolution and simplified sibling-tab transitions to short fades.
- No Room/backup schema change. Version **2.9.1** (`versionCode 25`).

# Session 34g — Campus map compile fix

- Fixed `CampusFullMapScreen.kt` compile failure (`Unresolved reference: context`) in the MapLibre `AndroidView` update block.
- The location-component activation now uses the active `MapView` context (`it.context`), which is guaranteed to be in scope inside `AndroidView.update`.
- No feature or database-schema changes.
- Version bumped to **2.8.3** (`versionCode 23`).

# Session 34 — Course Learning Path

- Bumped app to **2.8.0 / versionCode 20**.
- Upgraded the Study Hub so every course can follow a consistent learning sequence:
  - Module Review → Module Flashcards → Module Quiz
  - then Overall Review → Overall Flashcards → Comprehensive Quiz.
- Top-level Study Topics now act as ordered course modules; nested topics remain subtopics inside the module.
- Added persistent reviewer-completion tracking without locking later study steps.
- Flashcard decks and quizzes can now be attached to a module (`topicId`) or left course-level for Overall Review.
- Added module-scoped opening for Flashcards and Quizzes from Study Hub.
- Added manual module assignment in Flashcard Deck and Quiz editors.
- Creating or importing decks/quizzes from a module now keeps that module/course scope by default; quiz-from-flashcards also stays inside the active module.
- Added module/topic ordering (`sortOrder`) and migration **v11 → v12**.
- Updated backup/restore to preserve module links, topic order, and reviewer completion.
- Extended study-pack JSON import with optional `topicKey` on flashcard decks/quizzes and `sortOrder` on topics while keeping schemaVersion 1 backward compatible.

---

# Session 33b — Study System 3.0 build fix

- Fixed missing ArrowForward, Image, and layout.height imports.
- Fixed nullable QuizQuestion editor initialization accesses.
- Bumped app to 2.7.1 / versionCode 19.

# Punla changes

## v2.6 — Crash-safe JSON imports

- Reworked Flashcard JSON imports into an awaited, atomic flow instead of fire-and-forget database jobs.
- Room/import failures are now caught and shown as an in-app error; failed transactions roll back instead of terminating Punla.
- The UI opens an imported deck only after the database transaction has actually succeeded.
- Import buttons lock while a save is running, preventing double-taps from starting overlapping transactions.
- Added a bounded JSON reader so Punla never reads an arbitrarily large selected file into memory before applying its size limit.
- Flashcard and Quiz JSON reading/parsing now runs off the main thread.
- Hardened duplicate-content history checks so storage/query failures surface as recoverable import errors.
- Applied the same transaction/error protections to Quiz JSON imports to avoid the same crash class there.
- No Room schema change; existing decks, quizzes, attempts, and import IDs remain compatible.
- App version: `2.6` (`versionCode 17`).

## v2.5 — Atmospheric Background Engine 2.0

- Rebuilt Rain with gravity-dominant motion and stable per-drop x anchors so the scene no longer feels like the camera is moving through the weather.
- Added explicit far/mid/near rain layers, tiny independent breeze wobble, sparse near-drop splashes, and a subtle stationary haze layer.
- Rebuilt Aurora from thick stroked curves into four broad filled gradient curtains with independently deforming top/bottom edges and soft inner glow.
- Slowed and softened Ocean Waves, Fireflies, Sakura, Snow, Bubbles, and Starfield motion so animated backgrounds feel atmospheric rather than like a moving viewport.
- Preserved the shared renderer used by the live app, Settings previews, and frozen widget frames; no new dependency or database migration.
- App version: `2.5` (`versionCode 16`).

## v2.4 — Flashcards 2.0 + Quiz Maker

- Upgraded Flashcards with cloze cards, reverse-direction study, tags, starred cards, and Smart Study filters for Due / Weak / New / Starred / All.
- Added strict flashcard JSON v2 export/import with `punla.flashcards.deck`, schema versioning, UUID content IDs, re-import warnings, and exact-card duplicate skipping.
- Added **Quizzes** as a drawer destination with manual quiz/question editing, multiple choice, true/false, identification, passing score, shuffling, explanations, scoring, attempt history, and retry-mistakes flow.
- Added **Create quiz from flashcards** and **Make flashcards from quiz mistakes**.
- Added strict quiz JSON v1 import/export with `punla.quiz` and UUID content IDs.
- Added `punla.backup` to newly exported full backups so the three JSON import surfaces can reject files intended for another Punla feature. Older backups remain readable.
- Added Room migration 9→10 for flashcard metadata, quizzes, attempts, and JSON import records.
- Backup format v6 now preserves upgraded flashcards, quizzes, quiz questions, attempts, and imported-content IDs.
- App version: `2.4` (`versionCode 15`).

# Punla 2.3 — Flashcard JSON Import

- Added JSON flashcard-deck import through Android's document picker.
- Added import preview and readable validation errors.
- Added canonical `punla-flashcards` format v1 for ChatGPT-generated decks.
- Supports common field aliases and bare card arrays.
- Importing from the library creates a new deck; importing from a deck adds cards there.
- Imported cards always start as fresh reviews; external mastery/history values are ignored.
- Added import size/card-count guardrails and invalid-card skipping.
- Added atomic deck + card insertion for new-deck imports.
- Fixed a duplicate `tint` argument in the flashcard hero icon found during validation.
- Version bumped to 2.3 (`versionCode 14`).

# Punla Changes

## Session 28 — Flashcard Maker (v2.2)

- Added an offline-first Flashcards destination with deck creation, optional course labels, descriptions, and deck-level progress.
- Added manual card create/edit/delete and deck search.
- Added bulk card import using `front :: back` or tab-separated lines.
- Added a focused study mode with answer reveal and `Again`, `Hard`, `Good` ratings.
- Added lightweight spaced repetition and per-card mastery/review metadata.
- Added due-card counts plus `Study due` and `Study all` flows.
- Added Room entities/DAO and migration `8 -> 9`; deck deletion cascades safely to its cards.
- Added flashcards to Punla backup/restore; backup format version is now 5.
- Added scheduler unit tests.
- Bundled the GitHub Actions fixes for the Compose `animateColorAsState` import/dependency and nullable `initialExpense?.ruleId`.
- Version bumped to `2.2` (`versionCode 13`).


## Session 27 — UI/UX 2.1

- Added responsive, centered content gutters across primary screens so tablet/foldable and landscape layouts no longer stretch cards edge-to-edge.
- Navigation rail now activates at 600 dp, while compact phones retain the floating bottom navigation.
- Switched the root app bar from centered to left-aligned for better title/action balance.
- Redesigned shared section headers with clearer sentence-case hierarchy and optional contextual actions.
- Dashboard section headers now link directly to Schedule, Budget, and Deadlines.
- Added actionable empty states for Schedule, Budget, Deadlines, Grades, and Checklist.
- Replaced ambiguous plus-only FABs with labeled extended FABs on Budget, Deadlines, Grades, and Checklist.
- Removed duplicate in-screen titles from Settings, Checklist, Study Analysis, Assistant, and Campus.
- Added animated segmented-control and day-pill selection transitions.
- Normalized Dashboard stat-tile heights for cleaner scanning.
- Version bumped to `2.1` (`versionCode 12`).

## Session 26 — Budgeting System Upgrade (v2.0)

- Added a **Safe to Spend Today** planner that uses the tighter active weekly/monthly limit and spreads the remaining discretionary budget across the days left in the period.
- Monthly safe-spend calculations now reserve upcoming **fixed recurring commitments** before treating money as discretionary.
- Added optional **monthly category limits** for Food / Allowance, Transportation, Mobile Load / Internet, Supplies, Org / Activities, and Miscellaneous. Category cards now show amount left or amount over the configured cap.
- Added **expense editing** for amount, category, note, date, and fixed status.
- Added **backdated expense logging** with Today/Yesterday shortcuts and future-date validation.
- Editing a generated recurring occurrence changes only that occurrence; the future recurrence rule is preserved.
- Backdated recurring rules now catch up immediately after creation instead of waiting for the next app launch.
- Home and the tall Budget widget now surface the safe-to-spend figure.
- Budget threshold notifications now include actionable daily-pace guidance and expanded text.
- Category limits are preference-backed (no Room migration) and are included in backup/restore. Backup format version is now 4.


## v1.9 — Notification system upgrade

- Centralized notification channels, groups, stable IDs, and routine quiet-hour policy.
- Fixed cross-feature notification ID collisions and moved push notifications into the shared ID/channel policy.
- Kept the ongoing class-day card silent while restoring one intentional 15-minute class-start alert with Schedule/Navigate actions and 20-minute expiry.
- Added a quiet 7:15 AM Morning agenda with today's class count, first room/time, and deadlines due today; it is deduplicated once per date.
- Added Settings controls for Morning agenda and 10 PM–7 AM Quiet hours, plus a shortcut to Android's per-category notification controls.
- Quiet hours suppress routine checklist, budget, backup, and daily-brief nudges while preserving class, Pomodoro, and deadline alerts.
- Learned routine reminder hours are kept outside quiet hours.
- Deadline alerts remain deduplicated until the urgent deadline snapshot changes.
- Ongoing class cards prioritize at most three useful actions for the current state.
- Backup/restore now preserves Morning agenda and Quiet hours preferences.
- Validation: 85 Kotlin files parsed with zero syntax errors; notification policy compiled against local stubs and passed quiet-hour/ID tests.

# Version 1.8 — Schedule Today Auto-Focus

- Schedule List now resets to the current weekday whenever the destination is reopened, even when bottom-navigation state restoration retained an older day.
- Automatically scrolls to the class happening now.
- Before or between classes, automatically scrolls to the next class today.
- After the final class, opens at the last class and labels the day complete.
- Adds clear `HAPPENING NOW`, `UP NEXT`, and `DAY COMPLETE` emphasis without changing the user's saved schedule.
- Manual browsing remains stable: attendance changes and Room emissions no longer pull the list away after the initial focus request.
- Added pure schedule-focus regression tests for boundaries, empty days, adjacent classes, and Sunday fallback behavior.

# Version 1.7 — Attendance Check-In

- Added **Attended** and **Absent** actions to the ongoing class notification.
- Attendance remains available for the class that just ended during break and end-of-day states.
- Added deterministic per-occurrence attendance records, preventing duplicate rows from repeated notification taps.
- Switching Absent -> Attended automatically reverses the class absence tally; switching Attended -> Absent increments it once.
- Added today's attendance controls and per-class attended/absent totals to Schedule.
- Replaced Dashboard's one-way “Mark absent” shortcut with Attended and Absent choices.
- Added Room database migration 7 -> 8 and backup format v3 support for attendance history.
- Deleting a class also deletes its linked attendance history.

# Version 1.6 — Ongoing Class-Day Notification

- Added one silent notification that evolves through pre-class, ongoing class, between-class break, and end-of-day states.
- Added Android chronometer countdowns for class start/end without minute-by-minute background work.
- Added Navigate, Schedule, Start focus, and Hide today actions.
- Added a dedicated Settings toggle and backup support.
- Added WorkManager boundary scheduling plus 15-minute recovery checks.
- Added pure timeline regression tests.

---

# Punla Android — Change Log

## Session 21 — Procedural background engine

- **Shared renderer** — introduced `paintBackgroundFrame()` as the single drawing dispatcher used by the live Compose app, Settings previews, and frozen Glance widget frames.
- **Seven new choices** — added Theme Match, Aurora, Ocean Waves, Fireflies, Sakura, Snow, and Bubbles alongside the existing Minimal, Ambient, Rain, Starfield, and Paper Grain styles.
- **Theme Match** — added intentional signature mappings for all 16 curated/custom themes without changing the previous Ambient default for existing installs.
- **Rain upgrade** — replaced uniform streaks with depth-based speed, alpha, width, length, brighter heads, wind drift, and tiny bottom-edge splashes.
- **Static picker previews** — every Settings option now shows a representative frozen thumbnail without running twelve animations at once.
- **Performance discipline** — no new runtime dependency, video, WebView, or game engine; only the selected animated style owns an animation clock, while Minimal and Paper Grain remain static.
- **Widget parity** — widgets now resolve Theme Match with the active theme and render the same effect code as the app.
- **Tests and attribution** — added `BackgroundEngineTest.kt`, implementation notes, and Apache-2.0 third-party notices for the reviewed `skydoves/compose-animations` rain sample.
- **Version** — bumped to `1.5` (`versionCode 6`).

---

## Session 20 — Canonical room and building locations

- **Complete canonical map snapshot** — replaced the 39-marker legacy directory with all 52 current buildings from `uplbtools/room-tba`.
- **Coordinate corrections** — updated every retained marker to the reviewed canonical coordinates, including PTCF, LHKCB, Hydraulics, IABE, ASI, IE, Graduate School, AFBED, ICropS, IRNR, CEM, CHE, Physical Sciences, and the remaining lower-drift locations.
- **Room overrides** — moved ABC rooms to AMPED, PSLH rooms to Physical Sciences, ASR/ASLH rooms to Animal Husbandry, MB rooms to New Math, HL rooms to Hydraulics, Fronda rooms to Fronda Hall, and split veterinary and Forestry rooms into their specific buildings.
- **CHE/ChE resolver fix** — numbered Chemical Engineering room aliases now resolve to Chemical Engineering while Human Ecology rooms such as `CHE MPH` remain at CHE.
- **Conservative matching** — removed unsafe prefix guessing. Exact aliases are preferred, punctuation-tolerant matching is used only when unambiguous, and `TBA`, `Online`, and incomplete room codes remain unresolved.
- **Source traceability** — bundled the repository/blob metadata and the reviewed audit date in `CampusDirectory`.
- **Regression tests** — added `CampusDirectoryTest.kt` for canonical marker count, coordinates, reviewed overrides, collision prevention, and unresolved-room behavior.
- **Version** — bumped to `1.4` (`versionCode 5`).

---

## Session 19 — Full theme collection

- **All 14 documented themes** — added Aurora Borealis, Sunset Sky, Ocean Depths, Forest Mist, Lavender Night, Golden Dawn, Coffee Shop, Lo-fi Night, Paper & Ink, Library Mode, Cyber Neon, Pastel Bloom, Frost Glass, and Galaxy using the exact colors in `PUNLA_THEME_COLLECTION.md`.
- **Richer Appearance picker** — replaced the small legacy swatches with theme preview cards showing the palette, intended mode, category, description, and selected state.
- **Accessible accent labels** — buttons, chips, and other filled accents choose the higher-contrast theme text role, preserving soft pastel palettes without sacrificing readability.
- **Light/dark override support** — every curated theme keeps its exact intended-mode palette and receives a derived readable companion palette for the opposite mode.
- **Widget/background integration** — home-screen widgets, Ambient, Starfield, Paper Grain, and Rain continue resolving from the same selected palette.
- **Saved-theme migration** — legacy `ocean`, `sunset`, `orchid`, and `slate` preferences map to Ocean Depths, Sunset Sky, Lavender Night, and Paper & Ink.
- **Regression tests** — added `ThemeCollectionTest.kt` to verify catalog completeness and all 84 documented color values.
- **Version** — bumped to `1.3` (`versionCode 4`).

---


## Session 17 — Pomodoro PiP, background alarm, and custom sounds

- **Picture-in-Picture timer** — an optional compact countdown appears when a running Pomodoro leaves the foreground. Android 12+ uses smooth auto-enter; Android 8–11 enters PiP from the Home/app-switch gesture.
- **Background-safe completion alarm** — every running phase schedules an `AlarmManager` deadline, so focus and break alerts still fire after the activity or process is removed. Pausing/stopping cancels it, resuming schedules a new deadline, and reboot/app-update recovery restores it.
- **Exact-alarm fallback** — Settings shows whether Android's *Alarms & reminders* access is granted. Punla uses exact while-idle delivery when allowed and an inexact while-idle fallback otherwise.
- **Custom sounds** — Focus-complete and Break-complete sounds can be selected independently from the device ringtone picker. Sound and vibration can also be disabled separately.
- **Duplicate protection** — the visible clock and background receiver share one completion coordinator, preventing duplicate session rows or duplicate alerts when both wake together.
- **Version** — bumped to `1.1` (`versionCode 2`) for install-over-update compatibility.

---
This build merges two parallel sessions that both started from the same
base and independently implemented the same roadmap items. Rather than
keep two divergent copies, this is one consolidated codebase: the newer
session's refactors were kept where they were a strict improvement, and
one regression it introduced was fixed by restoring the older session's
behavior.

---


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

## Session 15 — UI/UX Safety, Validation, State, Permissions, and Adaptive Navigation

Implemented the highest-impact remaining UI/UX improvements across the main student workflows:

- Added shared inline validation support to `PunlaField`, including error semantics for screen readers and minimum 48 dp field height.
- Added validation for required class/course/semester/checklist/deadline fields, positive expense and unit values, non-negative budgets, valid deadline dates, and trimmed saved text.
- Added unsaved-change protection to class, expense, deadline, semester, course, and checklist forms.
- Added consistent confirmation dialogs before deleting classes, expenses, deadlines, courses, semesters, and checklist items; checklist reset now uses the same destructive-action pattern.
- Converted key screen and form state to `rememberSaveable` so selections, open forms, entered values, and confirmation state survive rotation/process recreation.
- Replaced the immediate Android 13+ notification permission prompt with a contextual explanation, and made Settings reflect/request the real Android permission.
- Increased shared segmented-control, day-pill, dropdown, and text-field touch targets.
- Added adaptive Material 3 navigation: phones keep the five-item bottom bar, while screens 840 dp and wider use a navigation rail.

Validation: every Kotlin source file was parsed through Kotlin PSI with no syntax errors. A full Android build was not available in the editing container because Gradle and the Android SDK are absent; GitHub Actions remains the final compile check.

## Session 16 — Local Intelligence, Assistant, Precise Location, and Learned Reminders

Implemented the staged personal-intelligence plan as a local-first extension of Punla's existing Room data.

### Data and migration
- Bumped Room to v7 with an explicit v6→v7 migration instead of destructively recreating current personal data.
- Added `endReason` and `suggestionId` to `StudySession`.
- Added append-only `StudySuggestionEvent` and `NotificationEvent` tables plus `IntelligenceDao`.
- Upgraded backups to version 2 so event history, learned model state, term dates, dismissed patterns, and learned reminder hour survive export/import. Encrypted API keys remain excluded.

### On-device intelligence
- Added pure pattern functions for study-hour completion, early stops, recurring expenses, attendance projections, notification engagement, and best study hour.
- Added heuristic free-slot ranking and a hand-written online logistic-regression layer with sparse-data fallback and persisted weights.
- Added pattern cards to Study Analysis and Budget, and projected attendance warnings to Schedule.
- Added configurable term dates and reset controls to Settings.

### Local-first assistant
- Added a drawer-level Assistant screen.
- Local commands answer schedule, deadline, budget, attendance, and study questions without network access and can prepare focus/expense actions.
- Added an optional Claude Messages API fallback, off by default, using an Android-Keystore-encrypted user key, compact context, prompt-cache marker, error fallback, and a 10-call daily cap.

### Learned notifications
- Added tracked open/dismiss intents to existing reminder notifications without adding alarms or workers.
- Settings surfaces the best-performing hour after enough events and can align daily deadline/budget/checklist work to it. Urgent class reminders remain unchanged at 15-minute cadence.

### Precise location
- Restored Android's Approximate/Precise choice by requesting `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` together from every location entry point.
- Added `Enable precise` prompts when only coarse permission is available.
- High-accuracy fused location is used only when fine permission is granted; approximate remains a working fallback.

- Hardened the final integration pass: notification taps now launch the app directly instead of using a blocked broadcast-to-activity trampoline; cloud context is selected by query; study recommendations skip elapsed time slots; model positives are recorded only when the focus timer actually starts; early-term attendance smoothing no longer warns with zero absences.

### Validation
- Kotlin PSI syntax check passed for the full source tree.
- Pure pattern/model logic compiled and executed successfully.
- AndroidManifest XML and `git diff --check` passed.
- Added JUnit tests for pattern functions and the online predictor.

A full Android build was not run because this editing environment lacks Gradle and Android SDK 34; the included GitHub Actions workflow remains the final compile check.

## 2026-08-02 — Pomodoro background/resume fix
- Persisted the active Pomodoro phase, wall-clock deadline, cycle count, and course locally.
- Restored and resynchronized the countdown after app switching, notification-shade interruptions, or Android process recreation.
- Showed the configured short/long-break duration while the next phase is waiting to be started instead of displaying `00:00`.
- Captured completed-session values before auto-starting the next phase so history rows keep the correct start time and cycle number.


## Session 18 — Reliable PiP and live timer notification

- Added an ongoing, silent notification with a system-managed countdown while a Pomodoro phase is running.
- Added a Pomodoro setting to enable or disable the live timer notification.
- Added a direct shortcut to Android's per-app Picture-in-Picture permission screen.
- Added `onPictureInPictureRequested()` fallback handling alongside Android 12+ auto-enter.
- Restores the live timer notification after app/process recreation, device reboot, or app update.
- Cancels the live notification when paused, stopped, or completed.

## Session 34c — Quiz attempt save lifecycle fix
- Fixed quiz-completion saves cancelling themselves when `attemptSaving` triggered recomposition.
- Retry Save now explicitly restarts a stable save effect.
- Coroutine cancellation is rethrown instead of being surfaced as a database-save error.

## Session 34d — Quiz back-navigation crash fix
- Fixed a crash when leaving the completed quiz/result screen with **Back to quiz**.
- Removed transition-time force unwraps of `runRequest` and `selectedQuiz` inside the quiz `Crossfade`.
- Also protects the quiz-detail → quiz-library back path from the same animation/recomposition race.

## Session 34e — Study flow debug hardening
- Fixed the flashcard study/deck back-navigation crash caused by force-unwrapping `selectedDeck` during a Compose `Crossfade` transition.
- Moved completed quiz persistence into `viewModelScope` so navigating away from results cannot cancel a valid save.
- Added attempt-id idempotency before quiz side effects, preventing rotation/recomposition from double-counting mistake history or study-goal progress.
- Added a direct `QuizDao.getAttempt()` lookup used as the transaction idempotency guard.
- Retains the Session 34c save-lifecycle fix and Session 34d quiz back-navigation fix.
- Bumped app version to 2.8.1 (versionCode 21).


## Session 34f — Full debug hardening
- Bumped app to **2.8.2 / versionCode 22**.
- Replaced Room `REPLACE`-style upserts with `@Upsert` so editing parent rows no longer risks foreign-key cascade data loss.
- Kept quiz saves alive outside result-screen composition and made quiz-attempt side effects idempotent by attempt ID.
- Removed quiz and flashcard transition-time null crashes and remaining force-unwrapped nullable references in the main source.
- Hardened complete-backup restore: all major IDs, relationships, dates/times, numeric values, quiz/study metadata, topic hierarchies, attendance, recurrence metadata, and preference values are validated before the destructive Room transaction begins.
- Backup format is now v9 and preserves theme preset, custom color, background style, font choice, study goals, and Pomodoro preferences while continuing to exclude encrypted API-key storage.
- Added Android backup/data-extraction exclusions for the encrypted secret preference file and disabled cleartext traffic.
- Made recurring-expense/deadline and semester multi-write operations transactional; runtime recurrence now ignores invalid/corrupt cursors, priorities, and intervals instead of propagating bad state.
- Hardened numeric inputs/calculations against NaN/Infinity and oversized Float-backed preference values.
- Hardened Study Pack module hierarchy import against unknown parents, self-parenting, and cycles, and surfaces unknown material-module links as warnings.
- Converted repeated widget/notification/Quick Add navigation to one-shot request tokens so the same route can be invoked repeatedly.
- Audited broadcast receivers: async receiver work uses `goAsync()`, and the boot/update receiver is exported for system broadcasts while internal action/alarm receivers remain non-exported.
- Fixed the duplicated `studyNotes` restore declaration and duplicate question-bank validation introduced during the debug pass.
- Final source validation: Kotlin delimiter/string/comment structural scan passed; Android XML and bundled JSON parse cleanly; representative Room v11→v12 migration SQL passed; no main-source `!!` or `OnConflictStrategy.REPLACE` remains.
- A full Android/KSP compile still requires the Android SDK/Gradle dependency environment (GitHub Actions is the final compile/install check).

## Session 35 — Phase 0A Stable Core
- Bumped app to **2.9.0 / versionCode 24** and started roadmap Phase 0 reliability work.
- Added guarded Study Hub Room streams plus bounds-safe Smart Study queue access so recoverable data/transition failures do not crash the entire hub.
- Added a persisted one-time WorkManager fallback for every Pomodoro deadline while retaining AlarmManager as the punctual completion path; both converge on the existing idempotent completion coordinator.
- Added opt-in schedule-based attendance auto-log after a 10-minute grace period. Manual Attended/Absent records always win, and the setting survives Punla backup/restore.
- Added an app-private rotating diagnostic log plus uncaught-crash capture, with the diagnostic file excluded from Android backup/device transfer.
- Room remains at database version 12; no schema migration is required.

## Session 35F — Phase 0C Recovery Hardening (v2.9.4)

- Centralized all persistent WorkManager registration in `CoreReliabilityScheduler` so app startup, restore, reboot, and package-update paths cannot drift apart.
- Boot/package-replaced/time/time-zone broadcasts now repair Punla's background schedules and restore an active Pomodoro deadline.
- Added System Health **Background execution** and **Reboot recovery** probes for real-device Phase 0 validation.
- Missing WorkManager jobs can now be repaired directly from System Health.
- Backup restore now runs SQLite `quick_check` and `foreign_key_check` inside the Room transaction before commit; failed verification rolls the restore back.
- Successful backup restore immediately re-syncs persistent workers with restored settings.
- Carried forward the System Health `Modifier.weight()` compile fix.
- Room remains v12; backup format remains v9.

## Session 35G — Phase 0D Validation Harness (v2.9.5)

- Added a 20-minute Battery / idle reliability probe to System Health for real-device screen-off, Battery Saver, and OEM background testing.
- The idle probe reports whether ordinary WorkManager execution completed near its target, completed late, or remained blocked/delayed.
- Backup restore now writes a metadata-only verified restore receipt after Room integrity checks and preference restoration succeed; System Health surfaces the latest verified receipt and restored record counts.
- Added a persistent seven-day Phase 0 stability soak tracker. It records a crash-counter baseline and automatically flags uncaught crashes during the soak.
- Uncaught process crashes now increment a tiny local monotonic crash counter in addition to the existing rotating diagnostic log.
- No Room migration and no backup-format change. Room remains v12; backup remains v9.

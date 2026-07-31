# Punla Local Intelligence Implementation

This implementation adds a personal, local-first intelligence layer to Punla. It is designed for one private user: pattern calculations and prediction stay on-device, while the optional conversational cloud fallback remains disabled until explicitly enabled in Settings.

## Implemented layers

### 1. Data-preserving foundation
- Room schema upgraded from version 6 to 7 with an explicit migration.
- Existing study history is preserved and backfilled with `COMPLETED` or `STOPPED_EARLY` end reasons.
- Study sessions can link back to the suggestion that started them.
- New append-only tables record study-suggestion outcomes and notification interactions.
- JSON backups now preserve the new event history, learned model, term dates, and reminder-hour setting. The assistant API key is never exported.

### 2. On-device pattern engine
`ml/UserPatterns.kt` contains pure, unit-testable functions for:
- study completion rate by hour;
- early-stop rate;
- normalized recurring-expense detection;
- calibrated attendance velocity and projected term risk that stays quiet with zero early-term absences;
- notification open rate by firing hour;
- best logged study hour.

Fresh or sparse histories fall back to the app's existing behavior. Study-hour ranking starts after 10 sessions, recurring suggestions require three stable occurrences, notification learning waits for five firings, and the predictive model stays hidden until 50 labeled outcomes exist.

### 3. Smarter study-slot ranking
- Free slots are ranked by study-hour history, deadline urgency, and duration fit.
- A small online logistic-regression model updates after an actual suggested-timer start or a dismissal; merely opening the timer is not counted as success.
- Hour is encoded cyclically, so 11 PM and midnight remain close in feature space.
- Model weights persist locally and can be reset from Settings.
- The UI shows qualitative labels such as `Good match`, never false-precise percentages.

### 4. Existing-screen insights
- **Dashboard:** study suggestions never recommend elapsed times; shown, started, dismissed, completed, and stopped outcomes are recorded.
- **Study Analysis:** best hour and early-stop trend appear only when history exists.
- **Budget:** stable repeating transactions can be converted into recurring rules or dismissed.
- **Schedule:** attendance cards show pace-based projected risk using editable term dates.
- **Settings:** learned recommendation data and reminder timing can be reset.

### 5. Notification engagement
- Existing deadline, budget, checklist, class, and backup notifications use direct activity content intents plus tracked dismissal intents.
- Notification taps open `MainActivity` directly (compatible with modern Android's notification-trampoline restrictions), while tracking adds no new worker or alarm.
- Once enough data exists, Settings shows the most responsive hour.
- Applying that hour reschedules only non-urgent daily checks; class reminders keep their 15-minute safety cadence.

### 6. Punla Assistant
The new Assistant drawer destination is local-first.

Local commands can:
- summarize today's or tomorrow's classes;
- list upcoming deadlines;
- calculate weekly or monthly spending and categories;
- report remaining estimated absences;
- suggest a study slot;
- prepare a focus session;
- prepare an expense entry.

Known local commands never call the network. Unknown queries may use the optional Claude Messages API only when the cloud toggle is on and an API key is configured.

Cloud safeguards for this personal app:
- key encrypted with Android Keystore;
- no key in source, BuildConfig, backup, or logs;
- query-specific compact totals and planner slices rather than database dumps;
- 500-token output cap;
- 10 cloud requests per day;
- short connect/read timeouts and typed failure fallback;
- static system prompt marked for ephemeral prompt caching when eligible;
- editable model ID in Settings.

### 7. Precise-location recovery
All map entry points now request fine and coarse location together. If Android grants approximate access only, Punla keeps working and shows an `Enable precise` action. Fused-location priority becomes high accuracy only when fine permission is available.

## Deliberately omitted
- Navigation Markov-chain prediction remains optional and was not added; deterministic post-action shortcuts already cover Punla's current navigation paths with less tracking.
- On-device LLM inference remains out of scope because of the large model download and device requirements.
- The cloud assistant does not retain a conversation transcript in Room.

## Verification
- Kotlin PSI syntax parsing across every source file.
- Pure intelligence logic compiled and executed with checks for empty inputs, hourly rates, recurring patterns, calibrated attendance, and online-model learning.
- The local assistant and study-slot layer compiled and ran against lightweight stubs.
- The v6→v7 migration SQL was applied to a representative v6 SQLite table and preserved completed/stopped study rows.
- AndroidManifest XML parsed successfully.
- `git diff --check` passed.
- JUnit tests were added under `app/src/test/java/com/uplb/punla/ml` for CI/Android Studio.

A complete Android build still requires Gradle plus Android SDK 34. The included GitHub Actions workflow runs `assembleDebug`; the editing container does not provide the Android SDK or Gradle executable.

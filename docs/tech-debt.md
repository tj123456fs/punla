# Punla technical debt

Items in this file were observed during the repo-cleanup pass and deliberately left unchanged unless a later approved phase addresses them.

## Documentation and release metadata
- `README.md` still labels the continuation as 3.5.1, while `app/build.gradle.kts` is currently 3.5.2 / versionCode 42.
- `STUDENT_OS_PHASE_STATUS.md` still opens as 3.5.0 / versionCode 40. Its historical phase evidence remains useful, so Phase 1 did not rewrite it.
- The existing changelog's newest documented versioned entry is 2.9.6. Current 3.5.x release notes should be reconciled from validated release/session records rather than reconstructed from memory.
- No root `LICENSE` or `LICENSE.md` file was found during triage. `THIRD_PARTY_NOTICES.md` is present.
- Archived session/implementation documents may contain historical plain-text file references. Phase 1 repaired the active README/status links required by the move rather than rewriting historical prose.

## Data and persistence
- Several Room entities store calendar dates as ISO strings. Converting those fields to epoch-day or another typed representation requires a dedicated schema migration and is explicitly out of scope here.
- Recurrence generation now uses rule-scoped DAO queries, but `expenses.ruleId` and `deadlines.ruleId` are not Room-indexed. Adding indexes would require a schema migration and belongs in a dedicated persistence change.
- The 3.5.2 walk recorder added Room migration 13→14 and `tools/check_walk_migration.py`; any future CI-equivalent verification should include that check in addition to the older planning migration check.

## Assistant parsing and cloud usage
- The ten-call cloud limit remains enforced in one place: `PunlaRepository` through `assistantDailyCallLimit`, `consumeAssistantCall()`, and `assistantCallsUsedToday()`.
- `PunlaViewModel` now displays that same configured limit instead of repeating the numeric value.
- Local keyword matching remains intentionally English-only; expanding language support would be a feature, not a cleanup.

## Capture and shared content
- The detailed Phase 2 audit is in `docs/CAPTURE_ACTIVITY_AUDIT.md`.
- `CaptureActivity` caps shared text at 20,000 characters and each copied attachment at 8 MB, but there is no cumulative Inbox-directory cap at capture time.
- Runtime attachment handling still does not independently enforce the manifest's advertised SEND MIME allowlist or inspect file signatures.
- Provider-reported MIME determines the copied extension when known; unknown MIME falls back to `.bin`.

## UI/UX
- Current screens can feel visually crowded, especially where many equally prominent actions compete inside one surface.
- Dark mode has reported cases where text does not have enough contrast against its container/background; this needs a dedicated color/semantic-token audit rather than one-off hard-coded fixes.
- Interaction affordances must not drift toward text-only clickable labels. Future UI cleanup should prefer clear Material buttons, icon buttons, cards, rows, or chips with visible states and adequate touch targets.
- The pre-3.6 navigation/visual baseline remains the preferred direction; future cleanup should simplify hierarchy incrementally rather than reintroducing a broad navigation redesign.

## Large files and test seams
- `PunlaViewModel.kt` is about 2,297 lines / 105 KB and mixes settings, assistant, import/export, study, planning, grades, budget, and Pomodoro responsibilities.
- `SettingsScreen.kt` is about 1,931 lines / 92 KB.
- `StudyHubScreen.kt` is about 1,266 lines / 89 KB.
- `QuizScreen.kt` is about 1,427 lines / 86 KB.
- `BackupManager.kt` is about 1,121 lines / 77 KB and has no dedicated domain round-trip suite yet.
- `MainActivity.kt` is about 1,186 lines / 59 KB and still combines composition-root, navigation, permission, intent-routing, and Picture-in-Picture concerns.
- Triage found 20 JVM test source files containing `@Test`, rather than the 18 stated in the cleanup brief. Coverage remains concentrated in pure-logic/regression seams; BackupManager domain round-trip coverage is still a Phase 3 requirement.

## Protected validated cores
- `RecurrenceEngine.kt`, `ClassDayTimeline.kt`, `StudentContextReducer.kt`, and `LeaveByPolicy.kt` remain protected from behavioral refactoring.
- The approved Phase 2 recurrence query change is the narrow exception for `RecurrenceEngine.kt`: only its data-access source should change, with existing behavior/tests preserved.

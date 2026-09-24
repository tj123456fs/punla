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
- `RecurrenceEngine` currently calls `ExpenseDao.getAll()` and `DeadlineDao.getAll()`, then filters/scans the full snapshots by `ruleId`. Phase 2 is intended to replace those reads with rule-scoped DAO queries without changing recurrence behavior.
- The 3.5.2 walk recorder added Room migration 13→14 and `tools/check_walk_migration.py`; any future CI-equivalent verification should include that check in addition to the older planning migration check.

## Assistant parsing and cloud usage
- `LocalAssistant` uses substring checks such as `q.contains("class")`, so unrelated words containing a keyword can select an intent.
- Expense parsing currently accepts the first numeric match allowed by its regex, rather than explicitly prioritizing an amount after a ₱/PHP marker or after the add command.
- The ten-call cloud limit is already enforced in `PunlaRepository` through `assistantDailyCallLimit`, `consumeAssistantCall()`, and `assistantCallsUsedToday()`.
- `PunlaViewModel.askCloudAssistant()` also hard-codes the number 10 in its user-facing limit message. A later cleanup can decide whether a helper is warranted without introducing a second source of truth.

## Capture and shared content
- `CaptureActivity` already caps shared text at 20,000 characters and attachment copies at 8 MB.
- Attachment capture requires a `content://` URI, but `CaptureAttachments.copy()` does not independently enforce the manifest's advertised SEND MIME set.
- Attachment extension selection trusts the provider-reported MIME type and otherwise falls back to `.bin`; file signatures are not inspected.
- Malformed/unopenable content is surfaced through the existing exception/Toast path and temporary files are deleted, but MIME/content mismatch handling should be reviewed separately rather than changed during cleanup.

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

# Session 35 — Phase 0A Stable Core

Punla **v2.9.0 / versionCode 24** begins the Student OS roadmap's Phase 0 reliability work. This slice deliberately focuses on the three active daily-use failures: Study Hub stability, Pomodoro/background completion delivery, and attendance automation. It also adds a local diagnostic foundation for the rest of Phase 0.

## 1. Study Hub crash containment

- Wrapped the new Study System 3.0 Room list streams in guarded flows.
- A failing study-material query now records a local diagnostic and falls back to an empty list instead of cancelling the Study Hub collection path.
- Added an in-screen warning so a partial data-load failure is visible and dismissible.
- Replaced the Smart Study session's direct `queue[index]` access with bounds-safe lookup to protect against transition/recomposition races.
- Existing quiz/flashcard navigation hardening from Sessions 34c–34f remains intact.

This does not hide a database problem: it keeps the screen usable while preserving evidence for diagnosis.

## 2. Reliable Pomodoro / study-break completion

Punla now schedules every active Pomodoro deadline through two independent Android mechanisms:

1. **AlarmManager** remains the punctual path and uses exact alarms when Android allows them.
2. A persisted **one-time WorkManager job** is scheduled for the same deadline as a recovery path when an OEM battery manager or exact-alarm restriction delays/suppresses the alarm.

Both paths call the same idempotent `PomodoroCompletionCoordinator`, so duplicate delivery cannot double-log a completed focus interval or double-transition the phase. Cancelling/stopping the timer also cancels the fallback work.

The boot/app-update receiver and both deadline paths now record local diagnostics if completion handling fails.

## 3. Optional automatic attendance logging

Added **Settings → Attendance → Auto-log scheduled classes**.

- Off by default.
- After a scheduled class has been underway for 10 minutes, Punla can mark that occurrence **Attended** when no attendance record exists yet.
- Manual attendance always wins: Punla never overwrites an existing Attended/Absent record.
- Runs as persistent WorkManager work and does not depend on notification permission.
- Respects the configured academic term start/end dates.
- The preference is included in Punla's own backup/restore format.

This is intentionally schedule-based rather than location-based; enabling it means the user accepts that their schedule is a reasonable attendance proxy.

## 4. Local crash/diagnostic foundation

Added a small app-private rotating diagnostic log:

- records component, timestamp, short message, and stack trace for Punla-generated failures;
- captures otherwise-uncaught app crashes before delegating to Android's normal crash handler;
- is capped/trimmed locally to avoid unbounded growth;
- is excluded from Android cloud backup/device transfer;
- callers are required not to include notes, study content, API keys, or other user content.

A user-facing **System Health** screen and diagnostic viewer/export controls remain a Phase 0B task.

## No database migration

Room remains at **database version 12**. This slice adds no entity/table/column changes.

## Phase 0 items still open

- System Health screen.
- Explicit notification/background/battery-restriction diagnostics and shortcuts.
- Real-device checks after force-stop/app termination, reboot, long idle, and battery saver.
- Room migration/recovery-path hardening beyond Session 34f.
- Full backup → uninstall → reinstall → restore test.
- Quiz/flashcard import soak testing.
- Attendance action + undo real-device verification, including auto-log interaction.
- Campus Map real-device soak after Session 34g's build fix.
- Phase 0 exit soak: one normal week without a critical crash, missed essential reminder, corrupted data, or unexpected state loss.

## Validation boundary

Static source/resource validation is run in the editing environment. The final Android/KSP compile and device behavior still need the repository's GitHub Actions `assembleDebug` workflow and a real Android install because this editing container does not include the Android SDK/Gradle build environment.

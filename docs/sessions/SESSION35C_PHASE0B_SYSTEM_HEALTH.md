# Session 35C — Phase 0B System Health

Punla **v2.9.2 / versionCode 26** adds the first user-facing reliability diagnostics from the Student OS roadmap's Phase 0B.

## System Health screen

Settings now links to **System Health**, a local-only diagnostic screen that checks the Android services Punla depends on:

- Notification runtime/system permission and blocked Punla notification categories.
- Exact-alarm access used by the punctual Pomodoro completion path.
- Android's app-level background restriction signal.
- Doze/battery-optimization exemption state.
- Presence of expected persistent WorkManager jobs.
- Room/SQLite integrity using `PRAGMA quick_check(1)`.
- Backup freshness.
- Recent local diagnostic warning/error counts.

Checks that can be fixed in Android expose a direct shortcut to the relevant settings page. Returning to Punla automatically refreshes the snapshot.

## Diagnostic log controls

The Phase 0A rotating diagnostic log is now user-visible from System Health.

- View the latest 80 non-empty lines in-app.
- Export the log to a user-selected text file.
- Clear the diagnostic log after confirmation.
- Diagnostic data remains local unless the user explicitly exports it.

No study notes, quiz content, API keys, or other user content are intentionally written by the diagnostic logger.

## Notification-channel hardening

Punla now creates its standard notification channels during `Application.onCreate()` rather than waiting for an individual worker to fire. This makes Android notification settings and System Health accurate immediately after app startup.

## Architecture / safety

- Health inspection is read-only.
- Room and WorkManager checks run on `Dispatchers.IO`.
- System Health does not request battery-optimization exemption automatically; it only opens Android's settings for the user to decide.
- No database schema change.
- No new dependency.

## Phase 0 status after this slice

Phase 0A remains complete. The core Phase 0B diagnostic UI is now implemented, but Phase 0 itself is **not complete** until real-device reliability checks and the one-week exit soak pass.

Still open:

- Force-stop/app-termination reminder test.
- Device reboot reminder/timer recovery test.
- Long-idle / Doze test.
- Battery-saver / OEM restriction test.
- Backup → uninstall → reinstall → restore test.
- Room migration/recovery test matrix.
- Quiz/flashcard import soak.
- Attendance manual/undo/auto-log interaction soak.
- Campus Map real-device soak.
- One normal week with no critical crash, missed essential reminder, corrupted data, or unexpected state loss.

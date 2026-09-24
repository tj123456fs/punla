# Session 35 — Reliability + Attendance Auto-log

Version: **2.9.0** (`versionCode 24`)

## Pomodoro background break/focus alerts

Punla now schedules each active Pomodoro phase through two independent Android background paths:

1. `AlarmManager` remains the primary deadline signal and uses exact alarms when Android grants the app exact-alarm access.
2. A one-time `WorkManager` request is scheduled for the same persisted deadline as a fallback.

Both paths call `PomodoroCompletionCoordinator.complete(...)`. That coordinator already checks the persisted deadline and the last-handled deadline, so a duplicate wake-up cannot duplicate a study-session log or completion notification.

Pausing/stopping a timer cancels the AlarmManager request and all tagged backup deadline work. Reboot/app-update restoration reuses the same scheduling path.

## Study Hub crash hardening

Study Hub subscribes to many Room tables simultaneously. Study-related flows now use a guarded collector that logs a storage/query failure and emits an empty list rather than allowing an uncaught flow failure to terminate the app process.

The hub's weak-topic, exam-date, smart-queue, meaningful-study-days, and streak derivations are also guarded. A malformed legacy/imported row can therefore degrade only the affected derived section.

## Attendance auto-log

A new **Auto-log attendance** setting is enabled by default.

- After a scheduled class ends, Punla creates an `ATTENDED` record only when that exact occurrence has no attendance row yet.
- Existing manual `ATTENDED` or `ABSENT` records are never overwritten.
- The generated record uses source `auto` and remains editable through the existing attendance controls.
- The worker has a 15-minute recovery schedule plus a targeted one-time request for the next class end.
- Attendance auto-log does not depend on Android notification permission or the ongoing class notification being enabled.
- Full Punla backup/restore now preserves the auto-log preference.

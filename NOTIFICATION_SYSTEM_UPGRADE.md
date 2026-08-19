# Punla Notification System Upgrade — v1.9

## Goals
Make Punla's notifications feel like one student-assistant system instead of unrelated workers competing for the notification shade.

## What changed

### Central notification policy
- Added `notification/PunlaNotifications.kt` as the shared source for notification channels, grouping, stable ID ranges, priority, and quiet-hour rules.
- Fixed cross-feature notification ID collisions so checklist, budget, backup, deadline, daily brief, Pomodoro, class-day, push, and per-class alerts no longer overwrite unrelated notifications.
- Migrates the temporary legacy current-class channel so Android Settings does not show duplicate class-day categories.

### Class notifications
- The ongoing class-day card remains low-priority and silent.
- The separate 15-minute `ClassReminderWorker` is now the intentional attention alert even when the class-day card is enabled.
- Class-start alerts expire after 20 minutes so stale reminders do not remain after class begins.
- Quick actions prioritize `Schedule` and `Navigate` when a room is known.
- Per-class alert IDs use the session id instead of only the course code, avoiding collisions between lecture/lab blocks of the same course.
- The ongoing class card continues to show at most three context-appropriate actions, including attendance logging.

### Morning agenda
- Added `MorningAgendaWorker`, scheduled for about 7:15 AM through WorkManager.
- The brief summarizes today's class count, first class/time/room, and deadlines due today.
- It posts at most once per date and skips empty days.
- Morning agenda can be toggled independently in Settings.

### Quiet hours
- Added a user toggle for routine quiet hours, defaulting to 10:00 PM–7:00 AM.
- Quiet hours suppress checklist, budget, backup, and daily-brief nudges.
- Class alerts, Pomodoro alarms/timers, and deadline alerts remain available because they are time-sensitive.
- Learned reminder hours that fall inside quiet hours are shifted to a safe evening delivery time rather than firing overnight.

### Notification controls
- Settings now exposes:
  - master reminder permission/switch,
  - ongoing class card,
  - morning agenda,
  - quiet hours,
  - a direct shortcut to Android's per-category notification settings.

### Deadline and routine cleanup
- Deadline reminders remain deduplicated until the urgent-deadline snapshot changes.
- Routine notifications are low-priority, silent, grouped, and `onlyAlertOnce`.
- Finance reminders use their own group while preserving stable IDs for monthly and weekly budget notices.

## Validation
- Kotlin PSI syntax parse: 85 source files, 0 syntax errors.
- Notification policy compiled with local Android/AndroidX stubs and passed quiet-hour boundary + stable-ID tests.
- Static notification-ID audit confirms separated ranges for academic, routine, Pomodoro, class-day, push, and per-class alerts.
- Backup/export and restore both include `morningAgendaEnabled` and `quietHoursEnabled`.
- Android XML parsing and ZIP integrity are part of the packaging check.

A full `assembleDebug` still requires Android Studio or GitHub Actions because this sandbox does not include Android SDK 34 or the Gradle wrapper JAR.

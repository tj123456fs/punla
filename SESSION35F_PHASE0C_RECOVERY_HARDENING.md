# Session 35F — Phase 0C Recovery Hardening

Punla **v2.9.4 / versionCode 28** continues Student OS Phase 0 with recovery and restore verification.

## Central background scheduling

Added `CoreReliabilityScheduler` as the single registration path for Punla's persistent WorkManager jobs:

- deadline / budget / checklist reminders
- morning agenda
- 15-minute class reminders
- study nudges
- backup nudges
- class-day assistant recovery
- attendance auto-log

The same path is now reused at normal process startup, Activity startup, backup restore, device reboot, app package replacement, and clock/time-zone changes. This reduces drift between separate scheduling paths and gives System Health a real repair action.

## Reboot / update / clock recovery

The existing Pomodoro boot receiver now also re-registers all persistent background schedules on:

- `BOOT_COMPLETED`
- `MY_PACKAGE_REPLACED`
- `TIME_SET`
- `TIMEZONE_CHANGED`

Active Pomodoro deadlines are still restored through AlarmManager + the persisted WorkManager fallback. Recovery events are recorded locally in Diagnostics and surfaced by System Health.

## Real-device reliability probes

System Health now includes two Phase 0 test tools:

### Background execution test

Schedules a one-time worker two minutes in the future. The intended test is:

1. Tap **Run 2-min test**.
2. Swipe Punla away / leave the UI closed.
3. Wait roughly three minutes.
4. Reopen System Health.

A completed worker reports **Passed**. A probe still missing after ten minutes reports **Delayed / blocked**.

Android Force stop is deliberately not part of this test because the platform intentionally suppresses alarms/jobs for a force-stopped app until the user launches it again.

### Reboot recovery test

Tap **Arm test**, restart the phone normally, then reopen Punla. Receiving `BOOT_COMPLETED` marks the probe **Passed** and re-registers the core background schedule.

## Backup / restore hardening

Backup restore already validates the complete incoming object graph before destructive writes. This session adds transaction-level post-insert verification before Room commits:

- `PRAGMA quick_check(1)` must return `ok`.
- `PRAGMA foreign_key_check` must return no violations.

If either check fails, the Room restore transaction throws and rolls back instead of committing a damaged restored database.

After a successful restore, Punla re-runs `CoreReliabilityScheduler` so restored reminder/attendance preferences immediately match the active WorkManager schedule.

## Build fix carried forward

Removed the explicit `androidx.compose.foundation.layout.weight` import from `SystemHealthScreen.kt`; `Modifier.weight()` resolves from `RowScope` with Punla's pinned Compose version.

## Phase 0 status

The code-side reliability foundation is now substantially complete. Remaining Phase 0 work is primarily real-device validation:

- run the new background execution probe
- run the reboot recovery probe
- battery saver / long-idle test
- backup → uninstall → reinstall → restore test
- quiz / flashcard import soak
- attendance manual / undo / auto-log soak
- Campus Map soak
- one normal week without a critical crash, missed essential reminder, corruption, or unexpected state loss

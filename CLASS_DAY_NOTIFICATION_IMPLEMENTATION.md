# Punla Class-Day Notification — Implementation

## Added in version 1.6

Punla now maintains one low-priority notification that changes with the student's schedule instead of only sending a one-off "class starts soon" alert.

### States

- **Before class (30-minute window):** shows the next class, room/building, start time, and a live countdown.
- **During class:** becomes an ongoing current-class card with a system-rendered countdown to the end time.
- **Between classes:** shows free time, the next class, and whether the gap is long enough for one or more 25-minute focus sessions.
- **After the final class:** briefly shows an end-of-day summary, then removes itself after 30 minutes.
- **Outside useful windows:** stays hidden rather than occupying the notification shade all morning or overnight.

### Actions

- **Attended / Absent:** logs the current class, the class that just ended during a break, or the final class on the end-of-day card. The selected state is shown with a check and can be changed safely.
- **Navigate:** opens Campus with the class room pre-filled in search when Punla can resolve the room to a building.
- **Schedule:** opens the class schedule.
- **Start focus:** opens the Pomodoro screen during a break.
- **Hide today:** removes the card until the next calendar day.

### Scheduling and battery behavior

- Uses a single stable notification ID, so state changes update the existing card instead of stacking notifications.
- Uses Android's notification chronometer for live countdowns; Punla does not wake every minute.
- Schedules one-time WorkManager transitions at class boundaries and keeps a 15-minute recovery worker for delayed work, device sleep, time changes, and process death.
- Notification channel importance is LOW, sound and vibration are disabled, and updates use `setOnlyAlertOnce(true)`.
- Existing "class starts soon" alerts remain separate so they can still provide an audible/visible reminder if Android channel settings allow it.

### Settings

Settings → Notifications now includes **Ongoing class card**. It is enabled by default and can be disabled independently from deadline and other reminder notifications.

### Files

- `notification/ClassDayTimeline.kt` — pure schedule state machine
- `worker/ClassDayNotificationWorker.kt` — notification renderer, WorkManager scheduling, attendance actions, and Hide Today receiver
- `data/entity/AttendanceRecord.kt` and `data/dao/AttendanceDao.kt` — dated attendance history
- `ClassDayTimelineTest.kt` — boundary regression tests

The backup format now preserves the ongoing-class-card setting.

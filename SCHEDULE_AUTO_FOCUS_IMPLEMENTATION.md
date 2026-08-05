# Schedule Today Auto-Focus

## Added in version 1.8

Punla's Schedule destination uses Navigation Compose state restoration, which previously retained the last selected day and scroll position. This update adds a destination-resume focus request so opening Schedule behaves like a current-day view while still allowing manual browsing afterward.

## Behavior

- Monday through Saturday opens the matching day pill.
- If a class contains the current time (`start <= now < end`), Punla scrolls to it and marks it **HAPPENING NOW**.
- Before the first class or during a gap, Punla scrolls to the next class and marks it **UP NEXT**.
- After the final class, Punla scrolls to the final block and marks it **DAY COMPLETE**.
- A day with no classes still opens on the correct weekday and shows the normal empty state.
- Sunday falls back to the first scheduled supported day because the existing class model intentionally supports Monday through Saturday only.

## Architecture

`ScheduleFocus.kt` contains the pure time/day selection logic. `ScheduleScreen.kt` observes the destination lifecycle and issues one focus request on resume. `LazyListState` overrides the restored scroll position only for that request; later attendance or database updates do not interrupt the user.

## Validation

`ScheduleFocusTest.kt` covers current class, upcoming class, gaps, after-day behavior, empty weekdays, adjacent class boundaries, and Sunday handling.

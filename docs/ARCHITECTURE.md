# Punla architecture

This is a high-level map of the existing native Android codebase. It mirrors responsibilities already described in the README and Student OS phase status; it is not a redesign.

## `data/`
- Room entities and DAOs live under `data/entity` and `data/dao`.
- `PunlaDatabase.kt` owns the Room database and schema migrations.
- `PunlaRepository` exposes shared schedule, budget, deadline, settings, and assistant preferences used by the app and widgets.
- Recurring expense and deadline materialization is handled by `RecurrenceEngine.kt`.
- Campus location/routing data and helpers also live in the data layer.
- Widgets query the same Room data in-process rather than maintaining a separate data bridge.

## `ui/`
- Jetpack Compose screens implement Schedule, Budget, Deadlines, Grades, Study, Campus, Assistant, Settings, and Student OS surfaces.
- `PunlaViewModel` coordinates screen-facing state and actions across those features.
- `ui/theme` contains the field-notebook color, typography, shape, and background system.
- `ui/screens/PunlaWidgets.kt` contains shared Compose primitives such as tags, section labels, empty states, and responsive gutters.
- Navigation and screen composition are rooted from the native Android app shell.
- Responsive behavior includes readable content widths and adaptive navigation on wider devices.

## `widget/`
- Punla ships Glance widgets for Next Class, Budget Remaining, and Next Deadline.
- Each widget owns its Compose-like Glance UI in its corresponding Kotlin file.
- Widget size and resize behavior are defined by the matching `res/xml/*_widget_info.xml` file.
- Widgets read the same Room-backed data as the app.
- `WidgetRefresher` refreshes widgets after relevant app data changes.
- Atmospheric widget backgrounds use frozen frames rather than live animation.

## `worker/`
- Background work supports scheduled reminders, agenda delivery, reliability repair, and other deferred app tasks.
- The morning agenda summarizes the day's classes and due work.
- Routine nudges respect the app's configured quiet-hours behavior.
- Class-related background work supports the separate upcoming-class alert and ongoing class-day experience.
- Reliability work is designed to recover scheduled behavior after app/process or device lifecycle events.
- Periodic background delivery remains subject to Android/OEM scheduling constraints.

## `notification/`
- Notification policy keeps unrelated reminders from overwriting one another through stable per-feature identities.
- The ongoing class-day notification evolves through leave-soon, current-class, free-time, and end-of-day states.
- Its actions include attendance check-in, navigation, Schedule, focus, and hiding the card for the day.
- The ongoing class card remains silent while the separate imminent-class alert is the deliberate attention notification.
- Quiet-hour and category behavior is exposed through Settings and Android notification controls.
- Attendance actions write the same per-occurrence attendance history used by Schedule and Dashboard.

## `ml/`
- The local intelligence layer learns from on-device study and usage history.
- Documented signals include study-hour patterns, early-stop trends, recurring-expense candidates, attendance projections, and learned reminder timing.
- Study-slot ranking remains sparse-data-safe.
- A small online logistic-regression model is used only after enough personal outcomes exist.
- The intelligence layer supports recommendations without replacing the user's explicit schedule or study choices.
- Existing local logic remains usable when the optional cloud assistant is disabled.

## `planning/`
- Student context feeds one planning repository used by priority and planning engines.
- Planning considers classes, travel, routines, active focus, workload, energy, and configured term dates.
- The planner generates blocks around fixed commitments and preserves locked or ongoing blocks during regeneration.
- Capture/Inbox converts explicit drafts into tasks, deadlines, or notes while guarding against duplicate conversion.
- Recovery can move or split missed work when capacity exists rather than silently dropping it.
- Today, Plan, Pulse, assistant, and widget surfaces consume the same Student OS planning context.

## `assistant/`
- The local assistant answers bounded schedule, deadline, spending, attendance, and study questions from saved Punla context.
- Local actions include explicit focus and expense drafts that still require the app's normal confirmation flow.
- Optional cloud fallback is disabled by default and uses a user-provided API key.
- Cloud requests use compact context instead of raw database tables or conversation history.
- Cloud usage is capped daily, while local commands continue to work without that service.
- The assistant is not an unrestricted natural-language automation layer.

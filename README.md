# Punla — Native Android Rewrite

> **UI pass (this update):** theme, typography, and every screen were reworked to
> match the web app's field-notebook look (cream paper, crop green, UP maroon,
> mango accents). See "UI design system" below for details.

A from-scratch Android Studio project (Kotlin + Jetpack Compose + Room + Glance)
recreating the core of the original Punla web app, with **three real home-screen
widgets**: Next Class, Budget Remaining, and Next Deadline. It also includes a
local-first personal intelligence layer and an optional private assistant.

## Opening the project

1. Open Android Studio (Hedgehog/2023.1+ recommended, matching AGP 8.5 / Kotlin 1.9.24).
2. `File > Open` → select this `Punla/` folder.
3. Let Gradle sync. If it complains about the wrapper jar, click
   "Use Gradle from: 'gradle-wrapper.properties' file" or regenerate it via
   `Terminal: gradle wrapper` inside the project — the jar binary itself
   isn't included since it can't be produced outside Android Studio/Gradle.
4. Run on a device or emulator (API 26+).
5. Long-press the home screen → Widgets → "Punla" → drag out any of the
   three widgets (Next Class / Budget Remaining / Next Deadline).

## What's implemented

- **Local intelligence** (`ml/`) — on-device study-hour patterns, early-stop trends,
  recurring-expense candidates, attendance projections, learned reminder timing,
  and sparse-data-safe study-slot ranking. A tiny online logistic-regression model
  is used only after enough personal outcomes exist.
- **Punla Assistant** (`assistant/`, `AssistantScreen.kt`) — answers common schedule,
  deadline, spending, attendance, and focus questions locally. Optional cloud
  fallback is off by default, uses a Keystore-encrypted user API key, sends compact
  context rather than raw tables, and is capped at ten calls per day.
- **Approximate + precise campus location** — all location entry points request the
  Android permissions together, retain approximate fallback, and expose an
  `Enable precise` upgrade action for better walking-route origins.
- **Reviewed campus room directory** — 52 canonical UPLB building markers, explicit room-to-building overrides for known upstream contradictions, and conservative unresolved handling instead of risky prefix guesses.

- **Procedural background engine** — twelve selectable styles rendered natively with one shared Canvas pipeline across the app, Settings thumbnails, and frozen widget frames. New effects include Aurora, Ocean Waves, Fireflies, Sakura, Snow, and Bubbles; Theme Match automatically pairs every curated theme with a signature effect.

- **Ongoing class-day notification** — one silent card evolves from leave-soon to current class, free time, and end-of-day. Android's chronometer supplies live start/end countdowns, while Attended/Absent check-in, Navigate, Schedule, Start focus, and Hide today actions keep the notification useful without stacking alerts.

- **Per-occurrence attendance history** — each class meeting can be logged as Attended or Absent from the notification, Schedule, or Dashboard. Repeated taps are idempotent, corrections update the same dated row, and the legacy absence-risk tally remains synchronized.

- **Room database** (`data/entity`, `data/dao`, `PunlaDatabase.kt`) mirroring your
  original state shape: class sessions, dated attendance records, expenses + recurring rules, deadlines +
  recurring rules, semesters/grade courses, and archives.
- **PunlaRepository** — the "next class", "budget remaining", and "next deadline"
  logic, shared by both the app UI and the widgets (no duplication, no bridge —
  since this is fully native, widgets query Room directly in-process).
- **Compose screens**: Schedule (weekly list + add/delete), Budget (set monthly
  budget, log/delete expenses, running remaining balance), Deadlines (list,
  mark done, add/delete). Bottom navigation ties them together.
- **Three Glance widgets** (`widget/`): `NextClassWidget`, `BudgetWidget`,
  `NextDeadlineWidget`, each with its own `AppWidgetProvider` XML and receiver,
  refreshed immediately after any relevant data change via `WidgetRefresher`.
- **Recurring expense/deadline generation** (`data/RecurrenceEngine.kt`) — ports
  the web app's `generateRecurringExpenses()` / `generateRecurringDeadlines()`
  "catch up on load" logic to Room. The expense form's "Repeats" dropdown
  (Doesn't repeat / Weekly / Monthly) and the deadline form's "Repeats weekly"
  checkbox create an `ExpenseRule`/`DeadlineRule` alongside the first instance;
  `PunlaViewModel.init` re-runs the engine on every app launch so any
  occurrences that should exist by now (e.g. you skipped opening the app for a
  week) get backfilled, the same way the web app catches up on load. Marking a
  recurring deadline done immediately generates its next occurrence instead of
  waiting for the next launch. Each recurring item shows a small repeat icon
  and a "stop repeating" action that detaches existing instances and deletes
  the rule.
- **Theme toggle** (`data/PunlaRepository.kt` `ThemeMode`, top app bar icon) —
  a three-way System/Light/Dark preference, persisted and cycled by tapping
  the sun/moon/auto icon in the top bar. Previously the app silently forced
  light mode regardless of the device setting (`MainActivity` passed a
  `Boolean` that defaulted to `false` rather than following the system), so
  this also fixes that.
- **Class edit + schedule conflict check** — tapping a class card (or its edit
  icon) reopens the same dialog used for adding, pre-filled and re-using the
  original `id` on save. The dialog also gained the Day/Type dropdowns it was
  missing before (previously every class silently saved as Monday/Lecture
  regardless of what you picked, since there was nothing to pick from). Saving
  now runs the same overlap check as the web app's `checkConflict()` — same
  day, overlapping time range — and blocks the save with an inline warning
  instead of silently creating a double-booking.

## What's simplified / left as a next step

Given the size of the original app, a few things are scaffolded but not fully
built out — the Room entities and DAOs for these already exist, so it's UI work
from here:

- **Grades / GWA screen** — implemented: semester tabs (add/delete), an editable
  course table (code, title, units, UPLB grade dropdown 1.00–5.00/INC/DRP/W),
  and a live weighted-GWA summary card. Since Tj holds a CHED scholarship with
  a GWA retention requirement, the summary also takes a target GWA and flags
  whether the current semester is on track (remember UPLB's scale runs the
  opposite direction from most schools — 1.00 is the best grade, so "on track"
  means GWA ≤ target).
- **Deadlines calendar view** — only the list view is implemented.
- **Building/room map lookup** — implemented with MapLibre, live location, walking routes, and multi-stop route ordering.

## UI design system

- `ui/theme/Color.kt` — the full field-notebook palette (paper/ink, leaf green,
  UP maroon, mango) in both light and dark variants, ported 1:1 from the web
  app's CSS variables.
- `ui/theme/Type.kt` — a `Typography` set using serif for headlines (stands in
  for Fraunces) and monospace for numerals (stands in for IBM Plex Mono). Drop
  real `Fraunces-*.ttf` / `IBMPlexMono-*.ttf` files under `res/font` and swap
  the `FontFamily.Serif` / `FontFamily.Monospace` references here for pixel
  parity with the web app.
- `ui/theme/Shape.kt` — consistent rounded-corner radii used by cards, chips,
  and dialogs.
- `ui/screens/PunlaWidgets.kt` — small shared composables (`Tag`, `AccentBar`,
  `SectionLabel`, `EmptyState`, `PesoText`) reused across all four screens so
  peso amounts, priority/type pills, and empty states look identical
  everywhere instead of each screen rolling its own.
- Screens now use a top app bar, colored left-edge accent bars per card
  (leaf = lecture/low priority, mango = lab/medium priority, maroon = high
  priority), a `LinearProgressIndicator` on the Budget screen showing
  spent-vs-budget at a glance, and friendly icon-based empty states instead of
  a lone line of gray text.
- The bottom `NavigationBar` and FAB now pull their colors from the theme
  (leaf green) instead of Material's stock purple defaults.

## Editing the widget UI

Each widget's visuals live entirely in its own file
(`widget/NextClassWidget.kt`, `BudgetWidget.kt`, `NextDeadlineWidget.kt`) using
Glance's Compose-like API. Widget sizing/behavior (min size, update interval,
resize mode) is controlled by the matching XML in `res/xml/*_widget_info.xml`.

## A note on testing

This project was edited in a sandbox without Android SDK 34, so the final Android
assembly remains the GitHub Actions/Android Studio check. The procedural background core, dispatcher, automatic mapping, class-day timeline,
and attendance occurrence model were compiled with local Kotlin stubs; runtime checks
passed for all 12 background styles, all 16 themes, class-notification boundaries,
and attendance-key/idempotency rules. Treat the first real `assembleDebug` as the authoritative integration
check for Compose and Android APIs.

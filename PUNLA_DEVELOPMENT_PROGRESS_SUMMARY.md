# Punla Development Progress Summary

_Last updated: August 20, 2026_

This document summarizes the major Punla improvements completed during this development run, including feature work, UX changes, data fixes, and build-error fixes.

---

## 1. Campus Room and Building Location Fixes

We audited Punla's UPLB room/building directory against the current `uplbtools/room-tba` data and corrected major mapping problems.

### Main fixes
- Expanded the campus directory from the older building set to the current canonical building list.
- Corrected outdated or significantly displaced building coordinates.
- Removed or replaced obsolete broad building markers where more specific current buildings exist.
- Corrected important room-to-building mappings, including:
  - `ABC` rooms → AMPED Building
  - `PSLH` rooms → Physical Sciences Building
  - `ASR` / Animal Science lecture rooms → Animal Husbandry Building
  - `MB` rooms → New Math Building
  - `HL` rooms → Hydraulics Laboratory
- Added missing buildings such as:
  - AMPED Building
  - Animal Husbandry Building
  - Fronda Hall
  - Villegas Hall
  - Meat Science Building
  - Basic Veterinary Sciences
  - Veterinary Paraclinical Sciences Building
  - CVM-IAS Communal Building
  - Agricultural Machinery Testing and Evaluation Center
  - Forest Products and Paper Science
  - Social Forestry and Forestry Governance
  - Old Math / Old Rural Building
  - Veterinary Teaching Hospital
  - and other current campus locations
- Fixed destructive room normalization issues such as `CHE` vs `ChE`.
- Reduced risky prefix guessing.
- Kept ambiguous entries such as `TBA`, `Online`, and unresolved rooms unresolved instead of sending users to the wrong place.
- Added regression checks for high-risk room mappings and cross-building alias collisions.

---

## 2. Procedural Background Engine

Punla received a reusable animated-background system based on native Jetpack Compose drawing rather than videos or WebViews.

### Added backgrounds
- Rain
- Aurora
- Ocean Waves
- Fireflies
- Sakura
- Snow
- Bubbles
- Existing Ambient, Starfield, Paper Grain, and Minimal modes were retained.

### Improvements
- Added **Theme Match**, which automatically pairs an effect with the selected Punla theme.
- Enhanced rain with:
  - depth variation
  - wind angle
  - different speeds and streak lengths
  - brighter heads / fading tails
  - small splash effects
- Added static previews in Settings.
- Shared the rendering logic between the app, previews, and widgets.
- Widgets use a frozen/static version of effects where live animation is unsupported.
- Kept the implementation lightweight and Compose-native.
- Included third-party attribution for the open-source animation reference used during development.

---

## 3. Ongoing Class Notification

Punla's class notifications were upgraded from simple “class starts soon” alerts into a continuous class-day assistant.

### Notification states
- **Leave soon**
  - Shows the upcoming class
  - Room/building
  - Start time
  - Countdown
- **Current class**
  - Persistent silent card
  - Shows the active class
  - Counts down until class ends
- **Free time**
  - Shows the next class
  - Shows the gap between classes
  - Can suggest a focus session
- **Day complete**
  - Shows a short end-of-day summary

### Actions
- Navigate
- Open Schedule
- Start Focus
- Hide for today

The card reuses one notification ID so it evolves instead of stacking multiple notifications.

---

## 4. Attendance Check-In System

Attendance tracking was upgraded from a one-way “Mark absent” feature into a two-way attendance log.

### Added
- **Attended**
- **Absent**
- Status can be changed later.
- Duplicate taps do not create duplicate records.
- Changing `Absent → Attended` correctly reverses the absence count.
- Attendance is stored per class occurrence and date.
- Schedule cards display attendance state and totals.
- Attendance can be cleared or corrected from inside the app.
- Attendance records are preserved in backup/restore.

This keeps the existing absence-risk calculations while making attendance history more accurate.

---

## 5. Schedule Auto-Focus

The Schedule screen now opens where the user actually needs it.

### Behavior
When Schedule is reopened:
- Selects the current weekday automatically.
- Scrolls to the class happening now.
- Marks it as **HAPPENING NOW**.
- If no class is active, jumps to the next class and marks it **UP NEXT**.
- After the final class, opens near the bottom with **DAY COMPLETE**.
- Overrides stale Android-restored tab/scroll state on a fresh visit.
- Stops auto-moving after opening so manual browsing and attendance logging are not interrupted.

---

## 6. Notification System Overhaul

The entire notification system was cleaned up for consistency and reliability.

### Major changes
- Centralized notification channels and IDs.
- Fixed ID collisions where unrelated notifications could overwrite each other.
- Kept the ongoing class card silent.
- Restored a separate **15-minute class alert** as the intentional attention notification.
- Class alerts automatically expire so stale reminders do not linger.
- Added notification actions such as Schedule and Navigate.
- Added a **Morning Agenda** around 7:15 AM with:
  - number of classes
  - first class
  - room
  - deadlines due today
- Added **Quiet Hours (10 PM–7 AM)** for low-priority reminders.
- Important time-sensitive alerts such as classes, deadlines, and Pomodoro completion still work during quiet hours.
- Added Settings toggles for:
  - Morning Agenda
  - Quiet Hours
- Added a shortcut to Android's notification-category settings.
- Notification settings are included in backup/restore.

---

## 7. Budgeting System Upgrade

Punla's budget system was expanded from basic totals into a more useful day-to-day spending assistant.

### Safe to Spend Today
Punla now calculates how much can safely be spent per remaining day based on the active weekly/monthly limits.

It also:
- Uses the tighter constraint when both weekly and monthly budgets are active.
- Can go negative when spending is genuinely over pace.

### Reserved fixed expenses
Upcoming fixed recurring bills are reserved before discretionary money is calculated.

Examples:
- Rent
- Subscriptions
- Tuition-related recurring payments
- Other fixed bills

### Category limits
Optional monthly limits were added for:
- Food / Allowance
- Transportation
- Mobile Load / Internet
- Supplies
- Org / Activities
- Miscellaneous

### Expense editing
Expenses can now be edited for:
- Amount
- Category
- Note
- Date
- Fixed/recurring classification

### Backdated expenses
- Users can log earlier expenses.
- Added quick **Today** and **Yesterday** date options.
- Future dates are rejected.

### Recurring expense behavior
- Backdated recurring expenses immediately generate any already-due occurrences.
- Editing one occurrence does not unintentionally rewrite the entire recurring rule.

### Visibility improvements
- Home shows **Safe today**.
- The larger Budget widget also shows safe-to-spend.
- Budget warnings now include more actionable guidance instead of only showing percentage used.
- Category limits are included in backup/restore.

---

## 8. UI / UX 2.1 Refresh

A broader design-system pass was applied across Punla rather than redesigning one screen at a time.

### Responsive layout
- Added centered, readable content widths.
- Prevented cards/forms from stretching too far on tablets and landscape.
- Navigation rail activates around tablet/foldable widths instead of forcing the phone bottom bar everywhere.

### Navigation and hierarchy
- Cleaner left-aligned app bar behavior.
- Removed duplicate page titles on several screens.
- Improved section heading hierarchy.
- Added contextual section actions.
- Added clearer direct navigation from Home to:
  - Schedule
  - Budget
  - Deadlines

### Empty states
Empty screens now offer the obvious next action, such as:
- Add class
- Add expense
- Add deadline
- Add course
- Add item

### Buttons and selectors
- Replaced ambiguous `+` actions with labels such as:
  - Expense
  - Deadline
  - Course
  - Item
- Improved schedule/day selectors with smoother transitions.
- Improved spacing, alignment, and consistency of cards and statistics.

---

## 9. Build Errors Found and Fixed

After the UI/UX update, GitHub Actions exposed two compile errors that were identified and patched.

### `animateColorAsState` import error

Incorrect import:

```kotlin
import androidx.compose.animation.core.animateColorAsState
```

Correct import:

```kotlin
import androidx.compose.animation.animateColorAsState
```

The Compose animation artifact was also ensured in `app/build.gradle.kts`.

This resolved:
- unresolved `animateColorAsState`
- cascading `Modifier.background(...)` overload ambiguity errors

### Nullable Expense error

`BudgetScreen.kt` used a nullable `Expense?` as if it were guaranteed non-null.

Incorrect:

```kotlin
else if (initialExpense.ruleId != null)
```

Fixed:

```kotlin
else if (initialExpense?.ruleId != null)
```

This resolved the Kotlin compile error around the edit-expense dialog.

---

## 10. Termux / Git Workflow

A reusable Termux workflow was used for each Punla session package.

Typical flow:
1. Download the new Punla ZIP.
2. Extract it to a temporary folder.
3. Find the Android project root.
4. `rsync` the files into `$HOME/punla`.
5. Preserve the existing `.git` directory.
6. Stage changes.
7. Commit with a descriptive message.
8. Push the current branch to GitHub.
9. Let GitHub Actions run `assembleDebug`.

This allowed most development to be done from Android without requiring a desktop machine for every update.

---

## 11. Major Session Packages

The development run produced several packaged milestones:

- Session 20 — Room Location Fixes
- Session 21 — Procedural Background Engine
- Session 22 — Ongoing Class Notification
- Session 23 — Attendance Check-In
- Session 24 — Schedule Today Auto-Focus
- Session 25 — Notification System Upgrade
- Session 26 — Budgeting System Upgrade
- Session 27 — UI/UX 2.1

Punla progressed through these releases to approximately **v2.1 / versionCode 12** during this run.

---

## Current State

Punla now combines:

- UPLB room/building navigation
- current-class awareness
- evolving notifications
- attendance logging
- automatic schedule focus
- Pomodoro / focus features
- smarter budgeting
- procedural animated backgrounds
- theme integration
- responsive phone/tablet UI
- widgets
- backup/restore
- improved notification controls

The most recent work after the v2.1 UI/UX package focused on resolving GitHub Actions compile errors exposed by the real Android build pipeline.

---

## Recommended Next Steps

1. Re-run GitHub Actions after the latest compile fixes.
2. Resolve any additional errors revealed by `assembleDebug`.
3. Install the resulting debug APK and test:
   - Schedule auto-focus
   - Attendance actions
   - Ongoing notifications
   - Morning agenda
   - Quiet hours
   - Budget safe-to-spend calculations
   - Expense editing/backdating
   - Responsive UI on the actual device
4. Do a final polish pass based on real-device screenshots.
5. Once the debug build is stable, prepare a release build and changelog.

---

## Session 30 — Flashcards 2.0 + Quiz Maker

Punla v2.4 adds cloze/reverse/tagged/starred flashcards, Smart Study filters, safe JSON export/import, a full Quiz Maker, flashcard-to-quiz conversion, retry-mistakes, mistake-to-flashcard conversion, quiz history, and strict JSON type/content IDs (`punla.flashcards.deck`, `punla.quiz`, `punla.backup`). Room database version is now 10 and backup format version is 6.


## Session 31 — Atmospheric Background Engine 2.0 (v2.5)

Punla's procedural backgrounds received a motion-quality pass focused on making the observer feel stationary. Rain now falls predominantly downward using stable x anchors and explicit depth layers instead of coupling sideways travel to fall progress. Aurora was rebuilt from thick stroked wave paths into broad translucent filled curtains with vertical gradient falloff and slow independent edge deformation. Ocean Waves, Fireflies, Sakura, Snow, Bubbles, and Starfield were also softened and slowed. The existing shared Canvas renderer for the app, previews, and widget frames was preserved with no database migration.

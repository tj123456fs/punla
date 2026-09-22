# Session 39 — Phase 2C: Contextual Today

Punla version: **3.4.0** (`versionCode 38`)

This cumulative package includes Session 38's Today-first Home work and the Phase 2C refinement. It can be applied directly to the current green **3.2.1/36** main branch, or on top of an unpushed **3.3.0/37** Session 38 working tree.

## Phase 2C

- Added explicit Today states: `IN_CLASS`, `FREE_TIME`, `LEAVE_SOON`, `NEXT_SOON`, `DAY_COMPLETE`.
- Uses `usableFreeMinutes`, travel buffer, current class, next occurrence date, and the shared `StudentState` rather than rebuilding schedule context in Home.
- Added a real travel-first state when the remaining time is at/below the protected travel buffer.
- `Now` and `Recommended` crossfade when their underlying context changes.
- Recommended still honors the existing free-slot study suggestion first.
- When no free-slot suggestion exists, the fallback is intentionally simple and explainable: travel soon, stay in current class, overdue work, due review items, or clear-for-now.
- Today rows now show the action they will take (`Map`, `Focus`, `Study`, `Schedule`, `Deadlines`).
- Later shows how many additional deadlines remain in the seven-day window.
- Starting a Today recommendation records the existing study-suggestion acceptance before opening Pomodoro.

## Phase 2B carried forward

- Home defaults to Greeting → Today → Quick actions → Study pulse → More at a glance.
- The old detailed dashboard remains available behind More; no capability is removed.

## Safety / compatibility

- No Room migration.
- No backup-format migration.
- No new permission, receiver, worker, service, or network dependency.
- Session 37 Review 2.0 and Typography 2.0 remain untouched.

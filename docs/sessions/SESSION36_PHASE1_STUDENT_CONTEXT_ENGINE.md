# Session 36 — Phase 1A Student Context Engine

## Goal

Start Phase 1 with a single shared source of student context. This session is deliberately architectural: it does not replace the Home screen or add recommendation scoring yet.

## Added

- `StudentState` — UI-agnostic snapshot used by future major screens.
- `StudentContextEngine` — singleton reactive aggregator backed by existing Room Flows.
- `StudentContextReducer` — deterministic/testable derivation logic.
- Unit tests for current-class and next-class time handling.

## Context currently exposed

- current local date/time/day;
- current class;
- next class;
- free minutes before the next scheduled class;
- pending deadline + study-plan tasks;
- deadlines due within seven days;
- overdue work;
- recent study minutes and latest study activity;
- due/weak flashcard counts;
- unresolved mistake count;
- weak quiz count;
- attendance summary and current-class attendance state;
- weekly/monthly budget context and safe-to-spend value;
- per-course study/deadline/weakness/review summary.

## Intentionally deferred

- Recommendation ranking / decision layer.
- Energy check-in UI.
- Location collection and travel-time calculation.
- Punla Today redesign.

Those are consumers or later layers on top of the context engine, not responsibilities of the engine itself.

## Reliability choices

- No Room schema change.
- No backup-format change.
- No new WorkManager job, alarm, receiver, permission, or notification.
- Time-derived state refreshes once per minute.
- Room-backed data refreshes reactively.
- Preference-only changes can call `StudentContextEngine.get(context).refreshNow()`.
- Aggregation runs on `Dispatchers.Default`, not the UI thread.

## Version

The apply script bumps the app from `2.9.6 / 30` to `3.0.0 / 31` when those exact values are present. It also accepts already-applied `3.0.0 / 31` without changing them again.

## Phase 1 next session

Wire `StudentState` into Punla Home as a read-only **Today/Now/Next** surface, then add a separate explainable decision layer for the primary recommendation.

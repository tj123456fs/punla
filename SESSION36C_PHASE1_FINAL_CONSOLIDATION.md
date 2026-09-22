# Session 36C — Phase 1 Final Consolidation

**Release:** Punla 3.1.1 / versionCode 34  
**Phase:** Phase 1 — Student Context Engine (final)  
**Room schema:** unchanged (v12)  
**Backup format:** unchanged (v9)

## Goal

Close Phase 1 by removing the highest-value remaining duplicate context calculations while preserving existing screen APIs and Phase 0 reliability behavior.

## Canonical context compatibility bridge

Existing callers still use `nextClassFlow`, `nextDeadlineFlow`, and `todayStudyMinutes`, but those APIs no longer independently decide schedule/deadline/study facts.

- `nextClassFlow` takes `studentState.nextClass.sessionId` and resolves the existing `ClassSession` by stable ID.
- `nextDeadlineFlow` takes the first canonical `studentState.upcomingDeadlines` item and resolves the existing `Deadline` by stable ID.
- `todayStudyMinutes` now maps directly from `studentState.study.minutesToday`.

This keeps Dashboard and Campus Map source-compatible while making the Student Context Engine the single source of truth for those facts.

## Overnight class consistency

`StudentContextReducer` and the class-day notification timeline already model an end time earlier than the start time as a next-day end. `PunlaViewModel.addOrUpdateClass()` previously rejected the same input.

Session 36C aligns validation:

- `22:00 -> 01:00` is accepted as an overnight class.
- identical start/end times remain invalid.

## Cumulative fixes retained

The installer retains all Session 36A/36B work:

- overnight-current-class reducer fix
- case-insensitive course dedupe
- unset weekly-budget guard
- flashcard next-answer one-frame spoiler fix
- app-wide `studentState`
- transient energy context
- opt-in ephemeral location context
- travel buffer and usable free time
- preference-driven context refresh
- Dashboard canonical budget/deadline consumers

## Phase 1 exit state

After Session 36C, Punla has one shared reactive student-state layer for:

- current and next class
- free time / usable free time
- deadlines and overdue work
- recent study activity and weak-study signals
- attendance context
- budget context
- per-course context
- optional energy and location context

The engine remains fact-oriented and does not rank or choose actions for the student.

**PHASE 1 — COMPLETE**

The next development block is **Phase 2 — Punla Today 1.0**, which can build `Now / Next / Recommended / Later` UI on top of this shared state without duplicating domain logic.

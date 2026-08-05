# Attendance Check-In Implementation

## Added in version 1.7

Punla now stores attendance for a concrete occurrence of a weekly class instead
of only maintaining a one-way absence counter.

## User flow

- During an ongoing class, the class-day notification shows **Attended** and
  **Absent** actions.
- During a break, those actions refer to the class that just ended.
- On the temporary end-of-day card, they refer to the final class.
- The notification refreshes silently and marks the selected action with a
  check.
- Today's Schedule card provides the same two controls plus **Clear**.
- Dashboard provides both controls when the displayed class occurs today.

## Data model

`attendance_records` stores:

- deterministic occurrence key: `sessionId|date|scheduledStart`
- class/session identity
- ISO local date and scheduled start
- `ATTENDED` or `ABSENT`
- log time and source

The deterministic primary key makes repeated taps idempotent. Changing status
replaces the same row.

## Existing absence-risk compatibility

The existing `ClassSession.absences` tally still powers the 20% risk estimate.
`PunlaRepository.setAttendance()` updates that tally transactionally:

- no previous absent record -> Absent: increment once
- Absent -> Attended: decrement once
- same status tapped again: no tally change

Older manually entered absence totals remain intact.

## Persistence

- Room database version: 8
- Migration: 7 -> 8 creates the attendance table and indexes
- Backup format: 3
- Backup export/import includes all attendance records

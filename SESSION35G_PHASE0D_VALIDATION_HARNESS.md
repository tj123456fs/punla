# Session 35G — Phase 0D Validation Harness

Punla **v2.9.5 / versionCode 29** continues Phase 0 by converting more of the remaining real-device validation matrix into persistent, inspectable checks.

## Battery / idle probe

System Health now has a **20-minute Battery / idle test**. It schedules ordinary non-expedited WorkManager work and records the actual completion time.

Recommended real-device test:

1. Open Settings → System Health.
2. Tap **Run 20-min test**.
3. Optionally enable Android Battery Saver.
4. Lock the phone and leave Punla closed.
5. Reopen System Health after roughly 25–30 minutes.

Results:

- **Passed** — completed within about 10 minutes of the target time.
- **Passed late** — Android eventually ran the work, but deferred it more than 10 minutes.
- **Delayed / blocked** — no completion after 60 minutes.

This deliberately measures ordinary background recovery work rather than requesting expedited execution.

## Verified restore receipt

After a backup import succeeds, Punla now persists a metadata-only receipt containing:

- backup version / content ID / exported timestamp
- verification timestamp
- restored counts for classes, deadlines, attendance, flashcards, quizzes, and study material

The receipt is written only after the Room transaction passes `PRAGMA quick_check(1)` and `PRAGMA foreign_key_check`, and after restored preferences are applied. System Health displays the receipt as **Restore verification — Verified**.

This makes the Phase 0 **export → uninstall → reinstall → import** test observable from inside the fresh installation.

## Seven-day stability soak

System Health now includes a **7-day stability soak** action. Starting it stores:

- the start timestamp
- the current monotonic uncaught-crash count

Punla's uncaught-exception handler increments that local counter before handing the crash back to Android. During the soak, System Health shows elapsed days/hours and immediately flags any new uncaught crash.

The seven-day card only proves the crash-free portion of the Phase 0 exit gate. Reminder delivery, data integrity, and state-loss checks still require normal use.

## Schema status

- Room: **v12**
- Backup: **v9**
- No migration required.

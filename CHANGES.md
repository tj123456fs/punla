# Punla changes

This root changelog keeps the ten most recent versioned release entries already documented in the repository. The complete pre-cleanup history is preserved in [docs/CHANGES_ARCHIVE.md](docs/CHANGES_ARCHIVE.md).

## Session 35H — Main-thread scheduler merge

- Moved persistent worker scheduling/recovery off the Android main thread.
- Boot/time/package recovery now uses `goAsync()` plus a background coroutine.
- System Health job repair no longer blocks the interaction frame.
- Preserved Phase 0D Battery/Idle, restore-verification, and 7-day stability validation features.
- Removed duplicate Activity-level cold-start scheduling; Application owns the startup repair path.
- Version 2.9.6 (30).

## Session 35G — Phase 0D Validation Harness (v2.9.5)

- Added a 20-minute Battery / idle reliability probe to System Health for real-device screen-off, Battery Saver, and OEM background testing.
- The idle probe reports whether ordinary WorkManager execution completed near its target, completed late, or remained blocked/delayed.
- Backup restore now writes a metadata-only verified restore receipt after Room integrity checks and preference restoration succeed; System Health surfaces the latest verified receipt and restored record counts.
- Added a persistent seven-day Phase 0 stability soak tracker. It records a crash-counter baseline and automatically flags uncaught crashes during the soak.
- Uncaught process crashes now increment a tiny local monotonic crash counter in addition to the existing rotating diagnostic log.
- No Room migration and no backup-format change. Room remains v12; backup remains v9.

## Session 35F — Phase 0C Recovery Hardening (v2.9.4)

- Centralized all persistent WorkManager registration in `CoreReliabilityScheduler` so app startup, restore, reboot, and package-update paths cannot drift apart.
- Boot/package-replaced/time/time-zone broadcasts now repair Punla's background schedules and restore an active Pomodoro deadline.
- Added System Health **Background execution** and **Reboot recovery** probes for real-device Phase 0 validation.
- Missing WorkManager jobs can now be repaired directly from System Health.
- Backup restore now runs SQLite `quick_check` and `foreign_key_check` inside the Room transaction before commit; failed verification rolls the restore back.
- Successful backup restore immediately re-syncs persistent workers with restored settings.
- Carried forward the System Health `Modifier.weight()` compile fix.
- Room remains v12; backup format remains v9.

# Session 35D — Scroll-aware animation smoothing

- Replaced the hard animated-background freeze during scrolling with adaptive slow motion.
- Added a continuous virtual animation clock so background motion does not stop/jump around scroll gestures.
- Eases toward ~32% background playback speed while scrolling and back to full speed afterward.
- Caps decorative background publishing to ~12 FPS during active scrolling while preserving the existing idle cadence.
- Clamps large resume-time deltas to prevent atmosphere jumps after app sleep/backgrounding.
- Bumped app version to 2.9.3 (27).
- No database migration and no new dependencies.

# Session 35C — Phase 0B System Health

- Added a local System Health screen for notifications, exact alarms, background restrictions, battery optimization, WorkManager jobs, database integrity, backup freshness, and diagnostics.
- Added in-app diagnostic log viewing, text export, and confirmed clearing.
- Added direct Android settings shortcuts for actionable reliability problems.
- Standard notification channels are now created at application startup so channel health is immediately inspectable.
- Bumped app version to 2.9.2 (26).
- No database migration and no new dependencies.

# Session 35B — Smooth interaction pass

- Isolated the animated procedural background into its own `graphicsLayer` so atmosphere redraws do not re-record the foreground app tree.
- Pauses decorative background animation during active Compose scroll/fling gestures, then resumes afterward.
- Vsync-aligned the existing 15–25 FPS background ticker with `withFrameNanos`.
- Migrated Compose screen Flow subscriptions to lifecycle-aware collection (`collectAsStateWithLifecycle`).
- Moved/serialized Glance widget refresh work off the UI dispatcher.
- Cached Settings background preview rasters and made the theme chooser lazy.
- Memoized palette/color-scheme/typography resolution and simplified sibling-tab transitions to short fades.
- No Room/backup schema change. Version **2.9.1** (`versionCode 25`).

## Session 35 — Phase 0A Stable Core
- Bumped app to **2.9.0 / versionCode 24** and started roadmap Phase 0 reliability work.
- Added guarded Study Hub Room streams plus bounds-safe Smart Study queue access so recoverable data/transition failures do not crash the entire hub.
- Added a persisted one-time WorkManager fallback for every Pomodoro deadline while retaining AlarmManager as the punctual completion path; both converge on the existing idempotent completion coordinator.
- Added opt-in schedule-based attendance auto-log after a 10-minute grace period. Manual Attended/Absent records always win, and the setting survives Punla backup/restore.
- Added an app-private rotating diagnostic log plus uncaught-crash capture, with the diagnostic file excluded from Android backup/device transfer.
- Room remains at database version 12; no schema migration is required.

# Session 34g — Campus map compile fix

- Fixed `CampusFullMapScreen.kt` compile failure (`Unresolved reference: context`) in the MapLibre `AndroidView` update block.
- The location-component activation now uses the active `MapView` context (`it.context`), which is guaranteed to be in scope inside `AndroidView.update`.
- No feature or database-schema changes.
- Version bumped to **2.8.3** (`versionCode 23`).

## Session 34f — Full debug hardening
- Bumped app to **2.8.2 / versionCode 22**.
- Replaced Room `REPLACE`-style upserts with `@Upsert` so editing parent rows no longer risks foreign-key cascade data loss.
- Kept quiz saves alive outside result-screen composition and made quiz-attempt side effects idempotent by attempt ID.
- Removed quiz and flashcard transition-time null crashes and remaining force-unwrapped nullable references in the main source.
- Hardened complete-backup restore: all major IDs, relationships, dates/times, numeric values, quiz/study metadata, topic hierarchies, attendance, recurrence metadata, and preference values are validated before the destructive Room transaction begins.
- Backup format is now v9 and preserves theme preset, custom color, background style, font choice, study goals, and Pomodoro preferences while continuing to exclude encrypted API-key storage.
- Added Android backup/data-extraction exclusions for the encrypted secret preference file and disabled cleartext traffic.
- Made recurring-expense/deadline and semester multi-write operations transactional; runtime recurrence now ignores invalid/corrupt cursors, priorities, and intervals instead of propagating bad state.
- Hardened numeric inputs/calculations against NaN/Infinity and oversized Float-backed preference values.
- Hardened Study Pack module hierarchy import against unknown parents, self-parenting, and cycles, and surfaces unknown material-module links as warnings.
- Converted repeated widget/notification/Quick Add navigation to one-shot request tokens so the same route can be invoked repeatedly.
- Audited broadcast receivers: async receiver work uses `goAsync()`, and the boot/update receiver is exported for system broadcasts while internal action/alarm receivers remain non-exported.
- Fixed the duplicated `studyNotes` restore declaration and duplicate question-bank validation introduced during the debug pass.
- Final source validation: Kotlin delimiter/string/comment structural scan passed; Android XML and bundled JSON parse cleanly; representative Room v11→v12 migration SQL passed; no main-source `!!` or `OnConflictStrategy.REPLACE` remains.
- A full Android/KSP compile still requires the Android SDK/Gradle dependency environment (GitHub Actions is the final compile/install check).

## Session 34e — Study flow debug hardening
- Fixed the flashcard study/deck back-navigation crash caused by force-unwrapping `selectedDeck` during a Compose `Crossfade` transition.
- Moved completed quiz persistence into `viewModelScope` so navigating away from results cannot cancel a valid save.
- Added attempt-id idempotency before quiz side effects, preventing rotation/recomposition from double-counting mistake history or study-goal progress.
- Added a direct `QuizDao.getAttempt()` lookup used as the transaction idempotency guard.
- Retains the Session 34c save-lifecycle fix and Session 34d quiz back-navigation fix.
- Bumped app version to 2.8.1 (versionCode 21).

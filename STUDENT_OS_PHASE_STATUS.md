# Punla Student OS continuation — 3.6.0

Current version code: **42**. The underlying Student OS continuation began at 3.5.0 / code 40. Starting repository commit: `42c187c645db118b9c211ebf0573a769ed17a694`.

The supplied Student OS roadmap and Session 40 summary were read before implementation. The cumulative Session 39/40 changes were applied to the full repository first. This report separates implemented local behavior from release gates; the entire roadmap is not declared production complete.

## Phase status

| Phase | Implemented behavior | Remaining gate or scope limit |
|---|---|---|
| 0 — Stable core | Existing System Health and reliability work retained; Room 12→13 migration, backup v10, attachment validation, transactional planning writes and regression coverage added. | One full week on a real device, including reboot, battery saver, background reminders and backup/reinstall/restore. |
| 1 — Shared context | Academic context feeds one planning repository; energy, routines, active focus, workload and travel affect availability. Term dates constrain current/next classes. | Device integration checks. |
| 2 — Today | Now/Next/Recommended/Later, one ranked task, explanations, loading/empty states, accessible tap targets, compact header, active class/focus/routine and departure guards. | Small-screen and large-font device review. |
| 3 — Priority | Deterministic urgency, importance, remaining effort, time fit, energy, weakness and recent-study signals; manual first choice, temporary dismissal and restore. | Tune using real usage; no claim of learned duration estimates. |
| 4 — Energy | Drained/Okay/Locked in check-in persists for six hours; affects priority and block length. | Device checks of expiry and restart. |
| 5 — Capture | Global quick add, Inbox, Android text/image/PDF/JSON sharing, private attachment copies, date/time/course draft parsing, editable confirmation into tasks/deadlines/notes. Existing class/expense/import forms remain reachable. | Real share-provider checks. Attachments are retained; OCR is not implemented. |
| 6 — Planner | Generates blocks around classes, travel, routines, active focus and locked blocks; move, resize, skip, lock and regenerate. Deadline boundaries are enforced. | Device interactions. |
| 7 — Recovery | Missed blocks move to a free opening or split; repeated recovery is guarded; insufficient capacity preserves the original block. | Device interactions. |
| 8 — Study intelligence | Existing quiz, flashcard, mistake and course data inform priorities/Pulse; structured steps open the appropriate course and study section. | Multi-step execution is manual; no new quiz generation model. |
| 9 — Deadline intelligence | Editable effort, progress and due time; remaining-work calculations, sessions before deadlines and cumulative capacity warnings over eight days. | Estimates are not predictions; undated tasks require judgment. |
| 10 — Academic Pulse | Concrete deadline, quiz, weakness, attendance and recorded-grade signals with links to corrective actions. | Historical grades require semester review. No universal risk score. |
| 11 — Ambient | Today widget shares Home presentation logic; existing morning brief retained; optional quiet evening recap and travel-aware class reminder. | Widget/OEM/permission/background checks. Periodic delivery is not an exact alarm guarantee. |
| 12 — Attendance | Post-class suggestions ask for Attended/Absent confirmation; Ignore and category disable supported; Schedule can correct attendance. | Real-device action/undo checks. Separately enabled legacy auto-attendance is preserved. |
| 13 — Campus | Offline graph→pedestrian OSRM→estimate fallbacks, source labels, shared duration, stale-route rejection, buffers and leave-by reminder content; GPX conversion included. | Real surveyed `campus_waypoints.json` is still required. The example is deliberately not production data. Field accuracy checks remain. |
| 14 — Life | Recurring meals, errands, organizations, exercise and sleep reserve time, including overnight commitments; editable planning hours and travel; connected budget. | No separate habit or wellness subsystem. |
| 15 — Assistant | Bounded local day/next/study/tonight-off commands use saved context; expense and session-move drafts require confirmation; linked from existing Assistant. | Not unrestricted natural-language action execution. |
| 16 — Proactive | Dismissible attendance, workload, new-quiz and budget suggestions; stable dismissal IDs and category controls. | Long-term pattern learning is not claimed. |
| 17 — Automation | Small class-ended, quiz-added and task-completed rules; reactive context, review/attendance suggestions, priority and widget refresh. | No programmable rule editor or cloud service. These rules never auto-mark attendance. |
| 18 — Timeline | Today/seven-day views assemble classes/attendance, focus, reviews, quiz attempts, expenses, captures, routines and plan blocks. Date-only expenses do not invent a recorded time. | Device usability checks. |
| 19 — Accounts/sync | **Deferred under the roadmap's stability gate.** Offline ownership and portable backup remain available. | First complete Phase 0 evidence; choose/provision backend; define authentication, encryption, recovery and conflict rules; implement cross-device tests. No sync/account service was provisioned. |

## Data and recovery

`StudentContextEngine → StudentOsRepository → priority/planning engines → Today, Plan, Pulse, assistant and widget`.

Existing deadlines and Study Hub plan items remain the task sources. Completing a task updates its original record. New tables hold task preferences, captures, day-plan blocks, recurring life commitments and settings. Regeneration preserves locked and ongoing blocks and reports work that cannot fit.

Energy, dismissal, effort and planning settings survive restart. Inbox conversion rechecks its source transactionally to prevent duplicate tasks. Captures use app-private attachments, limited to 8 MB each and 64 MB total in backups.

Backup v10 includes planning tables and attachments. Legacy backups restore without planning records. Current backups with malformed, duplicate or incomplete planning records fail validation before old rows are deleted. Attachments are staged under new names and removed if the database transaction fails.

## Validation

Current 3.6.0 evidence: **119 JVM tests, 8 Android API 34 tests, 3 GPX tests and the migration/schema check pass**. The UI/UX foundation is merged in [PR #3](https://github.com/tj123456fs/punla/pull/3). See [SESSION42_UI_UX_FOUNDATION.md](SESSION42_UI_UX_FOUNDATION.md) for the exact source, build, screenshots and signing details. Today, primary navigation, dated agenda, capture and Inbox presentation are updated. The phase gates in the table remain; this does not complete physical-device reliability, surveyed campus paths, accounts/sync or the remaining Study/reader redesign.

### Historical 3.5.0 validation

The exact final results are included in the update package's `verification.json` and `unit-test-report/index.html`.

The first complete local run passed **111 JVM tests across 19 suites**, **3 Python GPX tests**, the SQLite migration/schema check and APK signature verification. A workspace reset occurred before delivery; the recorded source patches were restored and the final package was rebuilt successfully and passed the same 111 JVM tests, 3 GPX tests, migration/schema check and APK signature verification.

The SQLite check compares all five new tables with Room-generated schema, preserves existing schema and a sample deadline, and runs `quick_check`. It does not replace an Android migration test with a real version-12 database. GPX tests ensure recording gaps do not create invented walkable edges and invalid/empty tracks are rejected.

Build prerequisites: JDK 17, Gradle 8.13, Android platform 34. Commands:

```bash
gradle testDebugUnitTest assembleDebug
python3 tools/check_planning_migration.py
python3 -m unittest discover -s tools -p 'test_*.py' -v
git diff --check
```

The existing schedule tests were corrected to use the configured JUnit imports without changing assertions. A fresh build exposed 14 byte-identical per-round KSP Java copies alongside canonical generated database classes; Java compilation now excludes only those temporary copies. CI runs a clean build/tests plus migration/GPX checks. No physical-device or emulator UI test was performed here. Existing deprecation warnings and unstripped native libraries do not prevent a debug build.

## Device acceptance before release

1. Back up an existing installation. Upgrade with its existing signing key; confirm schedule, study, attendance, expense and deadline records survive Room migration.
2. Share text and a PDF. Confirm Inbox opens, cancellation creates no task, conversion creates exactly one, and attachments remain accessible after restart.
3. Set energy, pin/dismiss tasks, generate, lock and regenerate. Reject overlapping moves. Complete blocks and tasks; verify progress and original-source completion.
4. Recover missed blocks whole/split; repeated taps must not duplicate recovery. Check overnight sleep and due-time boundaries.
5. Compare Today, maps and widget during class, focus, routines, free time and departure. Check small screens, large fonts and TalkBack.
6. Confirm and correct attendance. Dismiss/disable suggestions and confirm settings persist.
7. Export planning data with attachments, restore to a separate test installation and compare records. Malformed backup rejection must preserve existing data.
8. Exercise morning brief, departure alert, Pomodoro completion, recap, widgets, app closure, reboot and battery saver through the full-week reliability gate.

Current CI debug APKs use ephemeral signing keys; the delivered 3.6.0 signer differs from 3.5.1. If Android reports a signature mismatch, build the reviewed source with the existing signing setup. Do not remove an existing installation without a verified backup.

## Publishing and next session

Historical publication note, 2026-09-23: the user explicitly authorized GitHub publication. The saved continuation and the 3.5.1 study-rendering fix are now merged into `tj123456fs/punla` on `main` through [PR #2](https://github.com/tj123456fs/punla/pull/2). See `SESSION41_STUDY_RENDERING_FIX.md` for that release's validation and signing details. The current 3.6.0 continuation and remaining UI/UX work are recorded in `SESSION42_UI_UX_FOUNDATION.md`. The original 3.5.0 delivery had not been pushed; that historical limitation is resolved.

Next work should collect the device evidence and surveyed campus paths. Phase 19 remains gated by local reliability.

Routing references: [OSRM frontend pedestrian configuration](https://github.com/Project-OSRM/osrm-frontend), [FOSSGIS usage policy](https://routing.openstreetmap.de/about.html). Requests are paced and map surfaces include attribution and map-fix links.

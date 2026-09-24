# Session 42 — UI/UX foundation

Version **3.6.0 / code 42**. Continues the existing Android app and the priorities in UI_UX_IMPROVEMENT_PLAN.md.

## Implemented

- Today, Plan, Study and More are primary navigation destinations on phones and tablets. Existing routes, widget destinations and structured add forms remain reachable. More groups semester, campus, study and preference tools.
- Today has a compact greeting and one prominent contextual action. Current class, departure time and personal commitments take priority over optional study. An active focus timer has a return action. Next and Later use quieter rows.
- Capture is the + action in the top bar, so it never covers reading or block controls. It opens a text-first sheet. Parsed course/date/time are suggestions for later review; saving does not create a deadline automatically. A draft clears only after its database write succeeds. The Inbox badge and post-save Review action expose the next step. Structured class, expense, deadline and grade forms remain available. Inbox lists saved thoughts first. Theme controls remain in Settings.
- Plan offers Yesterday through the next seven days, with chronological class, reserved travel, life commitments, active timer, study blocks and available time. It labels flexible/locked blocks and current activity. Block actions are in a detail sheet. Recovery asks for confirmation and retains the existing conflict/deadline checks. Activity and Inbox remain adjacent; Pulse, Life and planning assistant retain their deep links.
- Academic Pulse is linked from the Study overview. The existing cream/green/maroon/mango theme remains, with opaque reading surfaces and wrapping controls.

No Room or backup schema changes. No new cloud service or dependency on UX reference services.

## Validation

[CI run 36017060626](https://github.com/tj123456fs/punla/actions/runs/36017060626) passed at source commit `02135b0ae6f0da7407a127530650f1d68cff1cee`:

- **119 JVM tests**, zero failures or ignored tests.
- **8 Android API 34 tests**: five ICU study-rendering regressions and three Compose interaction checks. The full app flow captures text, reviews and converts it, generates a plan, locks and completes a block, opens Study/More, and reaches Schedule. Repeated Inbox/Agenda switching follows conversion. The other checks cover draft retention after a failed save and departure guidance at 150% text in dark mode.
- **3 Python GPX tests** and the Room/SQLite migration comparison with `quick_check=ok`.
- Eight emulator screenshots collected; visual review covered the narrow-phone layout, capture/review/block sheets, Study, More and large-text dark mode. Capture was moved to the top bar after review showed the floating button obscuring content.

The emulator found a Compose lazy-list lifecycle crash during Inbox conversion/tab switching. Tab lists now have separate composition identity and stable row keys/content types; the repeated transition passes. Screenshots use Android's additional test output collection before test-app cleanup.

The implementation is merged in [PR #3](https://github.com/tj123456fs/punla/pull/3), merge commit `166a73c75d5f28ec3cbfc3773b3c4a3aa4fc7794`. The delivered APK is the build artifact from the verified CI run. Production code is identical to the tested source; this report is a documentation-only follow-up.

## Delivered build and signing

- `Punla_3.6.0_debug.apk`: 66,843,703 bytes.
- SHA-256: `73ee97a2868439204f1400dc0fb6247ecd6037c1691e1c35777efa523a1c9728`.
- Signer certificate SHA-256: `c2b8a2a8ab5f29feb634e7c214c772d9b4619aec5dfa8a66e3b52a3bb4c37e92`.
- The debug signer differs from the delivered 3.5.1 APK. An in-place install over that APK will not succeed with this signer. Keep a verified backup before any uninstall/reinstall, or build this source using the installation's original signing key. No stable production signing key was configured in this session.


## Remaining roadmap work

This is the first UI/UX implementation slice, not completion of the six-phase plan. Next: course-first Study navigation and reader typography, a recovery proposal with exact times and undo, broader populated-screen accessibility/landscape testing, and physical-device reliability gates in STUDENT_OS_PHASE_STATUS.md. Current recovery confirmation explains the operation; it does not show an exact proposed schedule or offer undo.

## iOS direction

An iPhone version is feasible. The current app is Android-specific Kotlin/Jetpack Compose, Room, Glance and Android background services. Extracting pure planning and study engines into Kotlin Multiplatform could share domain logic with a SwiftUI iOS app. Storage, notifications, widgets, maps and background behavior need iOS implementations. A Mac/Xcode build/sign/test workflow is needed. No iOS target is created in this release. Java/Android dependencies in otherwise reusable engines need refactoring before moving to common code. References: [JetBrains integration tutorial](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-integrate-in-existing-app.html), [Apple Xcode](https://developer.apple.com/xcode/).

# Punla Session 35A — Interaction Performance Hotfix

## Fixes

- Throttled full-screen procedural background animation redraws to 15–25 FPS depending on style instead of driving them at the device refresh rate.
- Prevented the root app composition from observing every Pomodoro second update when it only needs the running/not-running flag.
- Prevented the Pomodoro ticker from publishing duplicate `remainingSeconds` values four times per second.
- Fixed backup validation so correct-but-UNSURE mistake-review records with `timesMissed = 0` are valid, while also requiring `retryAt >= missedAt`.

These changes target app-wide scroll/tap jank without removing animated backgrounds or the live Pomodoro timer.

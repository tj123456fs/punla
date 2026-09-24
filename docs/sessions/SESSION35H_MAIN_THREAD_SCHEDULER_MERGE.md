# Session 35H — Main-thread scheduler merge

Merged the Session 35G scheduler-threading fix into the current Phase 0D build without regressing the validation harness.

## Changes
- `CoreReliabilityScheduler.ensureScheduled()` is now suspendable and performs scheduler work off the main thread.
- `PunlaApplication` verifies persistent jobs from an application coroutine instead of blocking startup.
- Removed the duplicate Activity startup scheduler call; Application remains the single cold-start entry point.
- `PomodoroBootReceiver` moves reboot/package/time recovery work under `goAsync()` and a background coroutine.
- System Health `Repair jobs` runs asynchronously.
- Preserved the 20-minute Battery/Idle probe, 7-day stability soak, and `PunlaDiagnostics.fatal()` crash counter from Phase 0D.

## Compatibility
- App: 2.9.6 / versionCode 30
- Room: unchanged
- Backup schema: unchanged

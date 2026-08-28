# Session 34c — Quiz Attempt Save Lifecycle Fix

## Symptom
After completing a quiz, the result screen could show `The coroutine scope left the composition` and `Retry save`.

## Root cause
The `LaunchedEffect` responsible for persisting a quiz attempt was created only while `!attemptSaving` was true. The effect immediately set `attemptSaving = true`, which caused recomposition, removed the effect from composition, and cancelled its own coroutine.

## Fix
- The save `LaunchedEffect` now has a stable lifecycle based on quiz completion, attempt ID, and an explicit retry token.
- `attemptSaving` is now UI state only and no longer controls whether the effect exists in the composition.
- Retry increments an explicit token to rerun persistence.
- `CancellationException` is rethrown from the ViewModel instead of being converted to a visible `Result.failure`.

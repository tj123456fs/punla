# Session 34g — Campus Map Build Fix

## Failure
GitHub Actions failed `:app:compileDebugKotlin` at `CampusFullMapScreen.kt:576` with `Unresolved reference: context`.

## Root cause
`CampusFullMapView` referenced a `context` variable that belonged to the parent composable and was not defined in the private map composable's scope. The reference only appeared in the `AndroidView.update` block.

## Fix
Use the `MapView` instance supplied to `AndroidView.update` directly:

```kotlin
LocationComponentActivationOptions.builder(it.context, style).build()
```

This avoids an unnecessary fallback and gives MapLibre the active view context.

## Scope
Build fix only. No schema, behavior, study-flow, or backup-format changes.

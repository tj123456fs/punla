# Punla Session 35B — Smooth Interaction Pass

## Goal

Reduce app-wide interaction jank (scrolling, taps, tab switches, and appearance changes) without removing Punla's animated backgrounds or existing features.

## Applied optimizations

### 1. Isolated the animated background from foreground UI drawing

The procedural background now lives in its own full-screen `graphicsLayer` sibling behind the app content instead of being a `drawBehind` modifier on the same Box that owns the entire navigation/content tree.

Why: background animation invalidations can now re-record their own drawing layer without unnecessarily re-recording foreground cards, text, lists, and navigation chrome.

### 2. Background animation yields while lists are scrolling

A root `NestedScrollConnection` detects active Compose scrolling. While content is moving, Punla freezes only the decorative background clock. It resumes after scrolling/flinging finishes.

Why: the user's finger-controlled 60/90/120 Hz interaction gets priority over non-essential atmospheric animation.

### 3. Animation updates are aligned to display frames

The background ticker now uses `withFrameNanos` and publishes only at each style's target cadence (~15–25 FPS), rather than waking from arbitrary `delay()` boundaries.

Why: this avoids invalidating drawing immediately after a vsync and keeps redraw timing more predictable.

### 4. Lifecycle-aware Flow collection

All Compose screen `StateFlow`/`Flow` subscriptions were migrated from `collectAsState()` to `collectAsStateWithLifecycle()` (68 subscriptions).

Why: inactive navigation destinations and backgrounded UI stop collecting data until their lifecycle is active again, reducing unnecessary work and recompositions.

Dependency added:

- `androidx.lifecycle:lifecycle-runtime-compose:2.8.4`

This matches the project's existing Lifecycle 2.8.4 line.

### 5. Home-screen widget refreshes moved off the UI dispatcher

`WidgetRefresher.refreshAll()` now runs Glance refresh work on `Dispatchers.Default` and serializes refresh bursts with a `Mutex`.

Why: changing a theme/background or saving app data should not make the same interaction frame also do widget composition/background-bitmap work.

### 6. Cached Settings background thumbnails

The frozen background previews in Settings now render once into a tiny cached bitmap using `drawWithCache`, instead of re-running each procedural painter whenever that subtree redraws.

### 7. Theme objects are memoized

Resolved palette, Material color scheme, and typography are now cached with `remember()` using only their actual inputs as keys.

Why: custom HCT/material-kolor palette generation and Material color-scheme construction should run only when the relevant appearance setting changes.

### 8. Theme chooser uses a LazyRow

The appearance theme strip now composes only visible theme cards instead of eagerly composing the entire horizontal catalog.

### 9. Lighter bottom-tab transitions

Sibling bottom tabs now use a short fade instead of fading + scaling the entire destination tree. Hierarchical push/pop navigation keeps its slide/fade motion.

## Research basis

The implementation follows current Android guidance around:

- isolating independently invalidated drawing with `graphicsLayer`;
- deferring rapidly changing visual state to drawing/layout phases;
- caching expensive drawing objects/work;
- lifecycle-aware Flow collection with `collectAsStateWithLifecycle`;
- using lazy containers for larger scrollable collections;
- keeping Release/R8 builds for representative performance testing.

Baseline Profiles were also reviewed. They remain a strong next step for first-run and interaction performance, but a correct app-specific profile should be generated and measured with Macrobenchmark on an Android device/emulator. This patch deliberately does not ship a guessed/fake profile.

## Compatibility

- No Room schema change.
- No backup schema change.
- No data migration.
- Existing Session 35A timer/background/backup fixes remain intact.
- App version bumped to **2.9.1** (`versionCode 25`).

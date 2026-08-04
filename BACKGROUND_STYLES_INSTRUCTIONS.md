# Punla Background Styles

This document describes the current procedural-background system in Punla 1.5.

## Available styles

1. **Theme Match** — automatically selects a signature effect for the active theme.
2. **Minimal** — flat color; no animation.
3. **Ambient** — drifting theme-colored light.
4. **Rain** — wind-blown layered drops with depth and small splashes.
5. **Aurora** — translucent flowing ribbons.
6. **Ocean Waves** — layered sine-wave fills with parallax motion.
7. **Fireflies** — wandering pulsing lights.
8. **Sakura** — rotating petals with breeze and depth.
9. **Snow** — layered falling flakes with sway.
10. **Bubbles** — rising translucent rings and highlights.
11. **Starfield** — twinkling stars with subtle orbital drift.
12. **Paper Grain** — static field-notebook texture.

## Architecture

### Preference and automatic mapping

- `BackgroundStyle` and `resolveForTheme()` live in `data/PunlaRepository.kt`.
- `PunlaRepository.backgroundStyle` persists the enum's stable lowercase key.
- Unknown or missing legacy values still resolve to `AMBIENT`, preserving the previous default.
- `PunlaViewModel.updateBackgroundStyle()` updates Compose state and refreshes widgets.

### One renderer for every surface

- `ui/theme/BackgroundPainters.kt` contains all drawing algorithms as plain Android `Canvas` functions.
- `ui/theme/BackgroundRenderer.kt` exposes `paintBackgroundFrame()`, the single dispatcher for all effects.
- `ui/theme/AmbientBackground.kt` supplies live elapsed time to that dispatcher.
- `SettingsScreen.kt` calls the same dispatcher at a fixed phase for preview thumbnails.
- `widget/WidgetBackground.kt` calls `renderBackgroundBitmap()` to produce one frozen frame because Glance cannot animate a normal Compose Canvas.

This structure prevents the app, previews, and widgets from drifting into three separate visual implementations.

## Performance rules

- Only the selected live background gets an animation clock.
- Settings previews are static snapshots, not twelve simultaneous animations.
- Particle placement is deterministic through fixed random seeds.
- Minimal and Paper Grain never start an infinite transition.
- No video, WebView, Lottie asset, game engine, or new runtime dependency is required.
- Rain depth is represented with speed, alpha, width, length, and a brighter head rather than one new gradient shader per drop per frame.

## Theme Match table

| Theme | Background |
|---|---|
| Field Notebook, Paper & Ink, Library Mode | Paper Grain |
| Aurora Borealis, Cyber Neon | Aurora |
| Ocean Depths | Ocean Waves |
| Forest Mist, Lavender Night | Fireflies |
| Pastel Bloom | Sakura |
| Frost Glass | Snow |
| Galaxy | Starfield |
| Coffee Shop, Lofi Night | Rain |
| Sunset Sky, Golden Dawn, Custom | Ambient |

## Adding another style

1. Add an enum value to `BackgroundStyle`.
2. Add its label and description to `PunlaBackgroundCatalog`.
3. Add a `paintXxx()` function to `BackgroundPainters.kt`.
4. Add one branch to `paintBackgroundFrame()`.
5. Decide whether Theme Match should use it.
6. Extend `BackgroundEngineTest.kt` when the mapping is intentional.

No screen-specific rendering code should be added.

## Verification checklist

- Switch each style in Settings and confirm the app updates immediately.
- Test light and dark mode with at least three contrasting themes.
- Confirm Theme Match changes effects when the theme changes.
- Confirm widgets refresh after a background or theme change.
- Confirm Settings previews remain static while the selected app background animates.
- Verify Minimal and Paper Grain use no continuous animation.
- Rotate the device and confirm particle layouts stay deterministic.
- Run `BackgroundEngineTest.kt` and the GitHub Actions Android build.

## Attribution

The updated rain design was informed by the Apache-2.0 continuous-rain sample in `skydoves/compose-animations`. Punla's renderer was substantially rewritten to support deterministic widget frames and theme-aware shared rendering. See `THIRD_PARTY_NOTICES.md`.

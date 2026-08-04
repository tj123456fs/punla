# Punla Procedural Background Engine

**Version:** 1.5 (`versionCode 6`)  
**Implemented:** 2026-08-04

## What changed

Punla now uses one deterministic renderer for three surfaces:

1. The live Jetpack Compose application background
2. Static previews in Settings
3. Frozen home-screen widget frames

The central dispatcher is `paintBackgroundFrame()` in `BackgroundRenderer.kt`. Each effect is drawn by a plain Android `Canvas` painter in `BackgroundPainters.kt`, while `AmbientBackground.kt` supplies the live time value.

## Available styles

- Theme Match
- Minimal
- Ambient
- Rain
- Aurora
- Ocean Waves
- Fireflies
- Sakura
- Snow
- Bubbles
- Starfield
- Paper Grain

## Theme Match rules

| Theme | Effect |
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

## Performance decisions

- No video backgrounds, WebViews, or game engine.
- No new runtime dependency was added.
- Particle layouts use fixed seeds, avoiding state duplication between app and widgets.
- Only the selected live style runs an infinite animation clock.
- Settings previews are frozen frames, so opening Settings does not animate every option simultaneously.
- Minimal and Paper Grain remain static.
- Rain avoids allocating a gradient shader for every drop on every frame; depth is represented through alpha, width, speed, length, and a brighter head segment.

## Source acknowledgement

The rain design was informed by the Apache-2.0 `AnimationExample21.kt` example from `skydoves/compose-animations`. See `THIRD_PARTY_NOTICES.md` and `third_party/licenses/compose-animations-APACHE-2.0.txt`.

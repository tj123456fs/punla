# Punla Atmospheric Background Engine 2.0

**Release:** Punla v2.5 (`versionCode 16`)  
**Session:** 31  
**Focus:** motion quality, stationary-camera feel, and more natural atmosphere

## Why this pass was needed

The original procedural effects were intentionally lightweight, but two effects had visual motion problems on a real phone:

- **Rain** linked each drop's horizontal position to its vertical fall progress. Because most drops drifted in the same direction as they fell, the whole background could feel like the user/camera was moving through the rain.
- **Aurora** was drawn as three thick stroked paths. Even with wide strokes and alpha, the eye still read them as glowing squiggly lines instead of broad aurora curtains.

Session 31 changes the motion model rather than hiding those artifacts with different colors or speeds.

## Rain 2.0

`paintRain()` now uses a gravity-first model:

- Drops fall predominantly downward.
- Horizontal position is anchored to a stable seed and is **not** derived from fall progress.
- A tiny per-drop breeze oscillation replaces screen-wide horizontal travel.
- Three explicit depth layers are used:
  - far: faint, small, slow
  - middle: normal rainfall
  - near: sparse, longer, brighter streaks
- Only a minority of near drops can create bottom-edge splash cues.
- A subtle atmospheric veil adds depth without translating the scene.
- A short brighter drop tip preserves the feeling of local motion blur.

The result is designed around a stationary observer: the rain moves, not the camera.

## Aurora 2.0

`paintAurora()` no longer uses stroked paths.

Each aurora layer is now a **closed filled curtain** bounded by two separate low-frequency curves:

- four overlapping translucent curtains
- broad vertical falloff using multi-stop gradients
- independent top and bottom deformation
- slow shape evolution rather than lateral screen travel
- a narrower filled inner glow for each curtain
- opaque sky gradient underneath so live app, previews, and widget bitmaps render consistently

This produces broad luminous sheets instead of neon-looking squiggles.

## Supporting motion polish

The same pass also refined the other animated backgrounds while preserving their identities:

- **Ocean Waves** — slower swell frequencies, lower amplitudes, secondary wave detail, less stripe-like motion.
- **Fireflies** — smaller meandering orbits, visual pauses, softer independent brightness pulses.
- **Sakura** — curved two-frequency breeze paths and gentler rotation.
- **Snow** — slower fall with layered flutter instead of nearly straight paths.
- **Bubbles** — dual-frequency wobble and slight oval shape breathing.
- **Starfield** — much smaller and slower drift so twinkling provides most of the perceived motion.

## Architecture preserved

No new dependency or database migration is required.

The same shared rendering pipeline remains in use:

- live Compose background
- Settings preview frame
- frozen home-screen widget frame

All three continue to call `paintBackgroundFrame()` and the same painters in `BackgroundPainters.kt`, preventing the app and previews from drifting into separate implementations.

## Performance notes

- Particle layouts remain deterministic through fixed seeds.
- No bitmap/video background loop was introduced.
- Rain still avoids per-drop gradient allocation.
- Aurora uses a bounded four-curtain/eight-path draw per frame.
- Only the selected live background uses the app's animation clock.
- Static styles remain static.

## Validation

Session 31 validation includes:

- direct Kotlin compilation of `BackgroundPainters.kt` against Android/Compose API stubs
- full Kotlin syntax parsing for the project
- Android XML parsing
- source invariants verifying rain x-position is no longer coupled to fall progress
- source invariants verifying Aurora uses filled closed curtains rather than wide stroked paths
- version/package integrity checks

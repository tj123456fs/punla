# Background Styles — What's Implemented

This documents the finished Background Style feature: a per-person choice of
**Ambient** (drifting blob wash), **Starfield** (twinkling + drifting stars),
or **Minimal** (flat color), applied consistently across the app *and* the
three home-screen widgets. It supersedes the two earlier partial passes —
this is the combined, current state of the code.

---

## 1. What's in place

### Setting & state
- `BackgroundStyle` enum (`MINIMAL, AMBIENT, STARFIELD`) — `data/PunlaRepository.kt`.
- `PunlaRepository.backgroundStyle` — `SharedPreferences`-backed, same shape
  as `themePreset`/`fontChoice`.
- `PunlaViewModel.backgroundStyle` + `updateBackgroundStyle()` — Compose-observable
  mirror, same `mutableStateOf` pattern as the other theme settings.

### Drawing logic (shared, one source of truth)
- `ui/theme/BackgroundPainters.kt` — `paintStarfield()` and `paintAmbientWash()`,
  both plain-`Canvas` functions with no Compose dependency. This is the only
  place the actual star/blob math lives; everything else just calls in.
- `ui/theme/AmbientBackground.kt` — the **live** Compose modifiers
  (`ambientGradientBackground()`, `starfieldBackground()`) that drive an
  animated `t` via `rememberInfiniteTransition` and hand it to the painters
  above through `drawIntoCanvas { ... }.nativeCanvas`. Also exposes
  `Modifier.appBackground(style, darkTheme)`, the single dispatch point
  `MainActivity` calls instead of hardcoding one style.
- `ui/theme/BackgroundRenderer.kt` — the **widget** entry point,
  `renderBackgroundBitmap()`. Glance has no live `DrawScope`, so this calls
  the same painters with one frozen `t` and returns a `Bitmap` (or `null` for
  Minimal, which just falls back to a flat `ColorProvider` fill).
- `widget/WidgetBackground.kt` — `widgetBackgroundImageProvider()`, called
  once per `provideGlance` in each widget. Resolves the palette explicitly
  via `resolvePalette()` (the `LocalPunlaPalette` CompositionLocal doesn't
  reach into Glance's separate composition) and hands back an `ImageProvider`
  wrapping the rendered bitmap.

### Wiring
- **App**: `MainActivity.kt` — `Box(Modifier.fillMaxSize().appBackground(vm.backgroundStyle, darkTheme))`.
- **Settings**: `ui/screens/SettingsScreen.kt` — a "BACKGROUND" card (next to
  "APPEARANCE") with a `BackgroundStyleOptionRow` per option, mirroring the
  existing `FontOptionRow` selected/unselected treatment.
- **Widgets**: `NextDeadlineWidget.kt`, `BudgetWidget.kt`, `NextClassWidget.kt`
  each call `widgetBackgroundImageProvider()` inside `provideGlance` and use
  it as their `GlanceModifier.background(...)`, sized to the widget's actual
  `LocalSize.current`. They also now resolve every other color role (text,
  urgent-red, etc.) from the same `resolvePalette()` call instead of the old
  hardcoded Field Notebook literals — so a person on Ocean/Sunset/Orchid/Slate,
  or a custom seed color, sees their own palette on the home screen too, not
  just in the app.
- **Refresh**: `WidgetRefresher.refreshAll()` already fires on every data
  write; `updateBackgroundStyle()` (and the theme-preset updater) also call
  it directly, so widgets pick up a style/palette change within seconds
  instead of waiting for the next deadline/expense/class edit.

---

## 2. This pass: stars now move, not just twinkle

The original Starfield only pulsed each star's *alpha*. Per your ask, stars
now **drift** in addition to twinkling — `paintStarfield()` in
`BackgroundPainters.kt` gives each star a small, slow elliptical wander
around its base position, on top of its existing twinkle:

```kotlin
val driftRadiusPx = rng.nextFloat() * 3.2f + 1.2f   // ~1–4px wander
val driftSpeed = rng.nextFloat() * 0.12f + 0.04f    // much slower than twinkle
val driftPhase = rng.nextFloat() * (2f * Math.PI.toFloat())

val dx = cos(t * driftSpeed + driftPhase) * driftRadiusPx
val dy = sin(t * driftSpeed * 0.7f + driftPhase * 1.3f) * driftRadiusPx * 0.6f
```

Design choices, and why:
- **Drift is much slower than twinkle** (period of ~40 seconds to a few
  minutes, vs. a few seconds for twinkle) and **small** (a few px). The
  original design note in the spec — "twinkle, don't drift, or it reads as
  particles" — is still the right instinct for a *large, fast* drift; a
  small, slow one instead reads as "a sky that's quietly alive" rather than
  "confetti" or "rain." If it ever feels too busy, turning `driftRadiusPx`
  down (or removing the `* 0.6f`/`0.7f` asymmetry so it's circular instead
  of elliptical) is the first knob to try.
- **One shared function, two callers, unchanged.** Because the drift lives
  inside `paintStarfield()` itself, both the live app background and the
  frozen widget bitmap get it for free — the widget's `FIXED_WIDGET_PHASE`
  just becomes a fixed *offset* per star instead of a fixed *position*, same
  relationship it already had with twinkle.
- **Independent RNG draws from twinkle's**, so drift speed/phase per star
  isn't correlated with its twinkle speed/phase — a star that twinkles fast
  doesn't necessarily drift fast too, which keeps the field looking organic
  rather than obviously formulaic.

Nothing else about the feature changed in this pass — this was purely the
`paintStarfield()` math; no new settings, no new files.

---

## 3. Testing checklist

- [ ] Switching Background style in Settings updates the running app
      immediately (no restart needed).
- [ ] Starfield stars visibly drift now, not just twinkle — watch one star
      for ~30–60s; it should trace a small, slow loop, not sit pinned in
      place.
- [ ] Drift doesn't read as "falling" or "blowing" in one direction — it
      should look like gentle wandering, not rain/confetti/wind.
- [ ] Starfield looks right in both light and dark mode, across at least two
      color presets.
- [ ] No visible "jump" or reshuffle of star *base* positions on rotation or
      backgrounding/foregrounding (only the twinkle+drift phase should carry
      on smoothly, not reset).
- [ ] Minimal mode has no animation and no bitmap generation (check via
      Developer Options → Profile GPU rendering, not just by eye).
- [ ] Setting persists across process death (force-stop, reopen).
- [ ] All three widgets update within a few seconds of changing Background
      style or color palette in Settings — not just on the next data edit.
- [ ] Resizing a widget regenerates its background at the new size, no
      stretching/cropping.
- [ ] The frozen widget starfield frame looks populated (not "all stars near
      zero alpha") and has a plausible drift offset, not a jarring one.
- [ ] Switching to Ocean/Sunset/Orchid/Slate, or a custom seed color, updates
      widget text and background colors, not just the in-app UI. The
      "urgent" deadline red stays legible against the widget background at
      both a light and dark custom seed color.
- [ ] Field Notebook (default) is pixel-identical to production before this
      feature existed, for anyone who leaves everything on defaults.
- [ ] No new periodic/alarm-driven wake-ups — widget backgrounds only
      regenerate on data writes, style/palette changes, and resizes.

---

## 4. Two more styles, now built: Paper Grain and Rain

Pulled the two cheapest ideas off the "future" list below into real
`BackgroundStyle` values — same shape as Ambient/Starfield, no new
architecture: a `paintXxx()` in `BackgroundPainters.kt`, a live modifier in
`AmbientBackground.kt`, a case in `renderBackgroundBitmap()`, a
`BackgroundStyle` enum value, a prefs string in `PunlaRepository.kt`, and a
`BackgroundStyleOptionRow` in Settings.

- **Paper Grain** (`paintPaperGrain`) — a faint, slightly-jittered dot grid
  over the flat theme background. True to the doc's original pitch, this one
  has **no animation at all** — no `rememberInfiniteTransition`, nothing.
  It's drawn with a single `Canvas.drawPoints()` call (one batched draw
  covering every dot) rather than one `drawCircle()` per dot, since a
  several-thousand-dot grid is the one part of "cheapest possible option"
  that could otherwise stop being cheap.
- **Rain** (`paintRain`) — thin, low-opacity diagonal streaks falling at a
  steady rate. Each drop gets its own random x position, fall speed
  (±25–50% off a base rate), and phase offset, so [paintRain]'s `tSeconds`
  input (real elapsed seconds, not a 0–2π angle like the ambient wash) wraps
  per-drop rather than all 70 drops visibly resetting to the top at once.
  Uses the same "very long single loop" trick as Starfield — the live
  modifier's `animateFloat` counts up for 4,000,000ms before it ever
  restarts, so that one eventual wrap is imperceptible.

Both reuse the existing palette roles (`lineDark`/`lineLight` for grain,
`lineDark`/`bark` for rain streaks) rather than introducing new ones, and
both get a widget bitmap for free through the same `renderBackgroundBitmap()`
dispatch every other style already goes through — `FIXED_RAIN_PHASE` picks
one arbitrary "seconds elapsed" value so the frozen widget frame shows drops
spread across the fall path instead of bunched at the top.

Not built from this pull: Falling Leaves, Fireflies, Growing Vines, Aurora
Wash, Topographic Contours, Koi Pond Ripples — still just ideas, listed
below.

---

## 5. Future background style ideas (not built yet)

Parking these here rather than in code comments, since none of them are
implemented — just a menu to pick from later, roughly cheapest-to-build to
fanciest. Given Punla's field-notebook / growing-things identity, the
on-theme options are the better fit over generic "pretty background" effects.
(Rain and Paper Grain, formerly on this list, are now built — see section 4.)

**On-theme (fits the agri/notebook vibe)**
- **Falling leaves** — small leaf shapes drifting down with a gentle sine-wave
  sway, in `leaf`/`bark`/`mango` tones. Very on-brand given the app name and
  palette; structurally close to Starfield (same infinite-transition
  pattern), just animating y-position + x-sway instead of alpha/drift.
- **Fireflies** — glowing dots that wander slowly and pulse in brightness — a
  starfield cousin with wandering motion instead of a fixed field (the drift
  work in section 2 is most of the way there already). Reads as "night in
  the fields," pairs well with dark mode.
- **Growing vines** — a procedurally drawn vine creeping up from the bottom
  edge with small leaf nodes, looping slowly. More interesting, but a bigger
  lift (Bezier curves along a path rather than a fixed dot field).

**Mood / general**
- **Aurora wash** — a slow soft color-band drift near the top of the screen;
  a banded cousin of the existing ambient blobs, low incremental cost.
- **Topographic contour lines** — faint concentric wavy lines drifting
  slowly, like a contour map. Nice tie-in since Campus Map already does
  custom Canvas drawing.
- **Koi pond ripples** — soft expanding rings fading out, spawning at random
  points. Calming, but more of a real particle system (rings need a
  spawn/despawn lifecycle, unlike the fixed star/leaf lists above).

If/when one of these gets built, it should slot into the same shape as
Ambient/Starfield: one `paintXxx()` function in `BackgroundPainters.kt`, a
live modifier in `AmbientBackground.kt`, a case in `renderBackgroundBitmap()`,
and a new `BackgroundStyle` enum value — no new architecture needed.

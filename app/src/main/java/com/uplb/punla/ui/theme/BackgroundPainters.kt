package com.uplb.punla.ui.theme

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Plain-Canvas drawing logic shared by the live Compose backgrounds
 * ([ambientGradientBackground], [starfieldBackground] in AmbientBackground.kt)
 * and the frozen-frame widget renderer (BackgroundRenderer.kt). Neither of
 * those call sites duplicates this math — `provideGlance` has no
 * `DrawScope`, so the widget side needs a raw [android.graphics.Canvas]
 * entry point, and the live side reaches it via `drawIntoCanvas { ... }.nativeCanvas`.
 */

/** Same StarSpec/seed/twinkle+drift math regardless of caller — a raw
 * [Canvas] + a `t` (either animated, from the live modifier, or frozen,
 * from the widget renderer) instead of a `DrawScope` + a live-only
 * animated one.
 *
 * Each star does two independent things over time:
 * - **Twinkles**: alpha oscillates per-star (own speed + phase).
 * - **Drifts**: the star also wanders in a small ellipse around its base
 *   position, at a much slower, independent speed/phase than its twinkle.
 *   The drift radius is deliberately tiny (a few px) and the speed
 *   deliberately slow — enough to read as "a living night sky" rather
 *   than "particles blowing across the screen." A star that only twinkles
 *   in place can look static/frozen even while animating; a small wander
 *   makes the motion legible without turning the field into rain or
 *   confetti.
 *
 * On the widget side, [t] is a single frozen value — the drift there just
 * becomes a fixed (rather than moving) offset from each star's base
 * position, same "one frame of the live drawing" relationship the twinkle
 * alpha already has.
 */
fun paintStarfield(
    canvas: Canvas,
    widthPx: Float,
    heightPx: Float,
    t: Float,
    starColorArgb: Int,
    accentColorArgb: Int,
    baseColorArgb: Int,
    starCount: Int = 80,
) {
    canvas.drawColor(baseColorArgb)
    val rng = Random(20260711) // fixed seed — same star layout every draw
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    repeat(starCount) {
        val xFrac = rng.nextFloat()
        val yFrac = rng.nextFloat()
        val radiusPx = rng.nextFloat() * 2.2f + 0.8f
        val phase = rng.nextFloat() * (2f * Math.PI.toFloat())
        val speed = rng.nextFloat() * 1.5f + 0.5f
        val accent = rng.nextFloat() < 0.15f

        // Drift: a slow, small elliptical wander around the star's base
        // point — independent seed draws from the twinkle phase/speed above
        // so the two motions don't sync up and read as one repetitive cycle.
        val driftRadiusPx = rng.nextFloat() * 3.2f + 1.2f
        val driftSpeed = rng.nextFloat() * 0.12f + 0.04f // much slower than twinkleSpeed
        val driftPhase = rng.nextFloat() * (2f * Math.PI.toFloat())

        val twinkle = (sin(t * speed + phase) * 0.5f + 0.5f).coerceIn(0f, 1f) * 0.75f + 0.15f
        val dx = cos(t * driftSpeed + driftPhase) * driftRadiusPx
        val dy = sin(t * driftSpeed * 0.7f + driftPhase * 1.3f) * driftRadiusPx * 0.6f

        paint.color = if (accent) accentColorArgb else starColorArgb
        paint.alpha = (twinkle * 255).toInt()
        canvas.drawCircle(widthPx * xFrac + dx, heightPx * yFrac + dy, radiusPx, paint)
    }
}

/**
 * Raw-Canvas port of the three-blob Lissajous wash: same periodicity
 * reasoning as the live [ambientGradientBackground] modifier (each blob's
 * x/y motion uses a whole-number multiple of the driving angle [t], so any
 * given [t] — animated or frozen — is a valid, well-formed frame, never a
 * "mid-snap" one). [t] defaults to a fixed mid-cycle phase so a caller that
 * doesn't care about animation (the widget renderer) gets a populated-looking
 * frame for free; the live modifier passes its own animated value instead.
 */
fun paintAmbientWash(
    canvas: Canvas,
    widthPx: Float,
    heightPx: Float,
    palette: PunlaPalette,
    isDark: Boolean,
    t: Float = FIXED_AMBIENT_PHASE,
) {
    val base = if (isDark) palette.darkBg else palette.paper
    canvas.drawColor(base.toArgb())

    val w = widthPx
    val h = heightPx
    val maxDim = if (w > h) w else h
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun blob(
        xFreq: Int,
        yFreq: Int,
        phaseX: Float,
        phaseY: Float,
        xRange: Float,
        yRange: Float,
        radiusFrac: Float,
        colorArgb: Int,
    ) {
        val cx = w * 0.5f + cos(t * xFreq + phaseX) * w * xRange
        val cy = h * 0.5f + sin(t * yFreq + phaseY) * h * yRange
        val r = maxDim * radiusFrac
        paint.shader = RadialGradient(cx, cy, r, colorArgb, colorArgb and 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
    }

    // Same three blobs/paths as the live version — container-role colors at
    // low alpha over the flat base, matching Theme.kt's primary/tertiary/
    // secondaryContainer mapping (leafBg/mangoBg/maroonBg) 1:1 so a widget
    // bitmap and the in-app wash never drift apart.
    blob(
        xFreq = 1, yFreq = 1, phaseX = 0f, phaseY = 1.4f,
        xRange = 0.34f, yRange = 0.24f, radiusFrac = 0.55f,
        colorArgb = (if (isDark) palette.leafBgDark else palette.leafBgLight).copy(alpha = 0.30f).toArgb(),
    )
    blob(
        xFreq = 1, yFreq = 2, phaseX = 2.1f, phaseY = 0.5f,
        xRange = 0.3f, yRange = 0.3f, radiusFrac = 0.5f,
        colorArgb = (if (isDark) palette.mangoBgDark else palette.mangoBgLight).copy(alpha = 0.22f).toArgb(),
    )
    blob(
        xFreq = 2, yFreq = 1, phaseX = 4.2f, phaseY = 3f,
        xRange = 0.26f, yRange = 0.2f, radiusFrac = 0.42f,
        colorArgb = (if (isDark) palette.maroonBgDark else palette.maroonBgLight).copy(alpha = 0.18f).toArgb(),
    )
    paint.shader = null
}

/** Mid-cycle default for [paintAmbientWash]'s [t] — doesn't need to mean
 * anything beyond "a phase where all three blobs read as populated," same
 * spirit as `FIXED_WIDGET_PHASE` in BackgroundRenderer.kt. */
private const val FIXED_AMBIENT_PHASE = 2.6f

/**
 * Static "field notebook" grain: a faint, slightly-jittered dot grid over
 * the flat theme background — no animation at all, per the design note in
 * BACKGROUND_STYLES_INSTRUCTIONS.md ("cheapest possible option, zero
 * battery cost, and arguably strengthens the 'field notebook' identity more
 * directly than any animated effect would"). The live modifier
 * ([Modifier.paperGrainBackground] in AmbientBackground.kt) still redraws
 * this on every recomposition of its Box the same as a flat color fill
 * would — the "zero cost" is relative to STARFIELD/AMBIENT/RAIN's
 * `rememberInfiniteTransition`, not "never redraws."
 *
 * Drawn with a single [Canvas.drawPoints] call rather than one `drawCircle`
 * per dot — thousands of individual circle draws would be the one part of
 * this feature that *isn't* cheap; one batched point draw is.
 */
fun paintPaperGrain(
    canvas: Canvas,
    widthPx: Float,
    heightPx: Float,
    palette: PunlaPalette,
    isDark: Boolean,
) {
    val base = (if (isDark) palette.darkBg else palette.paper).toArgb()
    canvas.drawColor(base)
    if (widthPx <= 0f || heightPx <= 0f) return

    val dotColorArgb = (if (isDark) palette.lineDark else palette.lineLight).copy(alpha = 0.4f).toArgb()
    val rng = Random(20260711) // fixed seed — same grain every draw, same reasoning as paintStarfield
    val spacingPx = 26f
    val jitterFrac = 0.3f // keeps the grid reading as "grain," not a perfect lattice

    val cols = (widthPx / spacingPx).toInt() + 1
    val rows = (heightPx / spacingPx).toInt() + 1
    val pts = FloatArray(cols * rows * 2)
    var i = 0
    var y = spacingPx / 2f
    while (y < heightPx) {
        var x = spacingPx / 2f
        while (x < widthPx) {
            val jx = (rng.nextFloat() - 0.5f) * spacingPx * jitterFrac
            val jy = (rng.nextFloat() - 0.5f) * spacingPx * jitterFrac
            if (i + 1 < pts.size) {
                pts[i] = x + jx
                pts[i + 1] = y + jy
                i += 2
            }
            x += spacingPx
        }
        y += spacingPx
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dotColorArgb
        style = Paint.Style.STROKE
        strokeWidth = 2.6f
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawPoints(pts, 0, i, paint)
}

/** Cycles-per-second for [paintRain]'s fall loop — one drop takes roughly
 * 1/[RAIN_FALL_HZ] seconds to cross from just-above-top to just-below-bottom
 * at its base speed of 1x (individual drops vary ±25-50% per their own
 * random `speed` draw, same per-star-speed-variance idea as [paintStarfield]). */
private const val RAIN_FALL_HZ = 0.7f

/**
 * Thin, low-opacity diagonal streaks falling at a steady rate — literal for
 * Los Baños rainy season, and (per the design note) the simplest of the
 * "future" ideas to actually draw: lines, not circles. [tSeconds] is real
 * elapsed seconds (either from the live modifier's `rememberInfiniteTransition`
 * or a single frozen value for the widget bitmap) — each drop derives its
 * own fall progress from it via an independent speed multiplier and phase
 * offset, so [tSeconds] wrapping around is never a single synchronized
 * "reset" of every drop at once.
 */
fun paintRain(
    canvas: Canvas,
    widthPx: Float,
    heightPx: Float,
    tSeconds: Float,
    palette: PunlaPalette,
    isDark: Boolean,
    dropCount: Int = 70,
) {
    val base = (if (isDark) palette.darkBg else palette.paper).toArgb()
    canvas.drawColor(base)
    if (widthPx <= 0f || heightPx <= 0f) return

    val streakColorArgb = (if (isDark) palette.lineDark else palette.bark).copy(alpha = 0.30f).toArgb()
    val rng = Random(20260711) // fixed seed — same drop layout every draw
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = streakColorArgb
        strokeWidth = 1.6f
        strokeCap = Paint.Cap.ROUND
    }

    val streakLenPx = heightPx * 0.045f
    val leanPx = streakLenPx * 0.22f // a gentle diagonal lean, not a gale

    repeat(dropCount) {
        val xFrac = rng.nextFloat()
        val speed = rng.nextFloat() * 0.5f + 0.75f
        val phase = rng.nextFloat()

        val cycle = tSeconds * RAIN_FALL_HZ * speed + phase
        val progress = cycle - kotlin.math.floor(cycle) // 0..1, wraps per-drop
        val headY = progress * (heightPx + streakLenPx) - streakLenPx
        val headX = widthPx * xFrac

        canvas.drawLine(headX, headY, headX - leanPx, headY - streakLenPx, paint)
    }
}

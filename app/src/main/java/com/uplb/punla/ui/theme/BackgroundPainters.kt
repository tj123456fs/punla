/*
 * Punla procedural background painters.
 *
 * The rain depth/speed design was informed by the Apache-2.0
 * skydoves/compose-animations AnimationExample21 sample and substantially
 * rewritten for Punla's deterministic app/widget renderer. See
 * THIRD_PARTY_NOTICES.md for attribution and the bundled license copy.
 */
package com.uplb.punla.ui.theme

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
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
        val driftRadiusPx = rng.nextFloat() * 1.6f + 0.5f
        val driftSpeed = rng.nextFloat() * 0.055f + 0.018f // nearly stationary; twinkle carries most of the motion
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

/**
 * Gravity-first layered rain. The previous renderer coupled horizontal travel
 * to each drop's vertical progress, which made the whole scene feel like the
 * camera was moving through rain. Rain 2.0 keeps every drop anchored to a
 * stable x-position and allows only a tiny independent breeze wobble.
 *
 * Three explicit depth layers provide parallax through size/alpha/speed rather
 * than screen-wide translation:
 * - far: small, faint, slow drops
 * - mid: the majority of visible rainfall
 * - near: sparse, longer streaks and occasional bottom-edge splashes
 */
fun paintRain(
    canvas: Canvas,
    widthPx: Float,
    heightPx: Float,
    tSeconds: Float,
    palette: PunlaPalette,
    isDark: Boolean,
    dropCount: Int = 108,
) {
    val baseColor = (if (isDark) palette.darkBg else palette.paper).toArgb()
    canvas.drawColor(baseColor)
    if (widthPx <= 0f || heightPx <= 0f) return

    // A very soft atmospheric veil gives the rain depth while keeping the
    // screen itself visually stationary.
    val hazeColor = (if (isDark) palette.leafBgDark else palette.leafBgLight)
        .copy(alpha = if (isDark) 0.10f else 0.07f)
        .toArgb()
    val hazePaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, heightPx,
            intArrayOf(withAlpha(hazeColor, 0), hazeColor, withAlpha(hazeColor, 0)),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, widthPx, heightPx, hazePaint)

    val rng = Random(20260711)
    val farColor = (if (isDark) palette.lineDark else palette.bark).toArgb()
    val nearColor = (if (isDark) palette.textDark else palette.ink).toArgb()
    val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    repeat(dropCount) {
        val xSeed = rng.nextFloat()
        val layerRoll = rng.nextFloat()
        val layer = when {
            layerRoll < 0.44f -> 0 // far
            layerRoll < 0.86f -> 1 // mid
            else -> 2              // near
        }
        val phase = rng.nextFloat()
        val wobblePhase = rng.nextFloat() * PI.toFloat() * 2f
        val speedJitter = 0.90f + rng.nextFloat() * 0.20f
        val splashEligible = layer == 2 && rng.nextFloat() < 0.28f

        val fallHz = when (layer) {
            0 -> 0.18f
            1 -> 0.28f
            else -> 0.40f
        } * speedJitter
        val progress = fractional(tSeconds * fallHz + phase)

        val streakLength = heightPx * when (layer) {
            0 -> 0.010f + rng.nextFloat() * 0.004f
            1 -> 0.017f + rng.nextFloat() * 0.006f
            else -> 0.027f + rng.nextFloat() * 0.010f
        }
        val margin = streakLength * 1.6f
        val headY = progress * (heightPx + margin * 2f) - margin

        // Crucially, x is NOT derived from progress. Breeze is a small local
        // oscillation so the observer remains visually stationary.
        val breezeAmplitude = widthPx * when (layer) {
            0 -> 0.0015f
            1 -> 0.0028f
            else -> 0.0042f
        }
        val breeze = sin(tSeconds * (0.24f + layer * 0.05f) + wobblePhase) * breezeAmplitude
        val headX = widthPx * xSeed + breeze

        val lean = streakLength * when (layer) {
            0 -> 0.025f
            1 -> 0.045f
            else -> 0.070f
        }
        val alpha = when (layer) {
            0 -> 40 + rng.nextInt(24)
            1 -> 72 + rng.nextInt(34)
            else -> 112 + rng.nextInt(46)
        }

        dropPaint.color = if (layer == 2) nearColor else farColor
        dropPaint.alpha = alpha
        dropPaint.strokeWidth = when (layer) {
            0 -> 0.65f
            1 -> 1.05f
            else -> 1.65f
        }
        canvas.drawLine(headX - lean, headY - streakLength, headX, headY, dropPaint)

        // A short brighter tip suggests local motion blur without making the
        // whole rain field smear in one direction.
        if (layer > 0) {
            dropPaint.alpha = (alpha * 0.54f).toInt()
            dropPaint.strokeWidth *= 1.12f
            canvas.drawLine(
                headX - lean * 0.12f,
                headY - streakLength * 0.12f,
                headX,
                headY,
                dropPaint,
            )
        }

        if (splashEligible && progress > 0.986f && headX in 0f..widthPx) {
            val splashProgress = ((progress - 0.986f) / 0.014f).coerceIn(0f, 1f)
            splashPaint.color = nearColor
            splashPaint.alpha = ((1f - splashProgress) * 86f).toInt()
            splashPaint.strokeWidth = 1.1f
            val radius = (2.2f + 5.2f * splashProgress) * max(widthPx, heightPx) / 900f
            canvas.drawOval(
                RectF(
                    headX - radius,
                    heightPx - radius * 0.24f,
                    headX + radius,
                    heightPx + radius * 0.24f,
                ),
                splashPaint,
            )
        }
    }
}

/** Broad, softly layered aurora curtains over the active theme background. */
fun paintAurora(
    canvas: Canvas,
    widthPx: Float,
    heightPx: Float,
    tSeconds: Float,
    palette: PunlaPalette,
    isDark: Boolean,
) {
    if (widthPx <= 0f || heightPx <= 0f) return

    val skyTop = (if (isDark) palette.darkBg else palette.paper).toArgb()
    val skyBottom = (if (isDark) palette.leafBgDark else palette.leafBgLight).toArgb()
    val skyPaint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, heightPx, skyTop, skyBottom, Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, widthPx, heightPx, skyPaint)

    // Each curtain is a filled translucent region bounded by two independently
    // deforming low-frequency curves. Nothing is stroked as a line, which
    // prevents the old "squiggly neon ribbon" look.
    val curtainColors = arrayOf(
        if (isDark) palette.leaf else palette.leafLight,
        if (isDark) palette.mangoDark else palette.mango,
        if (isDark) palette.leafLight else palette.leaf,
        if (isDark) palette.maroonDark else palette.maroon,
    )

    repeat(4) { index ->
        val phase = 0.85f + index * 1.47f
        val topBase = heightPx * (0.10f + index * 0.075f)
        val thickness = heightPx * (0.27f + index * 0.022f)
        val ampTop = heightPx * (0.025f + index * 0.006f)
        val ampBottom = heightPx * (0.040f + index * 0.007f)
        val speed = 0.030f + index * 0.006f
        val samples = 44

        fun topY(unit: Float): Float = topBase +
            sin(unit * PI.toFloat() * (1.15f + index * 0.12f) + tSeconds * speed + phase) * ampTop +
            sin(unit * PI.toFloat() * 2.4f - tSeconds * speed * 0.63f + phase * 0.7f) * ampTop * 0.28f

        fun bottomY(unit: Float): Float = topBase + thickness +
            sin(unit * PI.toFloat() * (1.02f + index * 0.10f) + tSeconds * speed * 0.72f + phase * 1.18f) * ampBottom +
            sin(unit * PI.toFloat() * 2.05f + tSeconds * speed * 0.44f + phase) * ampBottom * 0.22f

        val path = Path()
        for (sample in 0..samples) {
            val unit = sample / samples.toFloat()
            val x = widthPx * unit
            val y = topY(unit)
            if (sample == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (sample in samples downTo 0) {
            val unit = sample / samples.toFloat()
            path.lineTo(widthPx * unit, bottomY(unit))
        }
        path.close()

        val color = curtainColors[index]
        val peakAlpha = if (isDark) 92 - index * 8 else 58 - index * 5
        val curtainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                topBase - ampTop,
                0f,
                topBase + thickness + ampBottom,
                intArrayOf(
                    withAlpha(color.toArgb(), 0),
                    withAlpha(color.toArgb(), peakAlpha / 2),
                    withAlpha(color.toArgb(), peakAlpha),
                    withAlpha(color.toArgb(), peakAlpha / 2),
                    withAlpha(color.toArgb(), 0),
                ),
                floatArrayOf(0f, 0.16f, 0.43f, 0.72f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(path, curtainPaint)

        // A narrower inner curtain gives a soft luminous core while remaining
        // a filled surface rather than a stroke.
        val innerPath = Path()
        for (sample in 0..samples) {
            val unit = sample / samples.toFloat()
            val x = widthPx * unit
            val y = topY(unit) + thickness * 0.18f
            if (sample == 0) innerPath.moveTo(x, y) else innerPath.lineTo(x, y)
        }
        for (sample in samples downTo 0) {
            val unit = sample / samples.toFloat()
            val y = topY(unit) + thickness * 0.58f +
                sin(unit * PI.toFloat() * 1.35f + phase - tSeconds * speed * 0.38f) * ampBottom * 0.18f
            innerPath.lineTo(widthPx * unit, y)
        }
        innerPath.close()
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, topBase, 0f, topBase + thickness,
                intArrayOf(
                    withAlpha(color.toArgb(), 0),
                    withAlpha(color.toArgb(), if (isDark) 56 else 34),
                    withAlpha(color.toArgb(), 0),
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(innerPath, innerPaint)
    }
}


/** Layered ocean swells with slow, non-uniform phase motion. */
fun paintOceanWaves(
    canvas: Canvas,
    widthPx: Float,
    heightPx: Float,
    tSeconds: Float,
    palette: PunlaPalette,
    isDark: Boolean,
) {
    if (widthPx <= 0f || heightPx <= 0f) return
    val top = (if (isDark) palette.darkBg else palette.paper).toArgb()
    val bottom = (if (isDark) palette.leafBgDark else palette.leafBgLight).toArgb()
    val background = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, heightPx, top, bottom, Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, widthPx, heightPx, background)

    val waveColors = intArrayOf(
        (if (isDark) palette.leafLight else palette.leaf).copy(alpha = 0.15f).toArgb(),
        (if (isDark) palette.mangoDark else palette.mango).copy(alpha = 0.12f).toArgb(),
        (if (isDark) palette.maroonDark else palette.maroon).copy(alpha = 0.11f).toArgb(),
        (if (isDark) palette.leaf else palette.leafLight).copy(alpha = 0.19f).toArgb(),
    )

    repeat(4) { layer ->
        val baseline = heightPx * (0.55f + layer * 0.095f)
        val amplitude = heightPx * (0.020f + layer * 0.008f)
        val direction = if (layer % 2 == 0) 1f else -1f
        val phase = tSeconds * (0.055f + layer * 0.012f) * direction + layer * 0.9f
        val path = Path().apply { moveTo(0f, heightPx) }
        val samples = 56
        for (sample in 0..samples) {
            val x = widthPx * sample / samples
            val unit = sample / samples.toFloat()
            val primary = sin(unit * PI.toFloat() * (2.0f + layer * 0.18f) + phase) * amplitude
            val secondary = sin(unit * PI.toFloat() * (4.1f + layer * 0.25f) - phase * 0.42f) * amplitude * 0.22f
            path.lineTo(x, baseline + primary + secondary)
        }
        path.lineTo(widthPx, heightPx)
        path.close()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = waveColors[layer] })
    }
}


/** Warm lights that meander gently, pause visually, and pulse independently. */
fun paintFireflies(
    canvas: Canvas,
    widthPx: Float,
    heightPx: Float,
    tSeconds: Float,
    palette: PunlaPalette,
    isDark: Boolean,
    count: Int = 34,
) {
    canvas.drawColor((if (isDark) palette.darkBg else palette.paper).toArgb())
    if (widthPx <= 0f || heightPx <= 0f) return
    val rng = Random(20260804)
    val color = (if (isDark) palette.mangoDark else palette.mango).toArgb()
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    repeat(count) {
        val baseX = rng.nextFloat() * widthPx
        val baseY = rng.nextFloat() * heightPx
        val phase = rng.nextFloat() * PI.toFloat() * 2f
        val speed = 0.09f + rng.nextFloat() * 0.13f
        val orbit = (4f + rng.nextFloat() * 13f) * max(widthPx, heightPx) / 900f
        // Two very small orbits at unrelated frequencies create organic
        // meandering. The squared gate naturally spends longer near a pause.
        val gateRaw = sin(tSeconds * speed * 0.37f + phase) * 0.5f + 0.5f
        val gate = 0.24f + gateRaw * gateRaw * 0.76f
        val x = baseX + cos(tSeconds * speed + phase) * orbit * gate +
            sin(tSeconds * speed * 0.41f + phase * 1.7f) * orbit * 0.24f
        val y = baseY + sin(tSeconds * speed * 0.69f + phase * 1.31f) * orbit * 0.62f * gate
        val pulse = (sin(tSeconds * (0.62f + rng.nextFloat() * 0.55f) + phase) * 0.5f + 0.5f)
        val pulseSoft = pulse * pulse
        val radius = (1.3f + rng.nextFloat() * 1.7f) * max(widthPx, heightPx) / 900f

        glowPaint.color = color
        glowPaint.alpha = (18 + pulseSoft * 44).toInt()
        canvas.drawCircle(x, y, radius * 4.6f, glowPaint)
        glowPaint.alpha = (42 + pulseSoft * 72).toInt()
        canvas.drawCircle(x, y, radius * 2.15f, glowPaint)
        corePaint.color = color
        corePaint.alpha = (105 + pulseSoft * 145).toInt()
        canvas.drawCircle(x, y, radius, corePaint)
    }
}


/** Falling petals with depth, curved breeze paths, and rotation. */
fun paintSakura(
    canvas: Canvas,
    widthPx: Float,
    heightPx: Float,
    tSeconds: Float,
    palette: PunlaPalette,
    isDark: Boolean,
    petalCount: Int = 54,
) {
    canvas.drawColor((if (isDark) palette.darkBg else palette.paper).toArgb())
    if (widthPx <= 0f || heightPx <= 0f) return
    val rng = Random(20260805)
    val petalPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val colorA = (if (isDark) palette.maroonDark else palette.maroon).toArgb()
    val colorB = (if (isDark) palette.mangoDark else palette.mango).toArgb()
    val margin = heightPx * 0.08f

    repeat(petalCount) {
        val xSeed = rng.nextFloat()
        val phase = rng.nextFloat()
        val depth = 0.35f + rng.nextFloat() * 0.65f
        val speed = 0.045f + depth * 0.048f
        val progress = fractional(tSeconds * speed + phase)
        val y = progress * (heightPx + margin * 2f) - margin
        val swayPhase = rng.nextFloat() * PI.toFloat() * 2f
        val curveA = sin(tSeconds * (0.34f + depth * 0.25f) + swayPhase) * widthPx * (0.012f + depth * 0.014f)
        val curveB = sin(progress * PI.toFloat() * 2f + swayPhase * 0.63f) * widthPx * (0.010f + depth * 0.012f)
        val gentleDrift = (progress - 0.5f) * widthPx * 0.018f
        val x = wrap(widthPx * xSeed + curveA + curveB + gentleDrift, -margin, widthPx + margin)
        val size = (3.2f + depth * 5.5f) * max(widthPx, heightPx) / 900f
        val rotation = (tSeconds * (22f + depth * 34f) + phase * 360f + sin(tSeconds * 0.5f + swayPhase) * 18f) % 360f

        petalPaint.color = if (rng.nextBoolean()) colorA else colorB
        petalPaint.alpha = (72 + depth * 105f).toInt()
        val saved = canvas.save()
        canvas.rotate(rotation, x, y)
        canvas.drawOval(RectF(x - size * 0.48f, y - size, x + size * 0.48f, y + size), petalPaint)
        canvas.rotate(32f, x, y)
        petalPaint.alpha = (petalPaint.alpha * 0.7f).toInt()
        canvas.drawOval(RectF(x - size * 0.32f, y - size * 0.75f, x + size * 0.32f, y + size * 0.75f), petalPaint)
        canvas.restoreToCount(saved)
    }
}


/** Soft snow with layered depth and flutter rather than straight-line fall. */
fun paintSnow(
    canvas: Canvas,
    widthPx: Float,
    heightPx: Float,
    tSeconds: Float,
    palette: PunlaPalette,
    isDark: Boolean,
    flakeCount: Int = 82,
) {
    canvas.drawColor((if (isDark) palette.darkBg else palette.paper).toArgb())
    if (widthPx <= 0f || heightPx <= 0f) return
    val rng = Random(20260806)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val color = (if (isDark) palette.textDark else palette.ink).toArgb()
    val margin = heightPx * 0.04f

    repeat(flakeCount) {
        val xSeed = rng.nextFloat()
        val phase = rng.nextFloat()
        val depth = 0.22f + rng.nextFloat() * 0.78f
        val speed = 0.026f + depth * 0.042f
        val progress = fractional(tSeconds * speed + phase)
        val y = progress * (heightPx + margin * 2f) - margin
        val angle = phase * PI.toFloat() * 4f
        val flutterA = sin(tSeconds * (0.24f + depth * 0.20f) + angle) * widthPx * (0.006f + depth * 0.010f)
        val flutterB = sin(tSeconds * (0.57f + depth * 0.18f) + angle * 1.6f) * widthPx * 0.004f
        val x = wrap(widthPx * xSeed + flutterA + flutterB, -margin, widthPx + margin)
        val radius = (0.8f + depth * 2.8f) * max(widthPx, heightPx) / 900f
        paint.color = color
        paint.alpha = (30 + depth * 115f).toInt()
        canvas.drawCircle(x, y, radius, paint)
    }
}


/** Rising outlined bubbles with highlights, wobble, and slight shape breathing. */
fun paintBubbles(
    canvas: Canvas,
    widthPx: Float,
    heightPx: Float,
    tSeconds: Float,
    palette: PunlaPalette,
    isDark: Boolean,
    bubbleCount: Int = 32,
) {
    canvas.drawColor((if (isDark) palette.darkBg else palette.paper).toArgb())
    if (widthPx <= 0f || heightPx <= 0f) return
    val rng = Random(20260807)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val highlight = Paint(Paint.ANTI_ALIAS_FLAG)
    val color = (if (isDark) palette.leaf else palette.leafLight).toArgb()
    val margin = heightPx * 0.08f

    repeat(bubbleCount) {
        val xSeed = rng.nextFloat()
        val phase = rng.nextFloat()
        val depth = 0.25f + rng.nextFloat() * 0.75f
        val speed = 0.022f + depth * 0.036f
        val progress = fractional(tSeconds * speed + phase)
        val y = heightPx + margin - progress * (heightPx + margin * 2f)
        val angle = phase * PI.toFloat() * 4f
        val drift = sin(tSeconds * (0.18f + depth * 0.13f) + angle) * widthPx * 0.014f +
            sin(tSeconds * 0.43f + angle * 1.4f) * widthPx * 0.004f
        val x = wrap(widthPx * xSeed + drift, -margin, widthPx + margin)
        val radius = (5f + depth * 14f) * max(widthPx, heightPx) / 900f
        val breathe = sin(tSeconds * (0.42f + depth * 0.18f) + angle) * 0.035f
        val rx = radius * (1f + breathe)
        val ry = radius * (1f - breathe)

        stroke.color = color
        stroke.alpha = (35 + depth * 75f).toInt()
        stroke.strokeWidth = 0.8f + depth * 1.1f
        canvas.drawOval(RectF(x - rx, y - ry, x + rx, y + ry), stroke)
        highlight.color = color
        highlight.alpha = (55 + depth * 90f).toInt()
        canvas.drawCircle(x - rx * 0.33f, y - ry * 0.35f, max(0.8f, radius * 0.11f), highlight)
    }
}


private fun withAlpha(argb: Int, alpha: Int): Int =
    (argb and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

private fun fractional(value: Float): Float = value - floor(value)

private fun wrap(value: Float, min: Float, max: Float): Float {
    val range = max - min
    if (range <= 0f) return min
    var result = (value - min) % range
    if (result < 0f) result += range
    return result + min
}

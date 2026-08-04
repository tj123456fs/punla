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
 * Layered wind-blown rain. Each deterministic drop receives its own depth,
 * speed, length, width, and phase; near drops move faster and appear brighter.
 * A short bright head reads as motion blur, while occasional bottom-edge
 * ellipses provide inexpensive splash cues.
 */
fun paintRain(
    canvas: Canvas,
    widthPx: Float,
    heightPx: Float,
    tSeconds: Float,
    palette: PunlaPalette,
    isDark: Boolean,
    dropCount: Int = 96,
) {
    val base = (if (isDark) palette.darkBg else palette.paper).toArgb()
    canvas.drawColor(base)
    if (widthPx <= 0f || heightPx <= 0f) return

    // Inspired by the depth/speed separation used by skydoves' Apache-2.0
    // Compose rain sample, but kept deterministic so the exact same painter
    // can also produce a frozen widget frame.
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
        val depth = 0.28f + rng.nextFloat() * 0.72f
        val speed = (0.52f + depth * 0.82f) * (0.86f + rng.nextFloat() * 0.28f)
        val phase = rng.nextFloat()
        val progress = fractional(tSeconds * RAIN_FALL_HZ * speed + phase)
        val streakLength = heightPx * (0.018f + depth * 0.035f)
        val lean = streakLength * (0.20f + depth * 0.16f)
        val margin = streakLength * 1.4f
        val headY = progress * (heightPx + margin * 2f) - margin
        val windTravel = heightPx * 0.12f
        val headX = wrap(widthPx * xSeed - progress * windTravel, -margin, widthPx + margin)
        val alpha = (42 + depth * 92f).toInt().coerceIn(0, 255)

        dropPaint.color = if (depth > 0.72f) nearColor else farColor
        dropPaint.alpha = alpha
        dropPaint.strokeWidth = 0.7f + depth * 1.6f
        canvas.drawLine(
            headX - lean,
            headY - streakLength,
            headX,
            headY,
            dropPaint,
        )

        // A small bright head makes the streak read as motion blur without
        // allocating one LinearGradient shader per drop per frame.
        dropPaint.alpha = (alpha * 0.7f).toInt()
        dropPaint.strokeWidth *= 1.18f
        canvas.drawLine(
            headX - lean * 0.14f,
            headY - streakLength * 0.14f,
            headX,
            headY,
            dropPaint,
        )

        if (progress > 0.982f && headX in 0f..widthPx) {
            val splashProgress = ((progress - 0.982f) / 0.018f).coerceIn(0f, 1f)
            splashPaint.color = nearColor
            splashPaint.alpha = ((1f - splashProgress) * 92f * depth).toInt()
            splashPaint.strokeWidth = 0.8f + depth
            val radius = (2.5f + depth * 5f) * splashProgress
            canvas.drawOval(
                RectF(
                    headX - radius,
                    heightPx - radius * 0.28f,
                    headX + radius,
                    heightPx + radius * 0.28f,
                ),
                splashPaint,
            )
        }
    }
}

/** Flowing translucent ribbons over the active theme background. */
fun paintAurora(
    canvas: Canvas,
    widthPx: Float,
    heightPx: Float,
    tSeconds: Float,
    palette: PunlaPalette,
    isDark: Boolean,
) {
    val base = (if (isDark) palette.darkBg else palette.paper).toArgb()
    canvas.drawColor(base)
    if (widthPx <= 0f || heightPx <= 0f) return

    val colors = intArrayOf(
        (if (isDark) palette.leaf else palette.leafLight).copy(alpha = if (isDark) 0.28f else 0.18f).toArgb(),
        (if (isDark) palette.mangoDark else palette.mango).copy(alpha = if (isDark) 0.22f else 0.14f).toArgb(),
        (if (isDark) palette.maroonDark else palette.maroon).copy(alpha = if (isDark) 0.22f else 0.13f).toArgb(),
    )

    repeat(3) { index ->
        val phase = index * 1.9f
        val baseY = heightPx * (0.25f + index * 0.19f)
        val amplitude = heightPx * (0.07f + index * 0.018f)
        val path = Path()
        val samples = 36
        for (sample in 0..samples) {
            val x = widthPx * sample / samples
            val y = baseY +
                sin(sample / samples.toFloat() * PI.toFloat() * (1.6f + index * 0.32f) + tSeconds * (0.18f + index * 0.035f) + phase) * amplitude +
                sin(sample / samples.toFloat() * PI.toFloat() * 4.2f - tSeconds * 0.11f + phase) * amplitude * 0.28f
            if (sample == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = heightPx * (0.13f - index * 0.018f)
            color = colors[index]
        }
        canvas.drawPath(path, glow)

        glow.strokeWidth *= 0.35f
        glow.alpha = 128
        canvas.drawPath(path, glow)
    }
}

/** Layered sine-wave fills with independent phase speeds. */
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
        (if (isDark) palette.leafLight else palette.leaf).copy(alpha = 0.20f).toArgb(),
        (if (isDark) palette.mangoDark else palette.mango).copy(alpha = 0.16f).toArgb(),
        (if (isDark) palette.maroonDark else palette.maroon).copy(alpha = 0.14f).toArgb(),
        (if (isDark) palette.leaf else palette.leafLight).copy(alpha = 0.22f).toArgb(),
    )

    repeat(4) { layer ->
        val baseline = heightPx * (0.52f + layer * 0.10f)
        val amplitude = heightPx * (0.024f + layer * 0.009f)
        val phase = tSeconds * (0.16f + layer * 0.035f) * if (layer % 2 == 0) 1f else -1f
        val path = Path().apply { moveTo(0f, heightPx) }
        val samples = 48
        for (sample in 0..samples) {
            val x = widthPx * sample / samples
            val unit = sample / samples.toFloat()
            val y = baseline + sin(unit * PI.toFloat() * (2.2f + layer * 0.35f) + phase + layer) * amplitude
            path.lineTo(x, y)
        }
        path.lineTo(widthPx, heightPx)
        path.close()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = waveColors[layer] })
    }
}

/** Warm lights that wander in small loops and pulse independently. */
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
        val speed = 0.18f + rng.nextFloat() * 0.34f
        val orbit = (5f + rng.nextFloat() * 18f) * max(widthPx, heightPx) / 900f
        val x = baseX + cos(tSeconds * speed + phase) * orbit
        val y = baseY + sin(tSeconds * speed * 0.73f + phase * 1.31f) * orbit * 0.7f
        val pulse = (sin(tSeconds * (1.0f + rng.nextFloat()) + phase) * 0.5f + 0.5f)
        val radius = (1.4f + rng.nextFloat() * 1.8f) * max(widthPx, heightPx) / 900f

        glowPaint.color = color
        glowPaint.alpha = (24 + pulse * 52).toInt()
        canvas.drawCircle(x, y, radius * 4.2f, glowPaint)
        glowPaint.alpha = (55 + pulse * 70).toInt()
        canvas.drawCircle(x, y, radius * 2.1f, glowPaint)
        corePaint.color = color
        corePaint.alpha = (135 + pulse * 120).toInt()
        canvas.drawCircle(x, y, radius, corePaint)
    }
}

/** Falling petals with depth, breeze, and rotation. */
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
        val speed = 0.055f + depth * 0.055f
        val progress = fractional(tSeconds * speed + phase)
        val y = progress * (heightPx + margin * 2f) - margin
        val swayPhase = rng.nextFloat() * PI.toFloat() * 2f
        val sway = sin(tSeconds * (0.55f + depth * 0.4f) + swayPhase) * widthPx * (0.018f + depth * 0.02f)
        val x = wrap(widthPx * xSeed + sway + progress * widthPx * 0.06f, -margin, widthPx + margin)
        val size = (3.2f + depth * 5.5f) * max(widthPx, heightPx) / 900f
        val rotation = (tSeconds * (32f + depth * 48f) + phase * 360f) % 360f

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

/** Soft snow with three apparent depth layers. */
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
        val speed = 0.035f + depth * 0.055f
        val progress = fractional(tSeconds * speed + phase)
        val y = progress * (heightPx + margin * 2f) - margin
        val sway = sin(tSeconds * (0.28f + depth * 0.24f) + phase * PI.toFloat() * 4f) * widthPx * (0.008f + depth * 0.012f)
        val x = wrap(widthPx * xSeed + sway, -margin, widthPx + margin)
        val radius = (0.8f + depth * 2.8f) * max(widthPx, heightPx) / 900f
        paint.color = color
        paint.alpha = (30 + depth * 115f).toInt()
        canvas.drawCircle(x, y, radius, paint)
    }
}

/** Rising outlined bubbles with highlights and gentle sideways drift. */
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
        val speed = 0.025f + depth * 0.045f
        val progress = fractional(tSeconds * speed + phase)
        val y = heightPx + margin - progress * (heightPx + margin * 2f)
        val drift = sin(tSeconds * (0.25f + depth * 0.2f) + phase * PI.toFloat() * 4f) * widthPx * 0.025f
        val x = wrap(widthPx * xSeed + drift, -margin, widthPx + margin)
        val radius = (5f + depth * 14f) * max(widthPx, heightPx) / 900f

        stroke.color = color
        stroke.alpha = (35 + depth * 75f).toInt()
        stroke.strokeWidth = 0.8f + depth * 1.1f
        canvas.drawCircle(x, y, radius, stroke)
        highlight.color = color
        highlight.alpha = (55 + depth * 90f).toInt()
        canvas.drawCircle(x - radius * 0.33f, y - radius * 0.35f, max(0.8f, radius * 0.11f), highlight)
    }
}

private fun fractional(value: Float): Float = value - floor(value)

private fun wrap(value: Float, min: Float, max: Float): Float {
    val range = max - min
    if (range <= 0f) return min
    var result = (value - min) % range
    if (result < 0f) result += range
    return result + min
}

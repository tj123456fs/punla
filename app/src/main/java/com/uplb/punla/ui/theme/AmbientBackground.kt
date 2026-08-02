package com.uplb.punla.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.uplb.punla.data.BackgroundStyle

/**
 * A very slow, subtle ambient wash behind the whole app: three soft tinted
 * blobs drifting on independent Lissajous-style paths, drawn via the raw
 * [paintAmbientWash] so the exact same drawing code produces the live,
 * animated version here and the single frozen frame the home-screen
 * widgets render (see BackgroundRenderer.kt) — one source of truth, two
 * entry points.
 *
 * The driving angle [t] sweeps exactly one full turn (0 to 2*PI) per loop,
 * and every blob's x/y motion uses a *whole-number* multiple of that angle
 * ([xFreq]/[yFreq] inside [paintAmbientWash]). That's what makes the loop
 * seamless: since cos/sin are periodic every 2*PI, and n*2*PI is itself a
 * whole multiple of 2*PI for any integer n, every blob is mathematically
 * guaranteed to land back on its exact starting position the instant the
 * animation restarts — no snap or jump-cut, just a continuous drift that
 * quietly loops.
 */
fun Modifier.ambientGradientBackground(darkTheme: Boolean = false): Modifier = composed {
    val palette = LocalPunlaPalette.current
    val transition = rememberInfiniteTransition(label = "ambient_bg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 34_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambient_bg_angle"
    )

    this.then(
        Modifier.drawBehind {
            drawIntoCanvas { canvas ->
                paintAmbientWash(canvas.nativeCanvas, size.width, size.height, palette, darkTheme, t)
            }
        }
    )
}

/**
 * A twinkling star field, same idea as [ambientGradientBackground] but
 * drawn through [paintStarfield]. Unlike the ambient wash, the twinkle
 * phase per star doesn't need a mathematically seamless loop — a random
 * per-star `speed` multiplier means no single loop length keeps every star
 * in sync, so instead of chasing an exact loop this just uses a very long
 * one; any seam is imperceptible against 80 independently-twinkling stars.
 */
fun Modifier.starfieldBackground(darkTheme: Boolean = false): Modifier = composed {
    val palette = LocalPunlaPalette.current
    val transition = rememberInfiniteTransition(label = "starfield_bg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_000_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "starfield_bg_t"
    )

    this.then(
        Modifier.drawBehind {
            drawIntoCanvas { canvas ->
                paintStarfield(
                    canvas.nativeCanvas, size.width, size.height, t,
                    starColorArgb = (if (darkTheme) palette.textDark else palette.ink).toArgb(),
                    accentColorArgb = (if (darkTheme) palette.tertiaryDark else palette.mango).toArgb(),
                    baseColorArgb = (if (darkTheme) palette.darkBg else palette.paper).toArgb(),
                )
            }
        }
    )
}

/**
 * A static "field notebook" paper-grain texture — no `rememberInfiniteTransition`
 * at all, since [paintPaperGrain] has nothing to animate. Still redrawn on
 * recomposition of whatever Box it's attached to, same as a flat color fill
 * would be; "static" here means no per-frame animation loop, not "drawn once
 * ever."
 */
fun Modifier.paperGrainBackground(darkTheme: Boolean = false): Modifier = composed {
    val palette = LocalPunlaPalette.current
    this.then(
        Modifier.drawBehind {
            drawIntoCanvas { canvas ->
                paintPaperGrain(canvas.nativeCanvas, size.width, size.height, palette, darkTheme)
            }
        }
    )
}

/**
 * Thin diagonal rain streaks falling at a steady rate, via [paintRain].
 * Same "very long single loop" trick as [starfieldBackground] — an
 * `animateFloat` that counts up for 4,000,000ms before ever restarting, so
 * the one-time value-wrap at the end of that window is imperceptible rather
 * than a visible once-a-cycle reset. [paintRain] treats the value as real
 * elapsed seconds (1 unit per 1000ms here) and derives each drop's own fall
 * progress from it independently.
 */
fun Modifier.rainBackground(darkTheme: Boolean = false): Modifier = composed {
    val palette = LocalPunlaPalette.current
    val transition = rememberInfiniteTransition(label = "rain_bg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_000_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rain_bg_t"
    )

    this.then(
        Modifier.drawBehind {
            drawIntoCanvas { canvas ->
                paintRain(canvas.nativeCanvas, size.width, size.height, t, palette, darkTheme)
            }
        }
    )
}

/**
 * Dispatches to whichever [BackgroundStyle] is currently selected — the one
 * call site [com.uplb.punla.MainActivity] needs instead of hardcoding
 * [ambientGradientBackground]. MINIMAL is a flat fill of the resolved
 * theme's background color, i.e. exactly what every screen already sits on
 * top of; the other four layer their live drawing over the same base.
 */
@Composable
fun Modifier.appBackground(
    style: BackgroundStyle,
    darkTheme: Boolean = isSystemInDarkTheme(),
): Modifier = when (style) {
    BackgroundStyle.MINIMAL -> this.background(MaterialTheme.colorScheme.background)
    BackgroundStyle.AMBIENT -> this.ambientGradientBackground(darkTheme)
    BackgroundStyle.STARFIELD -> this.starfieldBackground(darkTheme)
    BackgroundStyle.PAPER_GRAIN -> this.paperGrainBackground(darkTheme)
    BackgroundStyle.RAIN -> this.rainBackground(darkTheme)
}

package com.uplb.punla.ui.theme

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.uplb.punla.data.BackgroundStyle
import com.uplb.punla.data.ThemePreset
import com.uplb.punla.data.resolveForTheme
import kotlinx.coroutines.isActive

/**
 * Applies one deterministic procedural background. The renderer itself lives
 * in [paintBackgroundFrame], so the app, previews, and widgets never maintain
 * separate versions of the same effect.
 */
private fun Modifier.animatedProceduralBackground(
    style: BackgroundStyle,
    themePreset: ThemePreset,
    darkTheme: Boolean,
    animationEnabled: Boolean = true,
): Modifier = composed {
    val palette = LocalPunlaPalette.current

    // Atmospheric backgrounds do not need display-refresh-rate animation. Driving
    // a full-screen procedural shader at 60/90/120 Hz is expensive and competes
    // directly with scrolling and touch feedback. Keep the motion alive at a
    // style-appropriate cadence while letting the UI render interactions freely.
    var tSeconds by remember(style) { mutableFloatStateOf(0f) }
    LaunchedEffect(style, animationEnabled) {
        if (!animationEnabled) return@LaunchedEffect
        // Continue from the currently displayed phase after a temporary pause
        // (e.g. while a list is being scrolled) instead of snapping to zero.
        val startedAt = SystemClock.uptimeMillis() - (tSeconds * 1000f).toLong()
        val frameIntervalNanos = when (style) {
            BackgroundStyle.AMBIENT, BackgroundStyle.STARFIELD -> 66_000_000L  // ~15 fps
            BackgroundStyle.AURORA, BackgroundStyle.FIREFLIES -> 50_000_000L  // ~20 fps
            BackgroundStyle.RAIN, BackgroundStyle.OCEAN_WAVES,
            BackgroundStyle.SAKURA, BackgroundStyle.SNOW, BackgroundStyle.BUBBLES -> 40_000_000L // ~25 fps
            else -> 66_000_000L
        }
        var lastPublishedFrame = Long.MIN_VALUE
        while (isActive) {
            // Publish animation state on a real display frame instead of waking at
            // arbitrary delay boundaries. That avoids invalidating the draw tree just
            // after a vsync and gives touch/scroll frames more predictable headroom.
            withFrameNanos { frameTimeNanos ->
                if (lastPublishedFrame == Long.MIN_VALUE ||
                    frameTimeNanos - lastPublishedFrame >= frameIntervalNanos
                ) {
                    tSeconds = (SystemClock.uptimeMillis() - startedAt) / 1000f
                    lastPublishedFrame = frameTimeNanos
                }
            }
        }
    }

    this.then(
        Modifier.drawBehind {
            drawIntoCanvas { canvas ->
                paintBackgroundFrame(
                    canvas = canvas.nativeCanvas,
                    style = style,
                    widthPx = size.width,
                    heightPx = size.height,
                    palette = palette,
                    isDark = darkTheme,
                    themePreset = themePreset,
                    tSeconds = tSeconds,
                )
            }
        }
    )
}

private fun Modifier.staticProceduralBackground(
    style: BackgroundStyle,
    themePreset: ThemePreset,
    darkTheme: Boolean,
): Modifier = composed {
    val palette = LocalPunlaPalette.current
    this.then(
        Modifier.drawBehind {
            drawIntoCanvas { canvas ->
                paintBackgroundFrame(
                    canvas = canvas.nativeCanvas,
                    style = style,
                    widthPx = size.width,
                    heightPx = size.height,
                    palette = palette,
                    isDark = darkTheme,
                    themePreset = themePreset,
                    tSeconds = 7.3f,
                )
            }
        }
    )
}

/** Backward-compatible convenience wrappers used by older call sites. */
fun Modifier.ambientGradientBackground(darkTheme: Boolean = false): Modifier =
    animatedProceduralBackground(BackgroundStyle.AMBIENT, ThemePreset.FIELD_NOTEBOOK, darkTheme)

fun Modifier.starfieldBackground(darkTheme: Boolean = false): Modifier =
    animatedProceduralBackground(BackgroundStyle.STARFIELD, ThemePreset.FIELD_NOTEBOOK, darkTheme)

fun Modifier.paperGrainBackground(darkTheme: Boolean = false): Modifier =
    staticProceduralBackground(BackgroundStyle.PAPER_GRAIN, ThemePreset.FIELD_NOTEBOOK, darkTheme)

fun Modifier.rainBackground(darkTheme: Boolean = false): Modifier =
    animatedProceduralBackground(BackgroundStyle.RAIN, ThemePreset.FIELD_NOTEBOOK, darkTheme)

/** Main app entry point. */
@Composable
fun Modifier.appBackground(
    style: BackgroundStyle,
    themePreset: ThemePreset = ThemePreset.FIELD_NOTEBOOK,
    darkTheme: Boolean = isSystemInDarkTheme(),
    animationEnabled: Boolean = true,
): Modifier {
    val resolved = style.resolveForTheme(themePreset)
    return when (resolved) {
        BackgroundStyle.MINIMAL -> this.background(MaterialTheme.colorScheme.background)
        BackgroundStyle.PAPER_GRAIN -> this.staticProceduralBackground(resolved, themePreset, darkTheme)
        else -> this.animatedProceduralBackground(resolved, themePreset, darkTheme, animationEnabled)
    }
}

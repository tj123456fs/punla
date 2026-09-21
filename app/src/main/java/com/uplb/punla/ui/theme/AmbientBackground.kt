package com.uplb.punla.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import kotlin.math.exp

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
    interactionActive: Boolean = false,
): Modifier = composed {
    val palette = LocalPunlaPalette.current

    // Keep the decorative motion on a virtual clock instead of wall-clock time.
    // During active scrolling we *ease* the clock toward a slower rate and publish
    // fewer background frames rather than freezing it. This preserves frame budget
    // for lists/touch input without the visually awkward stop -> jump -> resume
    // behavior of the previous hard pause.
    var tSeconds by remember(style) { mutableFloatStateOf(0f) }
    val interactionActiveState = rememberUpdatedState(interactionActive)

    LaunchedEffect(style, animationEnabled) {
        if (!animationEnabled) return@LaunchedEffect

        val idleFrameIntervalNanos = when (style) {
            BackgroundStyle.AMBIENT, BackgroundStyle.STARFIELD -> 66_000_000L  // ~15 fps
            BackgroundStyle.AURORA, BackgroundStyle.FIREFLIES -> 50_000_000L  // ~20 fps
            BackgroundStyle.RAIN, BackgroundStyle.OCEAN_WAVES,
            BackgroundStyle.SAKURA, BackgroundStyle.SNOW, BackgroundStyle.BUBBLES -> 40_000_000L // ~25 fps
            else -> 66_000_000L
        }
        val interactionFrameIntervalNanos = maxOf(idleFrameIntervalNanos, 83_000_000L) // <= ~12 fps while scrolling

        var virtualTimeSeconds = tSeconds
        var playbackSpeed = 1f
        var previousFrameNanos = Long.MIN_VALUE
        var lastPublishedFrameNanos = Long.MIN_VALUE

        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (previousFrameNanos == Long.MIN_VALUE) {
                    previousFrameNanos = frameTimeNanos
                    lastPublishedFrameNanos = frameTimeNanos
                    return@withFrameNanos
                }

                // Clamp huge deltas after sleep/backgrounding so the atmosphere never
                // leaps forward when Android resumes the activity.
                val deltaSeconds = ((frameTimeNanos - previousFrameNanos) / 1_000_000_000f)
                    .coerceIn(0f, 0.050f)
                previousFrameNanos = frameTimeNanos

                val targetSpeed = if (interactionActiveState.value) 0.32f else 1f
                // Exponential easing (~180 ms time constant) makes both entering and
                // leaving scroll mode visually continuous.
                val blend = (1f - exp((-deltaSeconds / 0.18f).toDouble()).toFloat())
                    .coerceIn(0f, 1f)
                playbackSpeed += (targetSpeed - playbackSpeed) * blend
                virtualTimeSeconds += deltaSeconds * playbackSpeed

                val publishInterval = if (interactionActiveState.value) {
                    interactionFrameIntervalNanos
                } else {
                    idleFrameIntervalNanos
                }

                if (frameTimeNanos - lastPublishedFrameNanos >= publishInterval) {
                    tSeconds = virtualTimeSeconds
                    lastPublishedFrameNanos = frameTimeNanos
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
    interactionActive: Boolean = false,
): Modifier {
    val resolved = style.resolveForTheme(themePreset)
    return when (resolved) {
        BackgroundStyle.MINIMAL -> this.background(MaterialTheme.colorScheme.background)
        BackgroundStyle.PAPER_GRAIN -> this.staticProceduralBackground(resolved, themePreset, darkTheme)
        else -> this.animatedProceduralBackground(
            resolved,
            themePreset,
            darkTheme,
            animationEnabled = animationEnabled,
            interactionActive = interactionActive,
        )
    }
}

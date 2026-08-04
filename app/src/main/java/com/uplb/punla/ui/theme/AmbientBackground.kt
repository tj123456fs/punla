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
import com.uplb.punla.data.BackgroundStyle
import com.uplb.punla.data.ThemePreset
import com.uplb.punla.data.resolveForTheme

/**
 * Applies one deterministic procedural background. The renderer itself lives
 * in [paintBackgroundFrame], so the app, previews, and widgets never maintain
 * separate versions of the same effect.
 */
private fun Modifier.animatedProceduralBackground(
    style: BackgroundStyle,
    themePreset: ThemePreset,
    darkTheme: Boolean,
): Modifier = composed {
    val palette = LocalPunlaPalette.current
    val transition = rememberInfiniteTransition(label = "background_${style.storageKey}")
    val tSeconds by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4_000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_000_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "background_time_${style.storageKey}",
    )

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
): Modifier {
    val resolved = style.resolveForTheme(themePreset)
    return when (resolved) {
        BackgroundStyle.MINIMAL -> this.background(MaterialTheme.colorScheme.background)
        BackgroundStyle.PAPER_GRAIN -> this.staticProceduralBackground(resolved, themePreset, darkTheme)
        else -> this.animatedProceduralBackground(resolved, themePreset, darkTheme)
    }
}

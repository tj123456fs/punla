package com.uplb.punla.ui.theme

import com.uplb.punla.data.BackgroundStyle

/** User-facing metadata for the procedural background picker. */
data class BackgroundDescriptor(
    val style: BackgroundStyle,
    val label: String,
    val description: String,
    val animated: Boolean,
)

val PunlaBackgroundCatalog: List<BackgroundDescriptor> = listOf(
    BackgroundDescriptor(
        style = BackgroundStyle.THEME_MATCHED,
        label = "Theme Match",
        description = "Automatically picks a signature effect for the selected theme.",
        animated = true,
    ),
    BackgroundDescriptor(
        style = BackgroundStyle.MINIMAL,
        label = "Minimal",
        description = "A flat, distraction-free fill.",
        animated = false,
    ),
    BackgroundDescriptor(
        style = BackgroundStyle.AMBIENT,
        label = "Ambient",
        description = "Soft tinted light slowly drifting behind the app.",
        animated = true,
    ),
    BackgroundDescriptor(
        style = BackgroundStyle.RAIN,
        label = "Rain",
        description = "Layered wind-blown rain with depth and tiny splashes.",
        animated = true,
    ),
    BackgroundDescriptor(
        style = BackgroundStyle.AURORA,
        label = "Aurora",
        description = "Glowing ribbons that flow across a dark sky.",
        animated = true,
    ),
    BackgroundDescriptor(
        style = BackgroundStyle.OCEAN_WAVES,
        label = "Ocean Waves",
        description = "Calm layered waves with slow parallax motion.",
        animated = true,
    ),
    BackgroundDescriptor(
        style = BackgroundStyle.FIREFLIES,
        label = "Fireflies",
        description = "Warm wandering lights with soft pulsing glows.",
        animated = true,
    ),
    BackgroundDescriptor(
        style = BackgroundStyle.SAKURA,
        label = "Sakura",
        description = "Petals drift and rotate through a gentle breeze.",
        animated = true,
    ),
    BackgroundDescriptor(
        style = BackgroundStyle.SNOW,
        label = "Snow",
        description = "Layered flakes fall softly with a subtle sideways sway.",
        animated = true,
    ),
    BackgroundDescriptor(
        style = BackgroundStyle.BUBBLES,
        label = "Bubbles",
        description = "Translucent bubbles rise with tiny reflective highlights.",
        animated = true,
    ),
    BackgroundDescriptor(
        style = BackgroundStyle.STARFIELD,
        label = "Starfield",
        description = "A quiet field of twinkling, drifting stars.",
        animated = true,
    ),
    BackgroundDescriptor(
        style = BackgroundStyle.PAPER_GRAIN,
        label = "Paper Grain",
        description = "A static field-notebook texture with no animation.",
        animated = false,
    ),
)

fun BackgroundStyle.isAnimatedBackground(): Boolean = when (this) {
    BackgroundStyle.MINIMAL,
    BackgroundStyle.PAPER_GRAIN -> false

    else -> true
}

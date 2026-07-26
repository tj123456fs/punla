package com.uplb.punla.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.uplb.punla.R
import com.uplb.punla.data.FontChoice

// The web app loads these exact families via Google Fonts:
//   Fraunces:opsz,wght@9..144,400;9..144,500;9..144,600   (display serif)
//   Inter:wght@400;500;600;700                            (body sans)
//   IBM Plex Mono:wght@500;600                             (numerals / mono)
//
// Fraunces and Inter ship as variable fonts, so we bind the exact weights the
// web app actually uses via FontVariation.Settings on the single variable
// .ttf (API 26+, matching this module's minSdk). Fraunces also carries the
// web app's default opsz/SOFT/WONK axis values so glyph shapes match the
// display headings 1:1. IBM Plex Mono ships static weight files upstream, so
// those are declared as separate Font entries per weight (500/600), matching
// the exact two weights the web app requests — plus 400 for any regular body
// use of the numeral font.

@OptIn(ExperimentalTextApi::class)
val PunlaDisplay = FontFamily(
    Font(
        R.font.fraunces_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.opticalSizing(28.sp),
        )
    ),
    Font(
        R.font.fraunces_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.opticalSizing(28.sp),
        )
    ),
    Font(
        R.font.fraunces_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.opticalSizing(28.sp),
        )
    ),
)

@OptIn(ExperimentalTextApi::class)
val PunlaBody = FontFamily(
    Font(
        R.font.inter_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.inter_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
)

val PunlaMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, weight = FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, weight = FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, weight = FontWeight.SemiBold),
)

/** Display-slot family for each [FontChoice] — used for headlines/titles. */
private fun displayFamilyFor(choice: FontChoice): FontFamily = when (choice) {
    FontChoice.DEFAULT -> PunlaDisplay
    FontChoice.SANS -> PunlaBody
    FontChoice.SERIF -> PunlaDisplay
    FontChoice.MONO -> PunlaMono
    FontChoice.SYSTEM -> FontFamily.Default
}

/** Body-slot family for each [FontChoice] — used for body/label text. */
private fun bodyFamilyFor(choice: FontChoice): FontFamily = when (choice) {
    FontChoice.DEFAULT -> PunlaBody
    FontChoice.SANS -> PunlaBody
    FontChoice.SERIF -> PunlaDisplay
    FontChoice.MONO -> PunlaMono
    FontChoice.SYSTEM -> FontFamily.Default
}

/**
 * Builds the app's [Typography] for a given [FontChoice]. DEFAULT reproduces
 * the original web app's Fraunces-headings / Inter-body pairing 1:1; the
 * other choices substitute a single bundled (or system) family across both
 * slots so switching feels like a deliberate whole-app restyle rather than
 * a partial swap.
 *
 * Sizes/weights below are taken directly from index.html's <style> block —
 * see recreate-punla-ui.md's changelog for the web selector each maps to.
 */
fun punlaTypography(fontChoice: FontChoice = FontChoice.DEFAULT): Typography {
    val Display = displayFamilyFor(fontChoice)
    val Body = bodyFamilyFor(fontChoice)
    return Typography(
    // .splash-name { font-family: Fraunces; font-size: 30px; font-weight: 600; line-height: 1.18 }
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 35.sp),
    // .brand-name (topbar / drawer header title) { Fraunces 22px/600, letter-spacing 0.3px, line-height 1 }
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
    // h2.section-title { Fraunces 19px/600 }
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
    // .class-title / .map-modal-title { Fraunces 16px/600 }
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    // No exact CSS counterpart; kept as the general Inter semibold "value" style
    // used for course grades / GWA figures that aren't in mono.
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    // .expense-cat / gradeCourseRow code line { Inter 12.5px/500 }
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 17.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    // .empty { font-size: 13px }
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    // .class-time (font-mono 12px/600) / .muted (12.5px) cluster around 12px
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    // .week-day-label { 11.5px / 700 / uppercase / letter-spacing 0.4px }
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, letterSpacing = 0.4.sp),
    // .seed-label { 11.5px } (Android also uses this for the stat-box "l" style)
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, letterSpacing = 0.3.sp),
    // .badge { 10px / 600 / letter-spacing 0.3px }
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.3.sp),
    )
}

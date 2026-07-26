package com.uplb.punla.ui.theme

import androidx.compose.ui.graphics.Color

// Field-notebook palette — shared with the Punla web app.

// Light
val Paper = Color(0xFFFAF7ED)
val Ink = Color(0xFF1B2E23)
val InkSoft = Color(0xFF24392C)
val CardLight = Color(0xFFFFFFFF)
val LineLight = Color(0xFFE6E0CE)

val Leaf = Color(0xFF4F7A2A)
val LeafLight = Color(0xFF8FB35C)
val LeafBgLight = Color(0xFFEAF1DE)

val Maroon = Color(0xFF7A2942)
val MaroonBgLight = Color(0xFFF6E7EC)

val Mango = Color(0xFFC97F1E)
val MangoBgLight = Color(0xFFFBEEDA)

val Bark = Color(0xFF7A6A54)
val Danger = Color(0xFFA33131)

// Dark
val DarkBg = Color(0xFF141910)
val CardDark = Color(0xFF1E2618)
val TextDark = Color(0xFFEDE9DA)
val LineDark = Color(0xFF33402A)
val BarkDark = Color(0xFFA6AE96)

val LeafBgDark = Color(0xFF233420)
val MaroonBgDark = Color(0xFF3A1F27)
val MangoBgDark = Color(0xFF3A2A14)

// Topbar / tab bar — the web app keeps these a fixed dark "notebook cover" ink
// tone in BOTH light and dark theme (--ink / --ink-2 are not redefined under
// html[data-theme="dark"]), so these are theme-independent constants, not
// swapped between Light/DarkColors.
val TopbarSubtle = Color(0xFFB9C9BB) // .brand-sub / .date-chip text on the ink topbar
val TabInactive = Color(0xFF8FA093)  // .tab-btn (inactive) color on the ink tab bar

// Expense category accent colors (CATEGORIES array in the web app's <script>,
// not exposed as CSS custom properties). food/transpo/load/misc reuse the
// core palette; supplies and org/activities are category-only accents.
val CatFood = Leaf
val CatTranspo = Mango
val CatLoad = Maroon
val CatSupplies = Color(0xFF3D6B8A)
val CatOrg = Color(0xFF9C6B30)
val CatMisc = Bark

// Shadow tint used across cards/FABs/dialogs — the web app's box-shadow rgba
// values (e.g. rgba(27,46,35,0.05)) are literal RGB, equal to --ink, and are
// NOT swapped for dark mode, so this is also a fixed constant.
val ShadowInk = Ink

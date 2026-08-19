package com.uplb.punla.ui.screens

import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uplb.punla.ui.theme.LocalPunlaPalette
import com.uplb.punla.ui.theme.PunlaMono

/**
 * App-wide responsive gutter. On phones this stays at 16dp; on tablets and
 * landscape windows it grows until the actual content column tops out around
 * [maxContentWidth]. This keeps cards/forms readable instead of stretching
 * edge-to-edge once Punla switches to the navigation rail.
 */
@Composable
fun punlaScreenHorizontalPadding(
    maxContentWidth: Dp = 760.dp,
    compactPadding: Dp = 16.dp
): Dp {
    val config = LocalConfiguration.current
    val windowWidth = config.screenWidthDp.dp
    val railWidth = if (config.screenWidthDp >= 600) 80.dp else 0.dp
    val availableWidth = if (windowWidth > railWidth) windowWidth - railWidth else windowWidth
    val centeredPadding = (availableWidth - maxContentWidth) / 2
    return if (centeredPadding > compactPadding) centeredPadding else compactPadding
}

/**
 * Small rounded pill used for course type/lecture-lab tags (web: .badge —
 * Inter, 10px/600) and, when [mono] is set, the deadline countdown chip
 * (web: .dl-count — IBM Plex Mono, 11px/600). Both share the same fully
 * rounded pill shape and 10dp/4dp padding as .badge/.dl-count in the CSS.
 */
@Composable
fun Tag(text: String, container: Color, onContainer: Color, modifier: Modifier = Modifier, mono: Boolean = false) {
    Box(
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            if (mono) text else text.uppercase(),
            style = if (mono) {
                MaterialTheme.typography.labelSmall.copy(fontFamily = PunlaMono, fontSize = 11.sp)
            } else {
                MaterialTheme.typography.labelSmall
            },
            color = onContainer
        )
    }
}

/**
 * A thin colored bar used on the leading edge of list cards to signal category
 * at a glance (web: .class-card / .deadline-card's 4px solid border-left).
 * The web app's border-left has no corner radius of its own — the card's
 * overall 14px radius is what rounds the outer edges — so this stays a flat
 * rectangle rather than rounding its own corners.
 */
@Composable
fun AccentBar(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(color)
    )
}

/**
 * App-wide section header for grouped lists and dashboard modules. The 2.1
 * refresh deliberately moves away from the tiny all-caps web-port treatment:
 * sentence-case headings are easier to scan on a phone, icons receive a small
 * themed container, and an optional trailing action provides a clear path to
 * the full destination without making the entire section look tappable.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 10.dp)
    ) {
        if (icon != null) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
        } else {
            Box(
                Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Dashboard Redesign #1 - quick-glance stat row. Three equal-weight tiles
 * summarizing today/this-week at a skimmable glance, each tinted with a
 * distinct container color so the row introduces palette variety right under
 * the greeting/quote card. [nearLimitClasses] is grayed toward the surface
 * tone when zero so an empty "near limit" count doesn't read as alarming.
 */
@Composable
fun DashboardStatsRow(
    classesToday: Int,
    deadlinesThisWeek: Int,
    nearLimitClasses: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatTile(
            value = classesToday,
            label = "Classes today",
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            value = deadlinesThisWeek,
            label = "Due this week",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f)
        )
        val nearLimitActive = nearLimitClasses > 0
        StatTile(
            value = nearLimitClasses,
            label = "Near absence limit",
            container = if (nearLimitActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            onContainer = if (nearLimitActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(
    value: Int,
    label: String,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .heightIn(min = 78.dp)
            .background(container, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = PunlaMono, fontWeight = FontWeight.SemiBold),
                color = onContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = onContainer
            )
        }
    }
}

/**
 * Friendly empty state with a themed icon and an optional next-step action.
 * Giving dead ends a concrete action (Add class, Add expense, etc.) keeps the
 * user in flow instead of forcing them to hunt for the floating action button.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * Matches web: `.seg` — a pill-shaped tab switcher (e.g. List / Weekly grid).
 * Card-colored track, 10dp radius, 3dp inner padding; the active segment gets
 * a solid `--ink`/`--paper` fill exactly like `.seg button.active`, which is
 * NOT swapped between light/dark theme (see Color.kt's Ink/Paper notes).
 */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            val segmentColor by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(180),
                label = "segmentBackground"
            )
            val contentColor by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(180),
                label = "segmentContent"
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(segmentColor)
                    .clickable { onSelect(i) }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                    color = contentColor
                )
            }
        }
    }
}

/**
 * Matches web: `.day-pill` — a small rounded chip in a horizontally scrolling
 * row, used for day-of-week / semester pickers. Active state scales up 1.04x
 * with a solid leaf fill and literal white text (the CSS uses `color:#fff`,
 * not `--paper`, so this stays a hardcoded white rather than a theme token).
 * [hasDot] shows the small mango dot the web app uses to flag "has entries".
 */
@Composable
fun DayPill(label: String, active: Boolean, hasDot: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pillScale by animateFloatAsState(
        targetValue = if (active) 1.04f else 1f,
        animationSpec = tween(180),
        label = "dayPillScale"
    )
    val pillColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(180),
        label = "dayPillColor"
    )
    val pillTextColor by animateColorAsState(
        targetValue = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(180),
        label = "dayPillText"
    )

    Box(modifier, contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier
                .scale(pillScale)
                .clip(RoundedCornerShape(20.dp))
                .background(pillColor)
                .border(
                    1.dp,
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(20.dp)
                )
                .clickable { onClick() }
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                color = pillTextColor
            )
        }
        if (hasDot) {
            Box(
                Modifier
                    .padding(top = 4.dp, end = 6.dp)
                    .size(5.dp)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape)
            )
        }
    }
}

/**
 * Matches web: `.fab-row .primary` — an inline (not floating) pill-shaped
 * "+ Add" action that sits at the top of a list rather than a Material FAB.
 * When [open] the web app swaps the button to `--maroon` and rotates the
 * plus icon 45° into an "x", which this reproduces via Modifier.rotate.
 */
@Composable
fun InlineAddButton(label: String, open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (open) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            contentColor = if (open) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .rotate(if (open) 45f else 0f)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}

/**
 * Matches web: `label.field` + `input,select` — a caption-style label
 * (11.5px/500, `--bark`) stacked above a boxed input (8px radius, 1px
 * `--line` border, `--bg` fill), instead of Material's floating-label
 * OutlinedTextField. Used across all of the app's inline forms so every
 * field looks like the web app's plain boxed inputs.
 */
@Composable
fun PunlaField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    supportingText: String? = null
) {
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            keyboardOptions = keyboardOptions,
            cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    inner()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isError && supportingText != null) Modifier.semantics { error(supportingText) } else Modifier)
        )
        if (supportingText != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Matches web: `label.field` + `<select>` — same boxed look as [PunlaField]
 * but opens a dropdown menu of [options] on tap (a lightweight stand-in for
 * the browser's native `<select>` popover).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PunlaDropdownField(
    label: String,
    selectedLabel: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    optionLeadingColor: ((Int) -> Color)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedLabel, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { i, opt ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                optionLeadingColor?.let { colorFor ->
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .background(colorFor(i), RoundedCornerShape(2.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(opt)
                            }
                        },
                        onClick = { onSelect(i); expanded = false }
                    )
                }
            }
        }
    }
}

/**
 * Shared confirmation dialog for irreversible actions. Keeping the wording
 * and button placement consistent prevents destructive actions from feeling
 * unpredictable across Schedule, Budget, Deadlines, Grades and Checklist.
 */
@Composable
fun DestructiveActionDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * UX polish plan (glass section) — opaque "glass" treatment: zero new
 * dependencies, just a tinted background, a gradient edge highlight, and
 * the same soft shadow pattern `BudgetScreen.kt`'s `SpendingInsightsCard`
 * already uses. Deliberately skips real backdrop blur — on a text-dense
 * screen (schedules, deadline lists, budget figures) a high-alpha tint does
 * the "glass" work without the legibility cost of translucency, and blur is
 * the most expensive, least necessary property for that look. If this ever
 * feels too flat once seen on-device, `UX_POLISH_NAV_GLASS_MOTION.md`
 * documents which real blur libraries to reach for instead (see
 * `BRIEFING_AI_AGENTS_NEW_LIBRARIES.md` before wiring one in — none of
 * those are in this app's dependencies yet).
 *
 * [tintAlpha] defaults to 0.78 — inside the plan's recommended 0.6–0.85
 * opaque range, high enough that a busy animated background behind it
 * (falling leaves, fireflies, etc.) doesn't compromise text contrast.
 *
 * [elevation] defaults to the original flat-card 1.dp. Raised for
 * surfaces that need to visibly separate from the content behind them —
 * e.g. the floating bottom nav bar, which reuses this same tint/border
 * stack but wants a real "lifted off the page" shadow, not the subtle
 * one that suits an in-flow card.
 */
fun Modifier.glassCard(
    shape: Shape = RoundedCornerShape(14.dp),
    tint: Color? = null,
    tintAlpha: Float = 0.78f,
    elevation: Dp = 1.dp
): Modifier = this.composed {
    val resolvedTint = tint ?: MaterialTheme.colorScheme.surfaceVariant
    val shadowInk = LocalPunlaPalette.current.shadowInk
    this
        .shadow(elevation, shape, ambientColor = shadowInk.copy(alpha = 0.05f), spotColor = shadowInk.copy(alpha = 0.05f))
        .clip(shape)
        .background(resolvedTint.copy(alpha = tintAlpha))
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                // Skinny top/edge highlight — a light-colored line simulating
                // light catching the edge, per the plan's #2 priority
                // property. Fades to the ordinary outline color by the
                // bottom edge rather than a uniform bright border all round.
                colors = listOf(
                    Color.White.copy(alpha = 0.45f),
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            ),
            shape = shape
        )
}

/**
 * Drop-in Card replacement using [glassCard] — same role as a Material
 * `Card`, but with the opaque glass tint/edge-highlight/shadow stack
 * instead of a flat `containerColor` + `BorderStroke`. Use for cards that
 * want the plan's "glass" treatment specifically; ordinary `Card` is still
 * fine for list rows and other high-repetition, low-emphasis content.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    tint: Color? = null,
    tintAlpha: Float = 0.78f,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .glassCard(shape = shape, tint = tint, tintAlpha = tintAlpha)
            .padding(contentPadding),
        content = content
    )
}

/** Peso amount rendered in monospace, mirroring the web app's IBM Plex Mono numerals. */
@Composable
fun PesoText(
    amount: Double,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium
) {
    Text(
        "\u20b1${"%,.2f".format(amount)}",
        modifier = modifier,
        style = style.copy(fontFamily = PunlaMono, fontWeight = FontWeight.SemiBold),
        color = color
    )
}

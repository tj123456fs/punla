package com.uplb.punla.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.ImageProvider
import com.uplb.punla.MainActivity
import com.uplb.punla.R
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.ui.theme.resolvePalette
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private fun formatClassTime(raw: String): String = runCatching {
    LocalTime.parse(raw, DateTimeFormatter.ofPattern("HH:mm"))
        .format(DateTimeFormatter.ofPattern("h:mm a"))
}.getOrDefault(raw)

class NextClassWidget : GlanceAppWidget() {
    // Default 3x2 home-screen card, plus a small square that OEM lock-screen
    // "clock widget" slots (e.g. Funtouch OS 15's dual bubble clock style)
    // request instead of the full card.
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(130.dp, 130.dp),
            DpSize(180.dp, 110.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = PunlaRepository(context)
        val now = LocalDateTime.now()
        val allClasses = repo.allClasses()
        val todaysClasses = repo.todaysRemainingClassesFromList(allClasses, now)
        val next = repo.nextClassFromList(allClasses, now)
        val nextOngoing = next?.let { repo.isOngoing(it, now) } ?: false
        val minutesUntilNext = next?.takeIf { !nextOngoing }?.let { c ->
            runCatching {
                Duration.between(now.toLocalTime(), LocalTime.parse(c.start, DateTimeFormatter.ofPattern("HH:mm"))).toMinutes()
            }.getOrNull()
        }

        val openScheduleIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_START_ROUTE, "schedule")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openScheduleAction = actionStartActivity(openScheduleIntent)

        provideContent {
            val size = LocalSize.current
            val palette = resolvePalette(repo.themePreset, repo.customSeedColor)
            val wc = resolveWidgetColors(palette)
            if (size.width <= 140.dp) {
                CompactBubble(
                    next = next,
                    ongoing = nextOngoing,
                    minutesUntil = minutesUntilNext,
                    onClick = openScheduleAction,
                    wc = wc
                )
            } else {
                val density = context.resources.displayMetrics.density
                val widthPx = (size.width.value * density).toInt()
                val heightPx = (size.height.value * density).toInt()
                val bgImage = widgetBackgroundImageProvider(context, repo, palette, widthPx, heightPx)

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .let { m ->
                            if (bgImage != null) m.background(bgImage)
                            else m.background(ColorProvider(day = wc.backgroundFallback, night = wc.backgroundFallback))
                        }
                        .clickable(openScheduleAction)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "TODAY'S CLASSES",
                        style = TextStyle(
                            color = ColorProvider(day = wc.accent, night = wc.accent),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    when {
                        allClasses.isEmpty() -> {
                            Text(
                                text = "No classes scheduled",
                                style = TextStyle(color = ColorProvider(day = wc.textPrimary, night = wc.textPrimary))
                            )
                        }
                        todaysClasses.isEmpty() -> {
                            Text(
                                text = "No more classes today \uD83C\uDF89",
                                style = TextStyle(color = ColorProvider(day = wc.textPrimary, night = wc.textPrimary))
                            )
                        }
                        else -> {
                            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                items(todaysClasses, itemId = { it.id.hashCode().toLong() }) { c ->
                                    ClassRow(c, repo.isOngoing(c, now), openScheduleAction, wc)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact square variant for OEM lock-screen "clock widget" slots (the
 * small bubbles that sit next to the weather/step-count widgets on some
 * lock screens). Mirrors that visual language: icon-ish label up top, one
 * big stat in the middle, a short caption underneath.
 */
@Composable
private fun CompactBubble(
    next: ClassSession?,
    ongoing: Boolean,
    minutesUntil: Long?,
    onClick: Action,
    wc: WidgetColors
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.bg_widget_bubble))
            .clickable(onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Text(
                text = "NEXT CLASS",
                style = TextStyle(
                    color = ColorProvider(day = wc.accent, night = wc.accent),
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = when {
                    next == null -> "\uD83C\uDF89"
                    ongoing -> "NOW"
                    minutesUntil != null && minutesUntil < 60 -> "${minutesUntil}m"
                    minutesUntil != null -> "${minutesUntil / 60}h${minutesUntil % 60}m"
                    else -> "\u2013"
                },
                style = TextStyle(
                    color = ColorProvider(day = wc.textPrimary, night = wc.textPrimary),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = next?.code ?: "No classes",
                style = TextStyle(
                    color = ColorProvider(day = wc.textSecondary, night = wc.textSecondary),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ClassRow(
    c: ClassSession,
    ongoing: Boolean,
    onClick: Action,
    wc: WidgetColors
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        val indicatorColor = if (ongoing) wc.accent else wc.indicatorInactive
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(28.dp)
                .background(ColorProvider(day = indicatorColor, night = indicatorColor))
        ) {}
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(
                    text = c.code,
                    style = TextStyle(
                        color = ColorProvider(day = wc.textPrimary, night = wc.textPrimary),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
                if (ongoing) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = "NOW",
                        style = TextStyle(
                            color = ColorProvider(day = wc.accent, night = wc.accent),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }
            Text(
                text = "${formatClassTime(c.start)} \u2013 ${formatClassTime(c.end)} \u00B7 ${c.room ?: "Room TBA"}",
                style = TextStyle(color = ColorProvider(day = wc.textSecondary, night = wc.textSecondary), fontSize = 11.sp)
            )
        }
    }
}

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()
}

package com.uplb.punla.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.uplb.punla.MainActivity
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.ui.theme.resolvePalette

class NextDeadlineWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = PunlaRepository(context)
        val next = repo.nextDeadline()
        val days = next?.let { repo.daysUntil(it.due) }

        val openDeadlinesIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_START_ROUTE, "deadlines")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        provideContent {
            val size = LocalSize.current
            val density = context.resources.displayMetrics.density
            val widthPx = (size.width.value * density).toInt()
            val heightPx = (size.height.value * density).toInt()
            val palette = resolvePalette(repo.themePreset, repo.customSeedColor)
            val wc = resolveWidgetColors(palette)
            val bgImage = widgetBackgroundImageProvider(context, repo, palette, widthPx, heightPx)

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .let { m ->
                        if (bgImage != null) m.background(bgImage) // same call NextClassWidget already makes for bg_widget_bubble
                        else m.background(ColorProvider(day = wc.backgroundFallback, night = wc.backgroundFallback)) // MINIMAL fallback, now palette-aware
                    }
                    .clickable(actionStartActivity(openDeadlinesIntent))
                    .padding(12.dp)
            ) {
                Text(
                    text = "NEXT DEADLINE",
                    style = TextStyle(
                        color = ColorProvider(day = wc.accent, night = wc.accent),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
                if (next == null) {
                    Text(
                        text = "Nothing due — you're clear",
                        style = TextStyle(color = ColorProvider(day = wc.textPrimary, night = wc.textPrimary))
                    )
                } else {
                    val urgent = days != null && days <= 3
                    Text(
                        text = next.title,
                        style = TextStyle(
                            color = ColorProvider(day = wc.textPrimary, night = wc.textPrimary),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                    val dayLabel = when {
                        days == null -> next.due
                        days < 0 -> "${-days}d overdue"
                        days == 0L -> "due today"
                        days == 1L -> "due tomorrow"
                        else -> "due in $days days"
                    }
                    val subColor = if (urgent) wc.urgent else wc.textSecondary
                    Text(
                        text = "${next.course ?: next.type} · $dayLabel",
                        style = TextStyle(
                            color = ColorProvider(day = subColor, night = subColor),
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

class NextDeadlineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextDeadlineWidget()
}

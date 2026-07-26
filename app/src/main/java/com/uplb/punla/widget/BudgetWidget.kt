package com.uplb.punla.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.uplb.punla.MainActivity
import com.uplb.punla.data.BudgetPeriod
import com.uplb.punla.data.PunlaRepository
import com.uplb.punla.ui.QuickAddExpenseActivity
import com.uplb.punla.ui.theme.resolvePalette
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

class BudgetWidget : GlanceAppWidget() {
    // Two declared sizes: the compact layout stays pixel-identical to what
    // the widget looked like before this chart existed, and the taller one
    // adds the 7-day spending row when there's room for it.
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 90.dp),
            DpSize(180.dp, 150.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = PunlaRepository(context)
        val budget = repo.monthlyBudget
        val spent = repo.budgetSpentThisMonth()
        val remaining = budget - spent
        val period = repo.budgetPeriod
        // Weekly Budgeting feature — only computed when actually shown, so
        // widgets that stay on the monthly-only default don't pay for an
        // extra expense-list fetch on every refresh.
        val weeklyBudgetAmt = if (period != BudgetPeriod.MONTHLY) repo.weeklyBudgetAmount() else 0.0
        val weeklySpent = if (period != BudgetPeriod.MONTHLY) repo.weeklyBudgetSpent() else 0.0
        val weeklyRemaining = weeklyBudgetAmt - weeklySpent
        val dailySpend = repo.spendingLast7Days()
        val peso = NumberFormat.getNumberInstance(Locale("en", "PH")).apply { maximumFractionDigits = 0 }

        val openBudgetIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_START_ROUTE, "budget")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val quickAddIntent = Intent(context, QuickAddExpenseActivity::class.java).apply {
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
                        if (bgImage != null) m.background(bgImage)
                        else m.background(ColorProvider(day = wc.backgroundFallback, night = wc.backgroundFallback))
                    }
                    .padding(12.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text = "BUDGET REMAINING",
                        style = TextStyle(
                            color = ColorProvider(day = wc.accent, night = wc.accent),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    // Quick-add: opens a small floating card to log an
                    // expense in a couple of taps, no full app launch.
                    Box(
                        modifier = GlanceModifier
                            .size(22.dp)
                            .background(ColorProvider(day = wc.accent, night = wc.accent))
                            .clickable(actionStartActivity(quickAddIntent)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            style = TextStyle(
                                color = ColorProvider(day = wc.onAccent, night = wc.onAccent),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(4.dp))
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionStartActivity(openBudgetIntent))
                ) {
                    if (budget <= 0.0 && weeklyBudgetAmt <= 0.0) {
                        Text(
                            text = "No budget set",
                            style = TextStyle(color = ColorProvider(day = wc.textPrimary, night = wc.textPrimary))
                        )
                    } else when (period) {
                        BudgetPeriod.MONTHLY -> {
                            val remainingColor = if (remaining < 0) wc.urgent else wc.textPrimary
                            Text(
                                text = "₱${peso.format(remaining)}",
                                style = TextStyle(
                                    color = ColorProvider(day = remainingColor, night = remainingColor),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            )
                            Text(
                                text = "of ₱${peso.format(budget)} · spent ₱${peso.format(spent)}",
                                style = TextStyle(color = ColorProvider(day = wc.textSecondary, night = wc.textSecondary), fontSize = 12.sp)
                            )
                        }
                        BudgetPeriod.WEEKLY -> {
                            val remainingColor = if (weeklyRemaining < 0) wc.urgent else wc.textPrimary
                            Text(
                                text = "₱${peso.format(weeklyRemaining)}",
                                style = TextStyle(
                                    color = ColorProvider(day = remainingColor, night = remainingColor),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            )
                            Text(
                                text = "left this week of ₱${peso.format(weeklyBudgetAmt)}",
                                style = TextStyle(color = ColorProvider(day = wc.textSecondary, night = wc.textSecondary), fontSize = 12.sp)
                            )
                        }
                        BudgetPeriod.BOTH -> {
                            // Combined stacked view per the plan doc:
                            // "₱850 left this week / ₱2,100 left this month".
                            val weeklyColor = if (weeklyRemaining < 0) wc.urgent else wc.textPrimary
                            val monthlyColor = if (remaining < 0) wc.urgent else wc.textSecondary
                            Text(
                                text = "₱${peso.format(weeklyRemaining)} left this week",
                                style = TextStyle(
                                    color = ColorProvider(day = weeklyColor, night = weeklyColor),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            )
                            Text(
                                text = "₱${peso.format(remaining)} left this month",
                                style = TextStyle(
                                    color = ColorProvider(day = monthlyColor, night = monthlyColor),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
                if (size.height >= 150.dp) {
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    SpendingBarRow(dailySpend, wc)
                }
            }
        }
    }
}

@Composable
private fun SpendingBarRow(days: List<Pair<LocalDate, Double>>, wc: WidgetColors) {
    val maxAmount = days.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val maxBarHeight = 36.dp
    val minBarHeight = 3.dp // zero-spend days still render a sliver, not nothing

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.Bottom
    ) {
        days.forEach { (date, amount) ->
            val fraction = (amount / maxAmount).coerceIn(0.0, 1.0)
            val barHeight = maxOf(minBarHeight, maxBarHeight * fraction.toFloat())
            val isToday = date == LocalDate.now()

            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                Box(
                    modifier = GlanceModifier
                        .height(maxBarHeight)
                        .fillMaxWidth()
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val barColor = if (isToday) wc.barActive else wc.barInactive
                    Box(
                        modifier = GlanceModifier
                            .height(barHeight)
                            .fillMaxWidth()
                            .background(ColorProvider(day = barColor, night = barColor))
                    ) {}
                }
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = date.dayOfWeek.name.take(1), // M T W T F S S
                    style = TextStyle(
                        color = ColorProvider(day = wc.textMuted, night = wc.textMuted),
                        fontSize = 9.sp
                    )
                )
            }
        }
    }
}

class BudgetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BudgetWidget()
}

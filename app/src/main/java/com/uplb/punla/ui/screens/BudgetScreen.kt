package com.uplb.punla.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uplb.punla.data.BudgetPeriod
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ui.theme.LocalPunlaPalette
import com.uplb.punla.ui.theme.PunlaMono
import com.uplb.punla.ui.theme.PunlaPalette
import java.time.LocalDate
import java.time.YearMonth

fun getCategoryColor(category: String, palette: PunlaPalette): Color = when (category.lowercase()) {
    "food", "food / allowance" -> palette.catFood
    "transpo", "transportation" -> palette.catTranspo
    "load", "mobile load / internet" -> palette.catLoad
    "supplies" -> palette.catSupplies
    "org", "org / activities" -> palette.catOrg
    else -> palette.catMisc
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(vm: PunlaViewModel, openFormOnStart: Boolean = false) {
    val expenses by vm.expenses.collectAsState()
    // Roadmap C — withhold "No expenses logged yet" until Room's first
    // real emission, so it doesn't flash for a frame on cold launch.
    val dataReady by vm.isDataReady.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var budgetInput by remember(vm.monthlyBudget) { mutableStateOf(vm.monthlyBudget.let { if (it > 0) it.toInt().toString() else "" }) }

    // Quick-add: when launched from the global quick-add FAB, jump straight
    // into the "new expense" form instead of making the user tap + again.
    LaunchedEffect(openFormOnStart) {
        if (openFormOnStart) showForm = true
    }

    val now = LocalDate.now()
    val currentYearMonth = YearMonth.now()
    val daysInMonth = currentYearMonth.lengthOfMonth()
    val currentDayOfMonth = now.dayOfMonth

    val currentMonthExpenses = expenses.filter {
        val d = runCatching { LocalDate.parse(it.date) }.getOrNull()
        d != null && d.year == now.year && d.monthValue == now.monthValue
    }

    val spent = currentMonthExpenses.sumOf { it.amount }
    val budget = vm.monthlyBudget
    val remaining = budget - spent
    val overBudget = remaining < 0
    val progress = if (budget > 0) (spent / budget).coerceIn(0.0, 1.0).toFloat() else 0f

    // Daily & Weekly Averages
    val dailyAvg = if (currentDayOfMonth > 0) spent / currentDayOfMonth else 0.0
    val weeklyAvg = dailyAvg * 7

    // Pace Card
    val budgetDailyLimit = if (daysInMonth > 0) budget / daysInMonth else 0.0
    val isOverPace = dailyAvg > budgetDailyLimit
    val paceDifference = kotlin.math.abs(dailyAvg - budgetDailyLimit)

    // Weekly Budgeting feature — mirrors the monthly figures above but
    // reuses the repository's pure functions (over the same reactive
    // `expenses` list already collected for the monthly view) so this
    // screen and the widget never compute it two different ways.
    val period = vm.budgetPeriod
    val weekStart = remember(now, vm.weekStartDay) { vm.repo.currentWeekStart(now) }
    val weeklyBudgetAmt = remember(expenses, weekStart, vm.weeklyBudgetOverride, vm.weeklyRolloverEnabled, budget) {
        vm.repo.weeklyBudgetAmountFromList(expenses, weekStart)
    }
    val weeklySpent = remember(expenses, weekStart) {
        vm.repo.weeklyBudgetSpentFromList(expenses, weekStart)
    }
    val weeklyRemaining = weeklyBudgetAmt - weeklySpent
    val weeklyOverBudget = weeklyRemaining < 0
    val weeklyProgress = if (weeklyBudgetAmt > 0) (weeklySpent / weeklyBudgetAmt).coerceIn(0.0, 1.0).toFloat() else 0f

    // Pace indicator (plan doc #4.2): % of the week elapsed vs. % of the
    // weekly budget already spent — "you've spent 60% of the week's budget
    // but it's only Wednesday" — rather than the monthly card's ₱/day framing.
    val daysElapsedInWeek = (java.time.temporal.ChronoUnit.DAYS.between(weekStart, now) + 1).coerceIn(1, 7)
    val expectedSpendPct = daysElapsedInWeek / 7.0
    val actualSpendPct = if (weeklyBudgetAmt > 0) weeklySpent / weeklyBudgetAmt else 0.0
    val weeklyPace = actualSpendPct - expectedSpendPct // positive = overspending

    // Category Breakdown
    val categoryTotals = currentMonthExpenses.groupBy { it.category }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    // Roadmap #5 — spending trend over the last 6 months. `expenses` (unlike
    // `currentMonthExpenses`) already holds the full history via the
    // reactive Room Flow, so this is a plain in-memory group-by keyed on the
    // "YYYY-MM" prefix of each ISO date rather than a second DB query.
    val monthlyTrend = remember(expenses) {
        (5 downTo 0).map { offset ->
            val ym = currentYearMonth.minusMonths(offset.toLong())
            val total = expenses.filter {
                val d = runCatching { LocalDate.parse(it.date) }.getOrNull()
                d != null && YearMonth.from(d) == ym
            }.sumOf { it.amount }
            MonthSpend(ym, total)
        }
    }

    // Deeper insights — previous month's totals (overall + per category) so
    // the Insights card can talk about trends and comparisons instead of
    // just this month's raw numbers.
    val prevYearMonth = remember(currentYearMonth) { currentYearMonth.minusMonths(1) }
    val prevMonthExpenses = remember(expenses, prevYearMonth) {
        expenses.filter {
            val d = runCatching { LocalDate.parse(it.date) }.getOrNull()
            d != null && YearMonth.from(d) == prevYearMonth
        }
    }
    val prevMonthTotal = prevMonthExpenses.sumOf { it.amount }
    val prevCategoryTotals = remember(prevMonthExpenses) {
        prevMonthExpenses.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount } }
    }
    // Projected month-end total at the current daily pace, for "if you keep
    // spending like this" framing.
    val projectedMonthEnd = dailyAvg * daysInMonth
    // Category with the largest peso swing vs last month — the headline
    // "what changed" insight. Only meaningful once there's a previous month
    // to compare against.
    val biggestMover = if (prevMonthTotal > 0) {
        categoryTotals.map { (cat, amt) -> Triple(cat, amt, amt - (prevCategoryTotals[cat] ?: 0.0)) }
            .maxByOrNull { (_, _, delta) -> kotlin.math.abs(delta) }
    } else null

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showForm = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Icon(Icons.Default.Add, "Add expense") }
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
            // Weekly Budgeting feature — shown first per the plan doc's
            // "₱850 left this week / ₱2,100 left this month" combined
            // framing, so the more time-sensitive figure reads first.
            if (period == BudgetPeriod.WEEKLY || period == BudgetPeriod.BOTH) {
                item {
                    Spacer(Modifier.height(8.dp))
                    RemainingCard(
                        label = "REMAINING THIS WEEK",
                        remaining = weeklyRemaining,
                        spent = weeklySpent,
                        budget = weeklyBudgetAmt,
                        progress = weeklyProgress,
                        overBudget = weeklyOverBudget,
                        budgetSuffix = "week's budget"
                    )
                }
            }
            if (period == BudgetPeriod.MONTHLY || period == BudgetPeriod.BOTH) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (overBudget) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                "REMAINING THIS MONTH",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            PesoText(
                                remaining,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp),
                                color = if (overBudget) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = if (overBudget) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                PesoText(spent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "of ${"\u20b1${"%,.0f".format(budget)}"} budget",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = budgetInput,
                                    onValueChange = { budgetInput = it },
                                    label = { Text("Monthly budget (₱)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { budgetInput.toDoubleOrNull()?.let { vm.setBudget(it) } }) { Text("Set") }
                            }
                        }
                    }
                }
            }

            // Stat Boxes (Daily Avg & Weekly Avg)
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("DAILY AVG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            PesoText(dailyAvg, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("WEEKLY AVG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            PesoText(weeklyAvg, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }

            // Weekly Pace Card (plan doc #4.2) — % of the week's budget spent
            // vs. % of the week elapsed, e.g. "spent 60% of this week's
            // budget but it's only Wednesday". Separate from the monthly
            // pace card below, which uses a ₱/day framing instead.
            if ((period == BudgetPeriod.WEEKLY || period == BudgetPeriod.BOTH) && weeklyBudgetAmt > 0.0) {
                item {
                    Spacer(Modifier.height(10.dp))
                    val weeklyIsOverPace = weeklyPace > 0
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (weeklyIsOverPace) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "It's day $daysElapsedInWeek of 7 this week",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "You've spent ${"%,.0f".format(actualSpendPct * 100)}% of this week's budget" +
                                    if (weeklyIsOverPace) " — ahead of pace" else " — on track",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = if (weeklyIsOverPace) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Pace Card
            if (budget > 0.0) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOverPace) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                if (isOverPace) {
                                    Text(
                                        "Over pace by ₱${"%,.0f".format(paceDifference)}/day vs ₱${"%,.0f".format(budgetDailyLimit)}/day budget",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    Text(
                                        "On track: actual ₱${"%,.0f".format(dailyAvg)}/day vs ₱${"%,.0f".format(budgetDailyLimit)}/day budget",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Spending Trend Section (roadmap #5)
            if (expenses.isNotEmpty()) {
                item {
                    SectionLabel("Spending Trend")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Last 6 months",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            SpendingTrendChart(monthlyTrend)
                        }
                    }
                }
            }

            // Insights Section — deeper analysis on top of the raw Spending
            // Trend chart: month-over-month change, the category that moved
            // the most, and a pace-based month-end projection.
            if (expenses.isNotEmpty()) {
                item {
                    SectionLabel("Insights")
                    SpendingInsightsCard(
                        spent = spent,
                        budget = budget,
                        prevMonthTotal = prevMonthTotal,
                        projectedMonthEnd = projectedMonthEnd,
                        biggestMover = biggestMover
                    )
                }
            }

            // Category Breakdown Section
            if (categoryTotals.isNotEmpty()) {
                item {
                    SectionLabel("Spending by Category")
                }
                items(categoryTotals, key = { (cat, _) -> cat }) { (cat, amt) ->
                    val catPct = if (spent > 0) (amt / spent).toFloat() else 0f
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .background(getCategoryColor(cat, LocalPunlaPalette.current), MaterialTheme.shapes.extraSmall)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(cat, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                }
                                PesoText(amt, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = PunlaMono, fontWeight = FontWeight.SemiBold))
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { catPct },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = getCategoryColor(cat, LocalPunlaPalette.current),
                                trackColor = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            if (expenses.isNotEmpty()) {
                item { SectionLabel("Recent expenses") }
                items(expenses.sortedByDescending { it.date }, key = { it.id }) { e -> ExpenseCard(e, vm) }
            } else if (dataReady) {
                item {
                    EmptyState(
                        icon = Icons.Default.Receipt,
                        message = "No expenses logged yet. Tap + to add one."
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showForm) {
        ExpenseFormDialog(
            onDismiss = { showForm = false },
            onSave = { expense, repeat -> vm.addExpense(expense, repeat); showForm = false }
        )
    }
}

/**
 * The "REMAINING THIS ___" hero card — extracted so the Weekly Budgeting
 * feature's weekly card can share the exact same look as the (still
 * inline, since it also hosts the monthly budget input) monthly one above
 * it, instead of a second hand-copied version drifting out of sync.
 */
@Composable
private fun RemainingCard(
    label: String,
    remaining: Double,
    spent: Double,
    budget: Double,
    progress: Float,
    overBudget: Boolean,
    budgetSuffix: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(
            containerColor = if (overBudget) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            PesoText(
                remaining,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp),
                color = if (overBudget) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (overBudget) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                PesoText(spent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "of ${"\u20b1${"%,.0f".format(budget)}"} $budgetSuffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExpenseCard(e: Expense, vm: PunlaViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .shadow(1.dp, MaterialTheme.shapes.medium, ambientColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f), spotColor = LocalPunlaPalette.current.shadowInk.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            AccentBar(getCategoryColor(e.category, LocalPunlaPalette.current))
            Row(
                Modifier.padding(14.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.category, style = MaterialTheme.typography.titleSmall)
                        if (e.isRecurring) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Repeat,
                                contentDescription = "Recurring",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (e.isFixed) {
                            Spacer(Modifier.width(6.dp))
                            Tag(
                                "Fixed",
                                container = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                onContainer = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        listOfNotNull(e.note?.ifBlank { null }, e.date).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PesoText(
                        e.amount,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (e.ruleId != null) {
                        IconButton(onClick = { vm.stopExpenseRecurrence(e.ruleId) }) {
                            Icon(Icons.Default.EventBusy, "Stop repeating", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { vm.deleteExpense(e) }) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** One bar of the spending trend chart — a calendar month + its total spend. */
private data class MonthSpend(val month: YearMonth, val total: Double)

/**
 * web equivalent: none — new for roadmap #5. A minimal bar chart (no
 * charting library in this native app), styled to match the rest of the
 * app's cards rather than reaching for a generic chart look. The current
 * month's bar is drawn in the full primary color; past months are drawn at
 * reduced opacity so the current month reads as the "you are here" bar.
 */
@Composable
private fun SpendingTrendChart(data: List<MonthSpend>, modifier: Modifier = Modifier) {
    val maxAmt = (data.maxOfOrNull { it.total } ?: 0.0).coerceAtLeast(1.0)
    val primary = MaterialTheme.colorScheme.primary
    val currentMonth = remember { YearMonth.now() }
    val monthLabelFormat = remember { java.time.format.DateTimeFormatter.ofPattern("MMM") }

    Column(modifier.fillMaxWidth()) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            val barCount = data.size
            if (barCount == 0) return@Canvas
            val gap = 10.dp.toPx()
            val barWidth = (size.width - gap * (barCount - 1)) / barCount
            data.forEachIndexed { i, monthSpend ->
                val heightFrac = (monthSpend.total / maxAmt).toFloat().coerceIn(0f, 1f)
                // Floor to a thin sliver instead of a zero-height bar, so a
                // ₱0 month is still visible as "there was a month here".
                val barHeight = (heightFrac * size.height).coerceAtLeast(3.dp.toPx())
                val x = i * (barWidth + gap)
                val alpha = if (monthSpend.month == currentMonth) 1f else 0.45f
                drawRoundRect(
                    color = primary.copy(alpha = alpha),
                    topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEach { monthSpend ->
                Text(
                    monthSpend.month.format(monthLabelFormat),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (monthSpend.month == currentMonth) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (monthSpend.month == currentMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/**
 * Deeper spending insights — month-over-month comparison, the category that
 * moved the most since last month, and a pace-based month-end projection.
 * Sits below the Spending Trend chart, using the same numbers the chart and
 * pace card already compute rather than issuing new queries.
 */
@Composable
private fun SpendingInsightsCard(
    spent: Double,
    budget: Double,
    prevMonthTotal: Double,
    projectedMonthEnd: Double,
    biggestMover: Triple<String, Double, Double>?
) {
    data class Insight(val text: String, val positive: Boolean?)

    val insights = buildList {
        // Month-over-month.
        if (prevMonthTotal > 0) {
            val pct = ((spent - prevMonthTotal) / prevMonthTotal) * 100
            val direction = if (pct >= 0) "up" else "down"
            add(
                Insight(
                    "Spending is $direction ${"%,.0f".format(kotlin.math.abs(pct))}% vs last month (₱${"%,.0f".format(prevMonthTotal)}).",
                    positive = pct <= 0
                )
            )
        }

        // Biggest category mover.
        biggestMover?.let { (cat, _, delta) ->
            if (kotlin.math.abs(delta) >= 1.0) {
                val direction = if (delta > 0) "up" else "down"
                add(
                    Insight(
                        "$cat is the biggest mover — $direction ₱${"%,.0f".format(kotlin.math.abs(delta))} from last month.",
                        positive = delta <= 0
                    )
                )
            }
        }

        // Pace-based projection.
        if (spent > 0) {
            val projText = "At this pace, you're on track to spend ₱${"%,.0f".format(projectedMonthEnd)} by month end"
            if (budget > 0) {
                val diff = projectedMonthEnd - budget
                if (diff > 0) {
                    add(Insight("$projText — ₱${"%,.0f".format(diff)} over your budget.", positive = false))
                } else {
                    add(Insight("$projText — within your budget.", positive = true))
                }
            } else {
                add(Insight("$projText.", positive = null))
            }
        }
    }

    if (insights.isEmpty()) return

    // UX polish plan (glass section) — this card is the doc's own reference
    // point for the opaque glass treatment's shadow/border starting pattern,
    // so it's the first one switched over to the shared GlassCard.
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(14.dp)
    ) {
        insights.forEachIndexed { i, insight ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                val dotColor = when (insight.positive) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.secondary
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(6.dp)
                        .background(dotColor, MaterialTheme.shapes.extraSmall)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    insight.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private val REPEAT_OPTIONS = listOf(null to "Doesn't repeat", "weekly" to "Weekly", "monthly" to "Monthly")
private val CATEGORY_OPTIONS = listOf(
    "Food / Allowance",
    "Transportation",
    "Mobile Load / Internet",
    "Supplies",
    "Org / Activities",
    "Miscellaneous"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseFormDialog(onDismiss: () -> Unit, onSave: (Expense, String?) -> Unit) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val palette = LocalPunlaPalette.current
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CATEGORY_OPTIONS.first()) }
    var note by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf<String?>(null) }
    var isFixed by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now().toString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense") },
        text = {
            Column {
                PunlaField(
                    "Amount (₱)",
                    amount,
                    { amount = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                PunlaDropdownField(
                    "Category",
                    category,
                    CATEGORY_OPTIONS,
                    onSelect = { category = CATEGORY_OPTIONS[it] },
                    optionLeadingColor = { getCategoryColor(CATEGORY_OPTIONS[it], palette) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                PunlaField("Note", note, { note = it }, placeholder = "Optional", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PunlaDropdownField(
                    "Repeats",
                    REPEAT_OPTIONS.first { it.first == repeat }.second,
                    REPEAT_OPTIONS.map { it.second },
                    onSelect = { repeat = REPEAT_OPTIONS[it].first },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { isFixed = !isFixed },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isFixed, onCheckedChange = { isFixed = it })
                    Column {
                        Text("Fixed / recurring bill", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Rent, tuition, subscriptions — left out of the weekly budget view.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                amount.toDoubleOrNull()?.let {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onSave(Expense(amount = it, category = category, date = today, note = note.ifBlank { null }, isFixed = isFixed), repeat)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

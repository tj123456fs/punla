package com.uplb.punla.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.uplb.punla.ml.recurringExpenseCandidates
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
    val expenseRules by vm.expenseRules.collectAsState()
    // Roadmap C — withhold "No expenses logged yet" until Room's first
    // real emission, so it doesn't flash for a frame on cold launch.
    val dataReady by vm.isDataReady.collectAsState()
    val screenGutter = punlaScreenHorizontalPadding()
    var showForm by rememberSaveable { mutableStateOf(false) }
    var budgetInput by rememberSaveable(vm.monthlyBudget) { mutableStateOf(vm.monthlyBudget.let { if (it > 0) it.toInt().toString() else "" }) }
    var budgetTouched by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteExpenseId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingExpenseId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCategoryLimits by rememberSaveable { mutableStateOf(false) }
    val pendingDeleteExpense = remember(pendingDeleteExpenseId, expenses) { expenses.firstOrNull { it.id == pendingDeleteExpenseId } }
    val editingExpense = remember(editingExpenseId, expenses) { expenses.firstOrNull { it.id == editingExpenseId } }

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
    val recurringCandidates = remember(expenses, expenseRules, vm.dismissedExpensePatternKeys) {
        recurringExpenseCandidates(expenses, expenseRules, vm.dismissedExpensePatternKeys).take(3)
    }

    // Budget 2.0 — reserve future fixed recurring commitments before
    // calculating what is genuinely safe to spend on discretionary items.
    val monthEnd = currentYearMonth.atEndOfMonth()
    val upcomingFixedCommitments = remember(expenseRules, now) {
        vm.repo.projectedFixedCommitmentsFromRules(expenseRules, now, monthEnd)
    }
    val monthlyFlexibleRemaining = remaining - upcomingFixedCommitments
    val monthDaysRemaining = (java.time.temporal.ChronoUnit.DAYS.between(now, monthEnd) + 1).coerceAtLeast(1)
    val monthlySafePerDay = if (budget > 0.0) monthlyFlexibleRemaining / monthDaysRemaining else 0.0
    val weekEnd = weekStart.plusDays(6)
    val weekDaysRemaining = (java.time.temporal.ChronoUnit.DAYS.between(now, weekEnd) + 1).coerceAtLeast(1)
    val weeklySafePerDay = if (weeklyBudgetAmt > 0.0) weeklyRemaining / weekDaysRemaining else 0.0
    val safePerDay = when (period) {
        BudgetPeriod.MONTHLY -> monthlySafePerDay
        BudgetPeriod.WEEKLY -> weeklySafePerDay
        BudgetPeriod.BOTH -> listOfNotNull(
            monthlySafePerDay.takeIf { budget > 0.0 },
            weeklySafePerDay.takeIf { weeklyBudgetAmt > 0.0 }
        ).minOrNull() ?: 0.0
    }
    val safeBasis = when (period) {
        BudgetPeriod.MONTHLY -> "month"
        BudgetPeriod.WEEKLY -> "week"
        BudgetPeriod.BOTH -> when {
            budget <= 0.0 -> "week"
            weeklyBudgetAmt <= 0.0 -> "month"
            monthlySafePerDay <= weeklySafePerDay -> "month"
            else -> "week"
        }
    }
    val todaySpent = remember(expenses, now) {
        expenses.filter { it.date == now.toString() && !it.isFixed }.sumOf { it.amount }
    }
    val categoryLimits = vm.categoryBudgetLimits
    val categorySpentMap = remember(categoryTotals) { categoryTotals.toMap() }
    val categoryRows = remember(categoryTotals, categoryLimits) {
        (categoryTotals.map { it.first } + categoryLimits.keys)
            .distinct()
            .map { it to (categorySpentMap[it] ?: 0.0) }
            .sortedByDescending { it.second }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showForm = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Expense") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = screenGutter)) {
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
                            val parsedBudget = budgetInput.toDoubleOrNull()
                            val invalidBudget = budgetTouched && (parsedBudget == null || parsedBudget < 0)
                            Row(verticalAlignment = Alignment.Top) {
                                OutlinedTextField(
                                    value = budgetInput,
                                    onValueChange = { budgetInput = it; budgetTouched = true },
                                    label = { Text("Monthly budget (₱)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    isError = invalidBudget,
                                    supportingText = if (invalidBudget) {
                                        { Text("Enter a valid amount of 0 or more.") }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        budgetTouched = true
                                        if (parsedBudget != null && parsedBudget >= 0) vm.setBudget(parsedBudget)
                                    },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) { Text("Set") }
                            }
                        }
                    }
                }
            }

            if (budget > 0.0 || weeklyBudgetAmt > 0.0) {
                item {
                    Spacer(Modifier.height(12.dp))
                    SafeToSpendCard(
                        safePerDay = safePerDay,
                        basis = safeBasis,
                        todaySpent = todaySpent,
                        upcomingFixed = upcomingFixedCommitments.takeIf { period != BudgetPeriod.WEEKLY } ?: 0.0,
                        daysRemaining = if (safeBasis == "week") weekDaysRemaining else monthDaysRemaining
                    )
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

            if (recurringCandidates.isNotEmpty()) {
                item { SectionLabel("Pattern suggestions") }
                items(recurringCandidates, key = { it.key }) { pattern ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Looks recurring: ${pattern.label}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        "About ₱${"%,.2f".format(pattern.typicalAmount)} · ${pattern.occurrences} entries" +
                                            (pattern.cadenceDays?.let { " · roughly every $it days" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                IconButton(onClick = { vm.dismissExpensePattern(pattern.key) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss suggestion")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.createRecurringRuleFromPattern(pattern) }) {
                                    Text("Make recurring")
                                }
                                TextButton(onClick = { vm.dismissExpensePattern(pattern.key) }) {
                                    Text("Not recurring")
                                }
                            }
                        }
                    }
                }
            }

            // Category guardrails — optional monthly caps turn the old
            // percentage-only breakdown into something actionable. Categories
            // without a cap keep the original share-of-spending behavior.
            if (categoryRows.isNotEmpty() || dataReady) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel("Spending by Category")
                        TextButton(onClick = { showCategoryLimits = true }) { Text("Set limits") }
                    }
                }
                items(categoryRows, key = { (cat, _) -> cat }) { (cat, amt) ->
                    val limit = categoryLimits[cat]
                    val progressValue = if (limit != null && limit > 0.0) {
                        (amt / limit).coerceIn(0.0, 1.0).toFloat()
                    } else if (spent > 0) {
                        (amt / spent).coerceIn(0.0, 1.0).toFloat()
                    } else 0f
                    val overLimit = limit != null && amt > limit
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (overLimit) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
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
                                Column(horizontalAlignment = Alignment.End) {
                                    PesoText(amt, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = PunlaMono, fontWeight = FontWeight.SemiBold))
                                    if (limit != null && limit > 0.0) {
                                        Text(
                                            if (overLimit) "₱${"%,.0f".format(amt - limit)} over"
                                            else "₱${"%,.0f".format(limit - amt)} left of ₱${"%,.0f".format(limit)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (overLimit) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progressValue },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = if (overLimit) MaterialTheme.colorScheme.secondary else getCategoryColor(cat, LocalPunlaPalette.current),
                                trackColor = MaterialTheme.colorScheme.outline
                            )
                            if (limit == null && spent > 0.0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${"%,.0f".format((amt / spent) * 100)}% of this month's spending",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (expenses.isNotEmpty()) {
                item { SectionLabel("Recent expenses") }
                items(expenses.sortedByDescending { it.date }, key = { it.id }) { e ->
                    ExpenseCard(
                        e = e,
                        vm = vm,
                        onEdit = { editingExpenseId = e.id },
                        onDelete = { pendingDeleteExpenseId = e.id }
                    )
                }
            } else if (dataReady) {
                item {
                    EmptyState(
                        icon = Icons.Default.Receipt,
                        message = "No expenses logged yet.",
                        actionLabel = "Add expense",
                        onAction = { showForm = true }
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

    if (editingExpense != null) {
        ExpenseFormDialog(
            initialExpense = editingExpense,
            onDismiss = { editingExpenseId = null },
            onSave = { expense, _ ->
                vm.updateExpense(expense)
                editingExpenseId = null
            }
        )
    }

    if (showCategoryLimits) {
        CategoryBudgetDialog(
            current = categoryLimits,
            onDismiss = { showCategoryLimits = false },
            onSave = { limits ->
                vm.updateCategoryBudgetLimits(limits)
                showCategoryLimits = false
            }
        )
    }

    if (pendingDeleteExpense != null) {
        DestructiveActionDialog(
            title = "Delete expense?",
            message = "Remove ₱${"%,.2f".format(pendingDeleteExpense.amount)} from ${pendingDeleteExpense.category}?",
            onConfirm = {
                vm.deleteExpense(pendingDeleteExpense)
                pendingDeleteExpenseId = null
            },
            onDismiss = { pendingDeleteExpenseId = null }
        )
    }
}

@Composable
private fun SafeToSpendCard(
    safePerDay: Double,
    basis: String,
    todaySpent: Double,
    upcomingFixed: Double,
    daysRemaining: Long
) {
    val overPace = safePerDay < 0.0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (overPace) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.tertiaryContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "SAFE TO SPEND TODAY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            if (overPace) {
                Text(
                    "Pause discretionary spending",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    "Your remaining $basis budget is already below the safe pace by about ₱${"%,.0f".format(-safePerDay)} per remaining day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                PesoText(
                    safePerDay,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "per day for the next $daysRemaining day${if (daysRemaining == 1L) "" else "s"} · based on the tighter $basis limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Spent today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PesoText(todaySpent, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                if (upcomingFixed > 0.0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Reserved fixed bills", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PesoText(upcomingFixed, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
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
private fun ExpenseCard(e: Expense, vm: PunlaViewModel, onEdit: () -> Unit, onDelete: () -> Unit) {
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
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.ruleId != null) {
                        IconButton(onClick = { vm.stopExpenseRecurrence(e.ruleId) }) {
                            Icon(Icons.Default.EventBusy, "Stop repeating", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDelete) {
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
private fun ExpenseFormDialog(
    initialExpense: Expense? = null,
    onDismiss: () -> Unit,
    onSave: (Expense, String?) -> Unit
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val palette = LocalPunlaPalette.current
    val todayDate = remember { LocalDate.now() }
    val today = todayDate.toString()
    val initialAmount = initialExpense?.amount?.let { value ->
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    } ?: ""
    var amount by rememberSaveable(initialExpense?.id) { mutableStateOf(initialAmount) }
    var category by rememberSaveable(initialExpense?.id) { mutableStateOf(initialExpense?.category ?: CATEGORY_OPTIONS.first()) }
    var note by rememberSaveable(initialExpense?.id) { mutableStateOf(initialExpense?.note.orEmpty()) }
    var date by rememberSaveable(initialExpense?.id) { mutableStateOf(initialExpense?.date ?: today) }
    var repeat by rememberSaveable(initialExpense?.id) { mutableStateOf<String?>(null) }
    var isFixed by rememberSaveable(initialExpense?.id) { mutableStateOf(initialExpense?.isFixed ?: false) }
    var amountTouched by rememberSaveable(initialExpense?.id) { mutableStateOf(false) }
    var dateTouched by rememberSaveable(initialExpense?.id) { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable(initialExpense?.id) { mutableStateOf(false) }

    val parsedAmount = amount.toDoubleOrNull()
    val parsedDate = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
    val invalidAmount = amountTouched && (parsedAmount == null || parsedAmount <= 0)
    val invalidDate = dateTouched && (parsedDate == null || parsedDate.isAfter(todayDate))
    val isEditing = initialExpense != null
    val isDirty = if (initialExpense == null) {
        amount.isNotBlank() || category != CATEGORY_OPTIONS.first() || note.isNotBlank() ||
            date != today || repeat != null || isFixed
    } else {
        parsedAmount != initialExpense.amount || category != initialExpense.category ||
            note.trim().ifBlank { null } != initialExpense.note || date.trim() != initialExpense.date ||
            isFixed != initialExpense.isFixed
    }

    fun requestDismiss() {
        if (isDirty) showDiscardConfirm = true else onDismiss()
    }

    AlertDialog(
        onDismissRequest = { requestDismiss() },
        title = {
            Text(
                when {
                    showDiscardConfirm -> if (isEditing) "Discard changes?" else "Discard expense?"
                    isEditing -> "Edit expense"
                    else -> "Add expense"
                }
            )
        },
        text = {
            if (showDiscardConfirm) {
                Text(if (isEditing) "Your changes will be lost." else "Your unsaved expense details will be lost.")
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    PunlaField(
                        "Amount (₱)",
                        amount,
                        { amount = it; amountTouched = true },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        isError = invalidAmount,
                        supportingText = if (invalidAmount) "Enter an amount greater than 0." else null
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
                    PunlaField(
                        "Date (YYYY-MM-DD)",
                        date,
                        { date = it; dateTouched = true },
                        modifier = Modifier.fillMaxWidth(),
                        isError = invalidDate,
                        supportingText = if (invalidDate) "Use a valid date that isn't in the future." else null
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { date = today; dateTouched = true }) { Text("Today") }
                        TextButton(onClick = { date = todayDate.minusDays(1).toString(); dateTouched = true }) { Text("Yesterday") }
                    }

                    if (!isEditing) {
                        PunlaDropdownField(
                            "Repeats",
                            REPEAT_OPTIONS.first { it.first == repeat }.second,
                            REPEAT_OPTIONS.map { it.second },
                            onSelect = { repeat = REPEAT_OPTIONS[it].first },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (initialExpense?.ruleId != null) {
                        Text(
                            "This changes this occurrence only. The recurring rule for future entries stays unchanged.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { isFixed = !isFixed },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isFixed, onCheckedChange = { isFixed = it })
                        Column {
                            Text("Fixed / recurring bill", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Rent, tuition, subscriptions — left out of the weekly discretionary total.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showDiscardConfirm) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Discard") }
            } else {
                TextButton(onClick = {
                    amountTouched = true
                    dateTouched = true
                    if (parsedAmount != null && parsedAmount > 0 && parsedDate != null && !parsedDate.isAfter(todayDate)) {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        val saved = initialExpense?.copy(
                            amount = parsedAmount,
                            category = category,
                            date = parsedDate.toString(),
                            note = note.trim().ifBlank { null },
                            isFixed = isFixed
                        ) ?: Expense(
                            amount = parsedAmount,
                            category = category,
                            date = parsedDate.toString(),
                            note = note.trim().ifBlank { null },
                            isFixed = isFixed
                        )
                        onSave(saved, if (isEditing) null else repeat)
                    }
                }) { Text(if (isEditing) "Save changes" else "Save") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (showDiscardConfirm) showDiscardConfirm = false else requestDismiss()
            }) { Text(if (showDiscardConfirm) "Keep editing" else "Cancel") }
        }
    )
}

@Composable
private fun CategoryBudgetDialog(
    current: Map<String, Double>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Double>) -> Unit
) {
    val values = remember(current) {
        mutableStateMapOf<String, String>().apply {
            CATEGORY_OPTIONS.forEach { category ->
                val amount = current[category]
                put(category, amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty())
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly category limits") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Set guardrails only where they're useful. Leave a category blank for no limit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                CATEGORY_OPTIONS.forEach { category ->
                    PunlaField(
                        category,
                        values[category].orEmpty(),
                        { input -> values[category] = input.filter { ch -> ch.isDigit() || ch == '.' } },
                        placeholder = "No limit",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val limits = buildMap {
                    CATEGORY_OPTIONS.forEach { category ->
                        values[category]?.toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }?.let { put(category, it) }
                    }
                }
                onSave(limits)
            }) { Text("Save limits") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

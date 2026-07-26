package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "expense_rules")
data class ExpenseRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val category: String,
    val note: String? = null,
    val startDate: String, // ISO yyyy-MM-dd
    val repeat: String,    // "weekly" | "monthly"
    val lastGenerated: String,
    // Weekly Budgeting feature — carried onto every auto-generated
    // occurrence below, since a recurring rule for rent/tuition/subscriptions
    // is fixed every time it fires, not just the first instance.
    val isFixed: Boolean = false
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val category: String,
    val date: String, // ISO yyyy-MM-dd
    val note: String? = null,
    val ruleId: String? = null,
    val isRecurring: Boolean = false,
    // Weekly Budgeting feature (WEEKLY_BUDGET_INSTRUCTIONS.md #2/#4.3) — rent,
    // tuition installments, subscriptions, and other fixed/recurring bills.
    // Excluded from the weekly discretionary total and weekly pace
    // calculation so a tuition payment doesn't read as "blowing this week's
    // snacks-and-transport budget". Monthly figures are unchanged and still
    // include fixed expenses, same as before this field existed.
    val isFixed: Boolean = false
)

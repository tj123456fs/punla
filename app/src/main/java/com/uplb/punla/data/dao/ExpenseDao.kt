package com.uplb.punla.data.dao

import androidx.room.*
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.data.entity.ExpenseRule
import kotlinx.coroutines.flow.Flow

/**
 * One day's total spend, used for the Budget widget's 7-day mini bar chart.
 */
data class DailySpend(val date: String, val total: Double)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun getAll(): List<Expense>

    /**
     * Sums amounts for a given "YYYY-MM" prefix directly in SQL, instead of
     * pulling every expense row into memory and filtering in Kotlin. [date]
     * is stored as an ISO string ("YYYY-MM-DD"), so a prefix match on the
     * first 7 characters is equivalent to matching year + month.
     */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE substr(date, 1, 7) = :yearMonth")
    suspend fun sumForMonth(yearMonth: String): Double

    /**
     * Sums amounts per day from [startDate] (inclusive) onward, for the
     * Budget widget's 7-day mini bar chart. [date] is stored as an ISO
     * "yyyy-MM-dd" string, so a plain string comparison + GROUP BY works
     * without any date math in SQL.
     */
    @Query(
        """
        SELECT date, COALESCE(SUM(amount), 0) as total
        FROM expenses
        WHERE date >= :startDate
        GROUP BY date
        """
    )
    suspend fun sumByDaySince(startDate: String): List<DailySpend>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("DELETE FROM expenses")
    suspend fun clearAll()

    @Query("SELECT * FROM expense_rules")
    suspend fun getAllRules(): List<ExpenseRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: ExpenseRule)

    @Query("DELETE FROM expense_rules WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: String)

    @Query("DELETE FROM expense_rules")
    suspend fun clearAllRules()

    @Query("UPDATE expenses SET isRecurring = 0, ruleId = NULL WHERE ruleId = :ruleId")
    suspend fun detachRecurrence(ruleId: String)
}

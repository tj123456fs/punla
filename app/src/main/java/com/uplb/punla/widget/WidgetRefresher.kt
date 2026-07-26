package com.uplb.punla.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object WidgetRefresher {
    /** Call this after any write to classes, expenses, or deadlines so widgets reflect it immediately. */
    suspend fun refreshAll(context: Context) {
        NextClassWidget().updateAll(context)
        BudgetWidget().updateAll(context)
        NextDeadlineWidget().updateAll(context)
    }
}

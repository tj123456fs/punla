package com.uplb.punla.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object WidgetRefresher {
    private val refreshMutex = Mutex()

    /**
     * Refresh all home-screen widgets without occupying the caller's Main dispatcher.
     * Theme/background changes and ordinary CRUD taps often call this immediately
     * after a UI state change; keeping Glance composition/bitmap work on Default
     * prevents that secondary work from stealing the interaction frame. Bursts are
     * serialized so three quick writes don't render three widget sets concurrently.
     */
    suspend fun refreshAll(context: Context) = withContext(Dispatchers.Default) {
        refreshMutex.withLock {
            NextClassWidget().updateAll(context.applicationContext)
            BudgetWidget().updateAll(context.applicationContext)
            NextDeadlineWidget().updateAll(context.applicationContext)
        }
    }
}

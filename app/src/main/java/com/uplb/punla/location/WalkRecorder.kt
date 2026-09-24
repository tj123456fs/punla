package com.uplb.punla.location

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object WalkRecorder {
    const val ACTION_START = "com.uplb.punla.walk.START"
    const val ACTION_PAUSE = "com.uplb.punla.walk.PAUSE"
    const val ACTION_RESUME = "com.uplb.punla.walk.RESUME"
    const val ACTION_STOP = "com.uplb.punla.walk.STOP"

    fun start(context: Context) = send(context, ACTION_START)
    fun pause(context: Context) = send(context, ACTION_PAUSE)
    fun resume(context: Context) = send(context, ACTION_RESUME)
    fun stop(context: Context) = send(context, ACTION_STOP)

    private fun send(context: Context, action: String) {
        val intent = Intent(context, CampusWalkRecorderService::class.java).setAction(action)
        ContextCompat.startForegroundService(context, intent)
    }
}

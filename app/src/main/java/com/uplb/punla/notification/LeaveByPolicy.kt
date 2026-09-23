package com.uplb.punla.notification

/** Periodic reminders allow one worker interval before departure; exact delivery is not assumed. */
object LeaveByPolicy {
    data class Reminder(val leaveAt: Long, val startsInMinutes: Int, val travelMinutes: Int)
    fun reminder(startAt: Long, now: Long, travelMinutes: Int): Reminder? {
        val travel = travelMinutes.coerceIn(0, 120)
        val remaining = startAt - now
        if (remaining < 0 || remaining > (travel + 15L) * 60000L) return null
        return Reminder(startAt - travel * 60000L, kotlin.math.ceil(remaining / 60000.0).toInt(), travel)
    }
}

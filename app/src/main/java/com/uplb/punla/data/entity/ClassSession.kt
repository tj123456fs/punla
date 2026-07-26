package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single scheduled class block (mirrors `state.schedule` items in the original web app).
 * day is one of "Mon","Tue","Wed","Thu","Fri","Sat".
 * type is "lec" or "lab".
 * start / end are "HH:mm" 24h strings.
 */
@Entity(tableName = "class_sessions")
data class ClassSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val code: String,
    val section: String? = null,
    val title: String? = null,
    val day: String,
    val type: String, // "lec" | "lab"
    val start: String, // "HH:mm"
    val end: String,   // "HH:mm"
    val room: String? = null,
    val instructor: String? = null,
    // Roadmap #4 — tally of absences logged against this specific weekly
    // meeting block (a lec and lab for the same course are tracked
    // separately, since UP attendance is often taken per section/room).
    val absences: Int = 0
)

/**
 * Standard UP semester teaching-week length used to estimate the 20%-
 * absence drop-rule threshold (roadmap #4). Actual term length varies by
 * AY calendar and isn't tracked anywhere in this app, so this is a fixed,
 * good-enough approximation for a personal planner rather than an official
 * registrar computation.
 */
const val STANDARD_TERM_WEEKS = 15

/**
 * Allowed absences for this weekly meeting block before UP's 20%-absence
 * rule would drop the student, given [STANDARD_TERM_WEEKS] teaching weeks
 * and one meeting/week for this block.
 */
fun ClassSession.allowedAbsences(): Int = (0.2 * STANDARD_TERM_WEEKS).toInt()

package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One completed (or abandoned) Pomodoro focus block. Breaks are NOT stored
 * as rows — only "work" intervals count as study time. courseCode links
 * loosely to ClassSession.code / GradeCourse.code (a plain string, not a
 * foreign key) so a session can still be tagged after its class is deleted
 * or a semester is archived.
 */
@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseCode: String? = null,      // e.g. "MATH 20", null = "Untagged"
    val startedAt: Long,                 // epoch millis
    val endedAt: Long,                   // epoch millis
    val plannedMinutes: Int,             // the work-interval length it was run at
    val actualSeconds: Int,              // real elapsed focus time (see 1.3 pause handling)
    val completed: Boolean,              // true = ran to 00:00 naturally, false = stopped early
    val cyclesInSession: Int = 1         // which pomodoro # this was within its session run (see 1.4)
)

/** User-tunable durations, persisted via PunlaRepository (see 1.2) rather
 * than as a Room row — this is a settings object, not a log. */
data class PomodoroSettings(
    val workMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val cyclesBeforeLongBreak: Int = 4,
    val autoStartNext: Boolean = false
)

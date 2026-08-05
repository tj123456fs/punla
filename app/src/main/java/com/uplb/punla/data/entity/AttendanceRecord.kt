package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One attendance decision for one concrete occurrence of a weekly class.
 *
 * [occurrenceKey] is deterministic, so tapping the notification twice or
 * changing Attended -> Absent replaces the same row instead of creating
 * duplicate history entries.
 */
@Entity(
    tableName = "attendance_records",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["occurrenceDate"]),
        Index(value = ["sessionId", "occurrenceDate", "scheduledStart"], unique = true)
    ]
)
data class AttendanceRecord(
    @PrimaryKey val occurrenceKey: String,
    val sessionId: String,
    val classCode: String,
    /** ISO-8601 local date, for example 2026-08-05. */
    val occurrenceDate: String,
    /** Scheduled HH:mm start copied into the log for stable history. */
    val scheduledStart: String,
    /** One of [AttendanceStatus.ATTENDED] or [AttendanceStatus.ABSENT]. */
    val status: String,
    val loggedAt: Long = System.currentTimeMillis(),
    /** notification, schedule, dashboard, restore, etc. */
    val source: String = "app"
)

object AttendanceStatus {
    const val ATTENDED = "ATTENDED"
    const val ABSENT = "ABSENT"

    fun isValid(value: String): Boolean = value == ATTENDED || value == ABSENT
}

object AttendanceLog {
    fun occurrenceKey(sessionId: String, date: LocalDate, scheduledStart: String): String =
        "$sessionId|$date|$scheduledStart"

    /** Delta applied to ClassSession.absences when replacing a record. */
    fun absenceDelta(previousStatus: String?, newStatus: String): Int {
        require(AttendanceStatus.isValid(newStatus))
        val wasAbsent = previousStatus == AttendanceStatus.ABSENT
        val isAbsent = newStatus == AttendanceStatus.ABSENT
        return when {
            !wasAbsent && isAbsent -> 1
            wasAbsent && !isAbsent -> -1
            else -> 0
        }
    }

    fun forOccurrence(
        session: ClassSession,
        date: LocalDate,
        status: String,
        source: String = "app",
        loggedAt: Long = System.currentTimeMillis()
    ): AttendanceRecord {
        require(AttendanceStatus.isValid(status)) { "Unsupported attendance status: $status" }
        return AttendanceRecord(
            occurrenceKey = occurrenceKey(session.id, date, session.start),
            sessionId = session.id,
            classCode = session.code,
            occurrenceDate = date.toString(),
            scheduledStart = session.start,
            status = status,
            loggedAt = loggedAt,
            source = source
        )
    }
}

fun AttendanceRecord.isAttended(): Boolean = status == AttendanceStatus.ATTENDED
fun AttendanceRecord.isAbsent(): Boolean = status == AttendanceStatus.ABSENT

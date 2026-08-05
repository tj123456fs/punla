package com.uplb.punla.data

import com.uplb.punla.data.entity.AttendanceLog
import com.uplb.punla.data.entity.AttendanceStatus
import com.uplb.punla.data.entity.ClassSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AttendanceLogTest {
    private val session = ClassSession(
        id = "math-27-mon-0900",
        code = "MATH 27",
        day = "Mon",
        type = "lec",
        start = "09:00",
        end = "10:00"
    )

    @Test
    fun sameOccurrenceAlwaysUsesSameKey() {
        val date = LocalDate.of(2026, 8, 3)
        val attended = AttendanceLog.forOccurrence(session, date, AttendanceStatus.ATTENDED)
        val absent = AttendanceLog.forOccurrence(session, date, AttendanceStatus.ABSENT)

        assertEquals(attended.occurrenceKey, absent.occurrenceKey)
        assertEquals("math-27-mon-0900|2026-08-03|09:00", attended.occurrenceKey)
    }

    @Test
    fun differentWeeksCreateDifferentHistoryRows() {
        val first = AttendanceLog.forOccurrence(
            session,
            LocalDate.of(2026, 8, 3),
            AttendanceStatus.ATTENDED
        )
        val next = AttendanceLog.forOccurrence(
            session,
            LocalDate.of(2026, 8, 10),
            AttendanceStatus.ATTENDED
        )

        assertNotEquals(first.occurrenceKey, next.occurrenceKey)
    }

    @Test
    fun absenceTallyDeltaIsIdempotentAndReversible() {
        assertEquals(1, AttendanceLog.absenceDelta(null, AttendanceStatus.ABSENT))
        assertEquals(0, AttendanceLog.absenceDelta(AttendanceStatus.ABSENT, AttendanceStatus.ABSENT))
        assertEquals(-1, AttendanceLog.absenceDelta(AttendanceStatus.ABSENT, AttendanceStatus.ATTENDED))
        assertEquals(0, AttendanceLog.absenceDelta(AttendanceStatus.ATTENDED, AttendanceStatus.ATTENDED))
        assertEquals(1, AttendanceLog.absenceDelta(AttendanceStatus.ATTENDED, AttendanceStatus.ABSENT))
    }

    @Test
    fun recordsKeepTheScheduledClassIdentity() {
        val record = AttendanceLog.forOccurrence(
            session,
            LocalDate.of(2026, 8, 3),
            AttendanceStatus.ABSENT,
            source = "notification",
            loggedAt = 123L
        )

        assertEquals(session.id, record.sessionId)
        assertEquals(session.code, record.classCode)
        assertEquals("2026-08-03", record.occurrenceDate)
        assertEquals("09:00", record.scheduledStart)
        assertEquals("notification", record.source)
        assertTrue(AttendanceStatus.isValid(record.status))
    }
}

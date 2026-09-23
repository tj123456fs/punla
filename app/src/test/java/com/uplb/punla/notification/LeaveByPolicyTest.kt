package com.uplb.punla.notification

import org.junit.Assert.*
import org.junit.Test

class LeaveByPolicyTest {
    @Test fun longWalkWarnsBeforeDeparture() {
        val start = 60 * 60000L
        assertNull(LeaveByPolicy.reminder(start, 0, 30))
        val reminder = LeaveByPolicy.reminder(start, 15 * 60000L, 30)!!
        assertEquals(30 * 60000L, reminder.leaveAt)
        assertEquals(45, reminder.startsInMinutes)
    }
    @Test fun countdownRoundsUpAndPastClassesAreExcluded() {
        assertEquals(1, LeaveByPolicy.reminder(60001, 60000, 0)!!.startsInMinutes)
        assertNull(LeaveByPolicy.reminder(60000, 60001, 10))
    }
    @Test fun excessiveTravelIsBounded() {
        assertEquals(120, LeaveByPolicy.reminder(100000, 0, 1000)!!.travelMinutes)
    }
}

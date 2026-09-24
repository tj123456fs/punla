package com.uplb.punla.planning

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PlanningAgendaTest {
    private val date = LocalDate.of(2026, 9, 24)
    private val zone = ZoneId.of("Asia/Manila")
    private fun at(hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun openTimeExcludesPlannedBlocksButNotSkippedBlocks() {
        val block = DayPlanBlock(id = "a", taskId = "task", title = "Review", startAt = at(10), endAt = at(11))
        val rows = agendaForDate(emptyList(), listOf(block), listOf(TimeWindow(at(9), at(12))), date, zone)
        assertEquals(listOf(at(9), at(11)), rows.filter { it.kind == "FREE" }.map { it.start })
        assertEquals(120L, rows.filter { it.kind == "FREE" }.sumOf { (it.end - it.start) / 60000 })
        val skipped = agendaForDate(emptyList(), listOf(block.copy(status = "SKIPPED")), listOf(TimeWindow(at(9), at(12))), date, zone)
        assertEquals(180L, skipped.filter { it.kind == "FREE" }.sumOf { (it.end - it.start) / 60000 })
    }

    @Test fun overnightCommitmentAppearsOnBothDatesButNotAtExclusiveEnd() {
        val item = AgendaEntry("sleep", at(23), at(23) + 8 * 3600000, "Sleep", "Reserved", "LIFE")
        assertEquals(1, agendaForDate(listOf(item), emptyList(), emptyList(), date, zone).size)
        assertEquals(1, agendaForDate(listOf(item), emptyList(), emptyList(), date.plusDays(1), zone).size)
        val midnight = item.copy(end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        assertTrue(agendaForDate(listOf(midnight), emptyList(), emptyList(), date.plusDays(1), zone).isEmpty())
    }
}

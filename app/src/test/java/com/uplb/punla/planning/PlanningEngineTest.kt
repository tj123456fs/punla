package com.uplb.punla.planning

import com.uplb.punla.context.EnergyLevel
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PlanningEngineTest {
    private val now = 1_800_000_000_000L
    private fun at(minutes: Int) = now + minutes * 60000L
    private fun task(id: String, minutes: Int = 25, dueMinutes: Int? = null) =
        WorkItem(id, id, dueAt = dueMinutes?.let(::at), estimateMinutes = minutes)

    @Test fun urgentWorkRanksBeforeDistantWorkWithReasons() {
        val ranked = PriorityEngine.rank(listOf(task("later", dueMinutes = 10000), task("urgent", dueMinutes = 60)), now, 50, EnergyLevel.UNKNOWN)
        assertEquals("urgent", ranked.first().task.id)
        assertTrue(ranked.first().reasons.any { "24 hours" in it })
    }
    @Test fun manualChoiceWinsAndDismissedWorkIsExcluded() {
        val ranked = PriorityEngine.rank(listOf(task("urgent", dueMinutes = 30), task("manual").copy(pinned = true),
            task("hidden").copy(dismissedUntil = at(60))), now, 60, EnergyLevel.OKAY)
        assertEquals(listOf("manual", "urgent"), ranked.map { it.task.id })
    }
    @Test fun dismissedWorkReturnsAfterExpiry() {
        assertEquals(1, PriorityEngine.rank(listOf(task("a").copy(dismissedUntil = now)), now, null, EnergyLevel.UNKNOWN).size)
    }
    @Test fun energyPrefersSuitableEffortWithoutErasingUrgency() {
        val low = task("easy").copy(effort = 1)
        val hard = task("hard").copy(effort = 3)
        assertEquals("easy", PriorityEngine.rank(listOf(hard, low), now, null, EnergyLevel.DRAINED).first().task.id)
        assertEquals("hard", PriorityEngine.rank(listOf(hard, low), now, null, EnergyLevel.LOCKED_IN).first().task.id)
    }
    @Test fun missingMetadataHasUsableEstimateAndStableTieBreak() {
        val result = PriorityEngine.rank(listOf(WorkItem("b", "B"), WorkItem("a", "A")), now, null, EnergyLevel.UNKNOWN)
        assertEquals("a", result.first().task.id)
        assertEquals(25, result.first().task.remainingMinutes)
        assertTrue(result.first().reasons.any { "estimate" in it })
    }
    @Test fun completedTasksDoNotReappear() {
        assertTrue(PriorityEngine.rank(listOf(task("done").copy(progress = 100)), now, 100, EnergyLevel.OKAY).isEmpty())
    }
    @Test fun overlappingAndAdjacentCommitmentsAreUnioned() {
        val free = DayPlanner.freeWindows(TimeWindow(at(0), at(120)), listOf(TimeWindow(at(20), at(60)),
            TimeWindow(at(40), at(80)), TimeWindow(at(80), at(90))))
        assertEquals(listOf(TimeWindow(at(0), at(20)), TimeWindow(at(90), at(120))), free)
    }
    @Test fun commitmentSpanningBoundsConsumesWholeWindow() {
        assertTrue(DayPlanner.freeWindows(TimeWindow(at(0), at(60)), listOf(TimeWindow(at(-20), at(80)))).isEmpty())
    }
    @Test fun emptyOrReversedDayCannotGenerateTime() {
        assertTrue(DayPlanner.freeWindows(TimeWindow(at(60), at(0)), emptyList()).isEmpty())
    }
    @Test fun generatedBlocksNeverOverlapOrExceedTheDeadline() {
        val result = DayPlanner.generate(listOf(task("report", 120, 90)), listOf(TimeWindow(at(0), at(180))), EnergyLevel.OKAY)
        assertTrue(result.blocks.all { it.window.end <= at(90) })
        assertTrue(result.blocks.zipWithNext().all { (a,b) -> a.window.end <= b.window.start })
        assertEquals(35, result.unallocated["report"])
    }
    @Test fun lastFewMinutesAreNotSilentlyDiscarded() {
        val result = DayPlanner.generate(listOf(task("a", 50).copy(progress = 98)), listOf(TimeWindow(at(0), at(10))), EnergyLevel.OKAY)
        assertEquals(1, result.blocks.size)
        assertTrue(result.blocks.single().window.minutes >= 5)
    }
    @Test fun lockedWorkIsNotAllocatedTwice() {
        val result = DayPlanner.generate(listOf(task("a", 50)), listOf(TimeWindow(at(30), at(100))), EnergyLevel.OKAY, mapOf("a" to 25))
        assertEquals(25, result.blocks.sumOf { it.window.minutes })
    }
    @Test fun drainedEnergyUsesShortBlocksAndBreaks() {
        val result = DayPlanner.generate(listOf(task("a", 60)), listOf(TimeWindow(at(0), at(120))), EnergyLevel.DRAINED)
        assertEquals(listOf(25, 25, 10), result.blocks.map { it.window.minutes })
        assertTrue(result.blocks.zipWithNext().all { (a,b) -> b.window.start - a.window.end >= 300000 })
    }
    @Test fun insufficientCapacityRemainsVisible() {
        val result = DayPlanner.generate(listOf(task("a", 100)), listOf(TimeWindow(at(0), at(20))), EnergyLevel.OKAY)
        assertEquals(80, result.unallocated["a"])
    }
    @Test fun overdueWorkIsReportedAsUnallocated() {
        val result = DayPlanner.generate(listOf(task("a", 25, -1)), listOf(TimeWindow(at(0), at(60))), EnergyLevel.OKAY)
        assertTrue(result.blocks.isEmpty()); assertEquals(25, result.unallocated["a"])
    }
    @Test fun moveRejectsConflictsAndPastButAllowsTouchingBoundaries() {
        val busy = listOf(TimeWindow(at(20), at(40)))
        assertFalse(DayPlanner.canPlace(TimeWindow(at(15), at(25)), busy, now))
        assertFalse(DayPlanner.canPlace(TimeWindow(at(-10), at(0)), busy, now))
        assertTrue(DayPlanner.canPlace(TimeWindow(at(0), at(20)), busy, now))
    }
    @Test fun deadlineCapacityIsSharedAcrossCompetingTasks() {
        val loads = DayPlanner.workload(listOf(task("a", 45, 60), task("b", 45, 60)), listOf(TimeWindow(at(0), at(60))), now)
        assertFalse(loads[0].overloaded); assertTrue(loads[1].overloaded)
        assertEquals(90, loads[1].cumulativeRequired)
    }
    @Test fun parserKeepsWeekdayAmbiguityForConfirmation() {
        val draft = CaptureParser.parse("MATH 27 exercise due Friday 11:59 PM", LocalDate.of(2026, 9, 22), listOf("MATH 27"))
        assertEquals("2026-09-25", draft.date); assertEquals("23:59", draft.time)
        assertEquals("MATH 27", draft.course); assertNotNull(draft.warning)
    }
    @Test fun parserHandlesNoonAndMidnight() {
        assertEquals("12:00", CaptureParser.parse("due tomorrow 12 pm", LocalDate.of(2026, 1, 31), emptyList()).time)
        val draft = CaptureParser.parse("due tomorrow 12 am", LocalDate.of(2026, 1, 31), emptyList())
        assertEquals("00:00", draft.time); assertEquals("2026-02-01", draft.date)
    }
    @Test fun parserDoesNotInventInvalidDateOrMissingCourse() {
        val draft = CaptureParser.parse("report due 2026-02-30 at 25:90 pm", LocalDate.of(2026, 1, 1), listOf("MATH 27"))
        assertNull(draft.date); assertNull(draft.time); assertNull(draft.course)
    }
    @Test fun parserDoesNotTreatTodayAsPartOfAnotherWord() {
        assertNull(CaptureParser.parse("todayish notes", LocalDate.of(2026,1,1), emptyList()).date)
    }
    @Test fun structuredStudyUsesExactlyRequestedMinutes() {
        for (minutes in listOf(5, 10, 25, 40, 120)) {
            for (weak in listOf(0, 3)) for (mistakes in listOf(0, 4)) {
                val steps = StudyIntelligence.session(minutes, weak, mistakes)
                assertEquals(minutes, steps.sumOf { it.minutes }); assertTrue(steps.all { it.minutes > 0 })
            }
        }
    }
    @Test fun automationNeverSilentlyMarksAttendance() {
        val actions = AutomationEngine.actions(AutomationEvent("a", "CLASS_ENDED", "class"))
        assertTrue("SUGGEST_ATTENDANCE" in actions); assertFalse("MARK_ATTENDED" in actions)
        assertTrue(AutomationEngine.actions(AutomationEvent("b", "unknown", "x")).isEmpty())
    }
}

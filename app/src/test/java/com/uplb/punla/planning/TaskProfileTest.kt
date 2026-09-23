package com.uplb.punla.planning

import org.junit.Assert.*
import org.junit.Test

class TaskProfileTest {
    @Test fun firstManualOverrideKeepsTheOriginalEstimateAndImportance() {
        val task = WorkItem("study:one", "Lab report", estimateMinutes = 120, importance = 5, effort = 3)
        val profile = OsSnapshot(tasks = listOf(task)).profile(task.id)
        assertEquals(120, profile.estimatedMinutes)
        assertEquals(5, profile.importance)
        assertEquals(3, profile.effort)
    }
    @Test fun savedProfileIsNotReplacedByLegacyDefaults() {
        val saved = TaskPreferences("study:one", estimatedMinutes = 90, progress = 40, pinned = true)
        assertEquals(saved, OsSnapshot(preferences = listOf(saved)).profile(saved.id))
    }
}

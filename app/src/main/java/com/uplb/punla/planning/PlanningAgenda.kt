package com.uplb.punla.planning

import java.time.LocalDate
import java.time.ZoneId

/** Read-only agenda; persistence and scheduling policy remain unchanged. */
data class AgendaEntry(val id: String, val start: Long, val end: Long, val title: String,
    val detail: String, val kind: String, val route: String = "", val blockId: String? = null)

fun agendaForDate(entries: List<AgendaEntry>, blocks: List<DayPlanBlock>, windows: List<TimeWindow>,
    date: LocalDate, zone: ZoneId): List<AgendaEntry> {
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val scheduled = blocks.map { block -> AgendaEntry("block:${block.id}", block.startAt, block.endAt, block.title,
        when { block.status != "PLANNED" -> block.status.lowercase().replaceFirstChar { it.uppercase() }
            block.locked -> "Locked study block"; else -> "Flexible study block" }, "STUDY", blockId = block.id) }
    val free = windows.flatMap { DayPlanner.freeWindows(it, blocks.filter { b -> b.status == "PLANNED" }.map { b -> b.window() }) }
        .filter { it.minutes >= 5 }.map { AgendaEntry("free:${it.start}", it.start, it.end, "Open time", "Available within planning hours", "FREE") }
    return (entries + scheduled + free).filter { it.start < end && it.end > start && it.end > it.start }
        .sortedWith(compareBy<AgendaEntry> { it.start }.thenBy { it.id })
}

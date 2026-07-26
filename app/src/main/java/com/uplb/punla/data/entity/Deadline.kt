package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "deadline_rules")
data class DeadlineRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val course: String? = null,
    val type: String,
    val priority: String, // "Low" | "Medium" | "High"
    val startDate: String,
    val repeat: String = "weekly"
)

@Entity(tableName = "deadlines")
data class Deadline(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val course: String? = null,
    val due: String, // ISO yyyy-MM-dd
    val type: String,
    val priority: String, // "Low" | "Medium" | "High"
    val done: Boolean = false,
    val ruleId: String? = null,
    val isRecurring: Boolean = false
)

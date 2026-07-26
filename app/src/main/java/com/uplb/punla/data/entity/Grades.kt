package com.uplb.punla.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "semesters")
data class Semester(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val label: String
)

@Entity(
    tableName = "grade_courses",
    foreignKeys = [
        ForeignKey(
            entity = Semester::class,
            parentColumns = ["id"],
            childColumns = ["semesterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("semesterId")]
)
data class GradeCourse(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val semesterId: String,
    val code: String,
    val title: String? = null,
    val units: Double = 0.0,
    val grade: String = "" // UPLB numeric-style grade string, e.g. "1.00", "2.50", "5.00", "INC"
)

/** Archived schedule + deadlines from a past semester (Start New Semester action). */
@Entity(tableName = "archives")
data class Archive(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val createdAt: Long,
    val label: String,
    val scheduleJson: String, // serialized List<ClassSession>
    val deadlinesJson: String // serialized List<Deadline>
)

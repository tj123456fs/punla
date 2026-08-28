package com.uplb.punla.data.dao

import androidx.room.*
import com.uplb.punla.data.entity.Archive
import com.uplb.punla.data.entity.GradeCourse
import com.uplb.punla.data.entity.Semester
import kotlinx.coroutines.flow.Flow

@Dao
interface GradesDao {
    @Query("SELECT * FROM semesters")
    fun observeSemesters(): Flow<List<Semester>>

    @Query("SELECT * FROM semesters")
    suspend fun getAllSemesters(): List<Semester>

    @Upsert
    suspend fun upsertSemester(semester: Semester)

    @Delete
    suspend fun deleteSemester(semester: Semester)

    @Query("DELETE FROM semesters")
    suspend fun clearSemesters()

    @Query("SELECT * FROM grade_courses WHERE semesterId = :semesterId")
    fun observeCourses(semesterId: String): Flow<List<GradeCourse>>

    @Query("SELECT * FROM grade_courses")
    suspend fun getAllCourses(): List<GradeCourse>

    // Reactive counterpart of getAllCourses(), spanning every semester —
    // backs the cumulative GWA card so it updates live as courses/grades
    // change in any semester, not just the one currently selected.
    @Query("SELECT * FROM grade_courses")
    fun observeAllCourses(): Flow<List<GradeCourse>>

    @Upsert
    suspend fun upsertCourse(course: GradeCourse)

    @Delete
    suspend fun deleteCourse(course: GradeCourse)

    @Query("DELETE FROM grade_courses")
    suspend fun clearCourses()

    @Upsert
    suspend fun insertArchive(archive: Archive)

    @Query("SELECT * FROM archives ORDER BY createdAt DESC")
    fun observeArchives(): Flow<List<Archive>>

    @Query("SELECT * FROM archives ORDER BY createdAt DESC")
    suspend fun getAllArchives(): List<Archive>

    @Query("DELETE FROM archives")
    suspend fun clearArchives()
}

package com.uplb.punla.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.uplb.punla.data.entity.Archive
import com.uplb.punla.data.entity.AttendanceRecord
import com.uplb.punla.data.entity.ClassSession
import com.uplb.punla.data.entity.Deadline
import com.uplb.punla.data.entity.DeadlineRule
import com.uplb.punla.data.entity.Expense
import com.uplb.punla.data.entity.ExpenseRule
import com.uplb.punla.data.entity.GradeCourse
import com.uplb.punla.data.entity.Flashcard
import com.uplb.punla.data.entity.FlashcardDeck
import com.uplb.punla.data.entity.Quiz
import com.uplb.punla.data.entity.QuizQuestion
import com.uplb.punla.data.entity.QuizAttempt
import com.uplb.punla.data.entity.JsonImportRecord
import com.uplb.punla.data.entity.StudyTopic
import com.uplb.punla.data.entity.StudyNote
import com.uplb.punla.data.entity.FormulaReference
import com.uplb.punla.data.entity.MistakeRecord
import com.uplb.punla.data.entity.StudyGoal
import com.uplb.punla.data.entity.StudyPlanItem
import com.uplb.punla.data.entity.QuizAnswerResult
import com.uplb.punla.data.entity.FlashcardReviewEvent
import com.uplb.punla.data.entity.QuestionBankItem
import com.uplb.punla.data.entity.Semester
import com.uplb.punla.data.entity.StudySession
import com.uplb.punla.data.entity.StudySuggestionEvent
import com.uplb.punla.data.entity.NotificationEvent
import com.uplb.punla.ml.StudySlotModelState
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mirrors the web app's exportData()/importData() (index.html ~line 2075):
 * serializes every piece of local state into one JSON document and can
 * restore it later, in one transaction, on this or another device.
 *
 * Kept as plain org.json (no kotlinx.serialization dependency) to match the
 * style already used in PunlaViewModel.startNewSemester.
 */
object BackupManager {

    const val CURRENT_VERSION = 7
    private const val MAX_BACKUP_FILE_CHARS = 20_000_000

    /** Suggested filename, mirrors the web app's punla-backup-YYYY-MM-DD.json. */
    fun suggestedFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "punla-backup-$date.json"
    }

    class InvalidBackupException(message: String) : Exception(message)

    // ---- Export ----

    suspend fun buildBackupJson(context: Context): String {
        val db = PunlaDatabase.get(context)
        val repo = PunlaRepository(context)

        val schedule = db.classSessionDao().getAll()
        val expenses = db.expenseDao().getAll()
        val expenseRules = db.expenseDao().getAllRules()
        val deadlines = db.deadlineDao().getAll()
        val deadlineRules = db.deadlineDao().getAllRules()
        val semesters = db.gradesDao().getAllSemesters()
        val allCourses = db.gradesDao().getAllCourses()
        val archives = db.gradesDao().getAllArchives()
        val studySessions = db.studySessionDao().getAll()
        val studySuggestionEvents = db.intelligenceDao().getStudySuggestionEvents()
        val notificationEvents = db.intelligenceDao().getNotificationEvents()
        val attendanceRecords = db.attendanceDao().getAll()
        val flashcardDecks = db.flashcardDao().getDecks()
        val flashcards = db.flashcardDao().getAllCards()
        val quizzes = db.quizDao().getQuizzes()
        val quizQuestions = db.quizDao().getAllQuestions()
        val quizAttempts = db.quizDao().getAllAttempts()
        val jsonImportRecords = db.jsonImportDao().getAll()
        val studyTopics = db.studyMaterialDao().getTopics()
        val studyNotes = db.studyMaterialDao().getNotes()
        val formulaReferences = db.studyMaterialDao().getFormulas()
        val mistakeRecords = db.studyMaterialDao().getMistakes()
        val studyGoals = db.studyMaterialDao().getGoals()
        val studyPlanItems = db.studyMaterialDao().getPlanItems()
        val flashcardReviewEvents = db.studyMaterialDao().getFlashcardReviewEvents()
        val quizAnswerResults = db.studyMaterialDao().getAnswerResults()
        val questionBank = db.studyMaterialDao().getQuestionBank()

        val root = JSONObject().apply {
            put("punlaFileId", PunlaJsonFileIds.BACKUP)
            put("schemaVersion", 1)
            put("contentId", UUID.randomUUID().toString())
            put("version", CURRENT_VERSION)
            put("exportedAt", java.time.Instant.now().toString())
            put("schedule", JSONArray(schedule.map(::classSessionToJson)))
            put("expenses", JSONArray(expenses.map(::expenseToJson)))
            put("expenseRules", JSONArray(expenseRules.map(::expenseRuleToJson)))
            put("deadlines", JSONArray(deadlines.map(::deadlineToJson)))
            put("deadlineRules", JSONArray(deadlineRules.map(::deadlineRuleToJson)))
            put("gradesSemesters", JSONArray(semesters.map { sem ->
                val courses = allCourses.filter { it.semesterId == sem.id }
                JSONObject().apply {
                    put("semester", semesterToJson(sem))
                    put("courses", JSONArray(courses.map(::gradeCourseToJson)))
                }
            }))
            put("archives", JSONArray(archives.map(::archiveToJson)))
            put("studySessions", JSONArray(studySessions.map(::studySessionToJson)))
            put("studySuggestionEvents", JSONArray(studySuggestionEvents.map(::studySuggestionEventToJson)))
            put("notificationEvents", JSONArray(notificationEvents.map(::notificationEventToJson)))
            put("attendanceRecords", JSONArray(attendanceRecords.map(::attendanceRecordToJson)))
            put("flashcardDecks", JSONArray(flashcardDecks.map(::flashcardDeckToJson)))
            put("flashcards", JSONArray(flashcards.map(::flashcardToJson)))
            put("quizzes", JSONArray(quizzes.map(::quizToJson)))
            put("quizQuestions", JSONArray(quizQuestions.map(::quizQuestionToJson)))
            put("quizAttempts", JSONArray(quizAttempts.map(::quizAttemptToJson)))
            put("jsonImportRecords", JSONArray(jsonImportRecords.map(::jsonImportRecordToJson)))
            put("studyTopics", JSONArray(studyTopics.map(::studyTopicToJson)))
            put("studyNotes", JSONArray(studyNotes.map(::studyNoteToJson)))
            put("formulaReferences", JSONArray(formulaReferences.map(::formulaReferenceToJson)))
            put("mistakeRecords", JSONArray(mistakeRecords.map(::mistakeRecordToJson)))
            put("studyGoals", JSONArray(studyGoals.map(::studyGoalToJson)))
            put("studyPlanItems", JSONArray(studyPlanItems.map(::studyPlanItemToJson)))
            put("flashcardReviewEvents", JSONArray(flashcardReviewEvents.map(::flashcardReviewEventToJson)))
            put("quizAnswerResults", JSONArray(quizAnswerResults.map(::quizAnswerResultToJson)))
            put("questionBank", JSONArray(questionBank.map(::questionBankItemToJson)))
            put("budget", repo.monthlyBudget)
            put("userName", repo.userName)
            put("chedTarget", repo.chedTarget)
            put("theme", repo.themeMode.name.lowercase())
            put("notificationsEnabled", repo.notificationsEnabled)
            put("classDayNotificationEnabled", repo.classDayNotificationEnabled)
            put("morningAgendaEnabled", repo.morningAgendaEnabled)
            put("quietHoursEnabled", repo.quietHoursEnabled)
            put("studyRemindersEnabled", repo.studyRemindersEnabled)
            put("dailyStudyGoalMinutes", repo.dailyStudyGoalMinutes)
            put("weeklyStudyGoalMinutes", repo.weeklyStudyGoalMinutes)
            put("budgetPeriod", repo.budgetPeriod.name.lowercase())
            put("weekStartDay", repo.weekStartDay.name)
            put("weeklyBudgetOverride", repo.weeklyBudgetOverride)
            put("weeklyRolloverEnabled", repo.weeklyRolloverEnabled)
            put("categoryBudgetLimits", JSONObject().apply {
                repo.categoryBudgetLimits.forEach { (category, amount) -> put(category, amount) }
            })
            put("termStartDate", repo.termStartDate.toString())
            put("termEndDate", repo.termEndDate.toString())
            put("cloudAssistantEnabled", repo.cloudAssistantEnabled)
            put("assistantModel", repo.assistantModel)
            put("preferredReminderHour", repo.preferredReminderHour)
            put("dismissedExpensePatternKeys", JSONArray(repo.dismissedExpensePatternKeys.toList()))
            put("studySlotModel", studySlotModelToJson(repo.studySlotModelState))
        }
        return root.toString(2)
    }

    suspend fun exportTo(context: Context, uri: Uri) {
        val json = buildBackupJson(context)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        } ?: throw InvalidBackupException("Couldn't open the selected file for writing.")
    }

    // ---- Validation ----

    /** Port of the web app's isValidBackupShape(): checks the top-level
     * shape is sane before anything touches the database. */
    private fun isValidBackupShape(root: JSONObject): Boolean {
        val declaredFileId = root.optString("punlaFileId").trim()
        if (declaredFileId.isNotEmpty() && declaredFileId != PunlaJsonFileIds.BACKUP) return false
        if (!root.has("version")) return false
        val version = root.optInt("version", -1)
        if (version !in 1..CURRENT_VERSION) return false
        val requiredArrays = listOf("schedule", "expenses", "deadlines", "gradesSemesters")
        for (key in requiredArrays) {
            if (root.opt(key) !is JSONArray) return false
        }
        return true
    }

    // ---- Import ----

    suspend fun importFrom(context: Context, uri: Uri) {
        val root = withContext(Dispatchers.IO) {
            val text = runCatching {
                PunlaJsonImportReader.readText(context, uri, MAX_BACKUP_FILE_CHARS)
            }.getOrElse { error ->
                throw InvalidBackupException(error.message ?: "Couldn't read the selected file.")
            }
            runCatching { JSONObject(text) }.getOrElse {
                throw InvalidBackupException("That file isn't valid JSON.")
            }
        }

        val declaredFileId = root.optString("punlaFileId").trim()
        if (declaredFileId.isNotEmpty() && declaredFileId != PunlaJsonFileIds.BACKUP) {
            throw InvalidBackupException(
                "That is a Punla ${PunlaJsonFileIds.label(declaredFileId)} JSON, not a backup. Open it from the matching Punla screen instead."
            )
        }
        val backupVersion = root.optInt("version", -1)
        if (backupVersion > CURRENT_VERSION) {
            throw InvalidBackupException("This backup was created by a newer Punla version. Update Punla before restoring it.")
        }
        if (!isValidBackupShape(root)) {
            throw InvalidBackupException("That file doesn't look like a compatible Punla backup.")
        }

        val db = PunlaDatabase.get(context)
        val repo = PunlaRepository(context)

        val schedule = root.getJSONArray("schedule").mapObjects(::classSessionFromJson)
        val expenses = root.getJSONArray("expenses").mapObjects(::expenseFromJson)
        val expenseRules = root.optJSONArray("expenseRules")?.mapObjects(::expenseRuleFromJson) ?: emptyList()
        val deadlines = root.getJSONArray("deadlines").mapObjects(::deadlineFromJson)
        val deadlineRules = root.optJSONArray("deadlineRules")?.mapObjects(::deadlineRuleFromJson) ?: emptyList()

        val gradesSemesters = root.getJSONArray("gradesSemesters")
        val semesters = mutableListOf<Semester>()
        val courses = mutableListOf<GradeCourse>()
        for (i in 0 until gradesSemesters.length()) {
            val entry = gradesSemesters.getJSONObject(i)
            val sem = semesterFromJson(entry.getJSONObject("semester"))
            semesters += sem
            entry.optJSONArray("courses")?.let { arr ->
                courses += arr.mapObjects(::gradeCourseFromJson)
            }
        }

        val archives = root.optJSONArray("archives")?.mapObjects(::archiveFromJson) ?: emptyList()
        val studySessions = root.optJSONArray("studySessions")?.mapObjects(::studySessionFromJson) ?: emptyList()
        val studySuggestionEvents = root.optJSONArray("studySuggestionEvents")?.mapObjects(::studySuggestionEventFromJson) ?: emptyList()
        val notificationEvents = root.optJSONArray("notificationEvents")?.mapObjects(::notificationEventFromJson) ?: emptyList()
        val attendanceRecords = root.optJSONArray("attendanceRecords")?.mapObjects(::attendanceRecordFromJson) ?: emptyList()
        val flashcardDecks = root.optJSONArray("flashcardDecks")?.mapObjects(::flashcardDeckFromJson) ?: emptyList()
        val flashcards = root.optJSONArray("flashcards")?.mapObjects(::flashcardFromJson) ?: emptyList()
        val quizzes = root.optJSONArray("quizzes")?.mapObjects(::quizFromJson) ?: emptyList()
        val quizQuestions = root.optJSONArray("quizQuestions")?.mapObjects(::quizQuestionFromJson) ?: emptyList()
        val quizAttempts = root.optJSONArray("quizAttempts")?.mapObjects(::quizAttemptFromJson) ?: emptyList()
        val jsonImportRecords = root.optJSONArray("jsonImportRecords")?.mapObjects(::jsonImportRecordFromJson) ?: emptyList()
        val studyTopics = root.optJSONArray("studyTopics")?.mapObjects(::studyTopicFromJson) ?: emptyList()
        val studyNotes = root.optJSONArray("studyNotes")?.mapObjects(::studyNoteFromJson) ?: emptyList()
        val formulaReferences = root.optJSONArray("formulaReferences")?.mapObjects(::formulaReferenceFromJson) ?: emptyList()
        val mistakeRecords = root.optJSONArray("mistakeRecords")?.mapObjects(::mistakeRecordFromJson) ?: emptyList()
        val studyGoals = root.optJSONArray("studyGoals")?.mapObjects(::studyGoalFromJson) ?: emptyList()
        val studyPlanItems = root.optJSONArray("studyPlanItems")?.mapObjects(::studyPlanItemFromJson) ?: emptyList()
        val flashcardReviewEvents = root.optJSONArray("flashcardReviewEvents")?.mapObjects(::flashcardReviewEventFromJson) ?: emptyList()
        val quizAnswerResults = root.optJSONArray("quizAnswerResults")?.mapObjects(::quizAnswerResultFromJson) ?: emptyList()
        val questionBank = root.optJSONArray("questionBank")?.mapObjects(::questionBankItemFromJson) ?: emptyList()

        db.withTransaction {
            // Study System 3.0 tables are cleared first so foreign-key children never
            // reference quiz/question rows that are about to be replaced.
            db.studyMaterialDao().clearAnswerResults()
            db.studyMaterialDao().clearFlashcardReviewEvents()
            db.studyMaterialDao().clearQuestionBank()
            db.studyMaterialDao().clearMistakes()
            db.studyMaterialDao().clearGoals()
            db.studyMaterialDao().clearPlanItems()
            db.studyMaterialDao().clearNotes()
            db.studyMaterialDao().clearFormulas()
            db.studyMaterialDao().clearTopics()
            db.classSessionDao().clearAll()
            schedule.forEach { db.classSessionDao().upsert(it) }

            db.expenseDao().clearAllRules()
            db.expenseDao().clearAll()
            expenseRules.forEach { db.expenseDao().upsertRule(it) }
            expenses.forEach { db.expenseDao().upsert(it) }

            db.deadlineDao().clearAllRules()
            db.deadlineDao().clearAll()
            deadlineRules.forEach { db.deadlineDao().upsertRule(it) }
            deadlines.forEach { db.deadlineDao().upsert(it) }

            db.gradesDao().clearCourses()
            db.gradesDao().clearSemesters()
            semesters.forEach { db.gradesDao().upsertSemester(it) }
            courses.forEach { db.gradesDao().upsertCourse(it) }

            db.gradesDao().clearArchives()
            archives.forEach { db.gradesDao().insertArchive(it) }

            db.studySessionDao().clearAll()
            studySessions.forEach { db.studySessionDao().upsert(it) }

            db.intelligenceDao().clearStudySuggestionEvents()
            studySuggestionEvents.forEach { db.intelligenceDao().insertStudySuggestionEvent(it) }
            db.intelligenceDao().clearNotificationEvents()
            notificationEvents.forEach { db.intelligenceDao().insertNotificationEvent(it) }

            db.attendanceDao().clearAll()
            attendanceRecords.forEach { db.attendanceDao().upsert(it) }

            db.flashcardDao().clearAll()
            db.flashcardDao().upsertDecks(flashcardDecks)
            db.flashcardDao().upsertCards(flashcards.filter { card -> flashcardDecks.any { it.id == card.deckId } })

            db.quizDao().clearAll()
            db.quizDao().upsertQuizzes(quizzes)
            db.quizDao().upsertQuestions(quizQuestions.filter { q -> quizzes.any { it.id == q.quizId } })
            val restoredAttempts = quizAttempts.filter { a -> quizzes.any { it.id == a.quizId } }
            db.quizDao().insertAttempts(restoredAttempts)

            db.studyMaterialDao().upsertTopics(studyTopics)
            db.studyMaterialDao().upsertNotes(studyNotes.filter { n -> n.topicId == null || studyTopics.any { it.id == n.topicId } })
            db.studyMaterialDao().upsertFormulas(formulaReferences.filter { f -> f.topicId == null || studyTopics.any { it.id == f.topicId } })
            db.studyMaterialDao().upsertMistakes(mistakeRecords)
            db.studyMaterialDao().upsertGoals(studyGoals)
            db.studyMaterialDao().upsertPlanItems(studyPlanItems)
            db.studyMaterialDao().upsertBankItems(questionBank)
            db.studyMaterialDao().upsertFlashcardReviewEvents(flashcardReviewEvents)
            val questionIds = quizQuestions.mapTo(hashSetOf()) { it.id }
            val attemptIds = restoredAttempts.mapTo(hashSetOf()) { it.id }
            db.studyMaterialDao().upsertAnswerResults(
                quizAnswerResults.filter { it.questionId in questionIds && it.attemptId in attemptIds }
            )

            db.jsonImportDao().clearAll()
            db.jsonImportDao().upsertAll(jsonImportRecords)
        }

        // Prefs live outside Room, so they're written after the DB transaction commits.
        repo.monthlyBudget = root.optDouble("budget", 0.0)
        repo.userName = root.optString("userName", "")
        repo.chedTarget = if (!root.has("chedTarget") || root.isNull("chedTarget")) null else root.optDouble("chedTarget")
        repo.themeMode = when (root.optString("theme", "system")) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
        repo.notificationsEnabled = root.optBoolean("notificationsEnabled", true)
        repo.classDayNotificationEnabled = root.optBoolean("classDayNotificationEnabled", true)
        repo.morningAgendaEnabled = root.optBoolean("morningAgendaEnabled", true)
        repo.quietHoursEnabled = root.optBoolean("quietHoursEnabled", true)
        repo.studyRemindersEnabled = root.optBoolean("studyRemindersEnabled", true)
        repo.dailyStudyGoalMinutes = root.optInt("dailyStudyGoalMinutes", repo.dailyStudyGoalMinutes)
        repo.weeklyStudyGoalMinutes = root.optInt("weeklyStudyGoalMinutes", repo.weeklyStudyGoalMinutes)
        repo.budgetPeriod = when (root.optString("budgetPeriod", "monthly")) {
            "weekly" -> BudgetPeriod.WEEKLY
            "both" -> BudgetPeriod.BOTH
            else -> BudgetPeriod.MONTHLY
        }
        repo.weekStartDay = runCatching {
            java.time.DayOfWeek.valueOf(root.optString("weekStartDay", "MONDAY"))
        }.getOrDefault(java.time.DayOfWeek.MONDAY)
        repo.weeklyBudgetOverride = if (!root.has("weeklyBudgetOverride") || root.isNull("weeklyBudgetOverride")) null else root.optDouble("weeklyBudgetOverride")
        repo.weeklyRolloverEnabled = root.optBoolean("weeklyRolloverEnabled", false)
        repo.categoryBudgetLimits = root.optJSONObject("categoryBudgetLimits")?.let { obj ->
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.optDouble(key, 0.0)
                    if (key.isNotBlank() && value > 0.0 && value.isFinite()) put(key, value)
                }
            }
        } ?: emptyMap()
        root.optStringOrNull("termStartDate")?.let { raw ->
            runCatching { java.time.LocalDate.parse(raw) }.getOrNull()?.let { repo.termStartDate = it }
        }
        root.optStringOrNull("termEndDate")?.let { raw ->
            runCatching { java.time.LocalDate.parse(raw) }.getOrNull()?.let { repo.termEndDate = it }
        }
        repo.cloudAssistantEnabled = root.optBoolean("cloudAssistantEnabled", false)
        repo.assistantModel = root.optString("assistantModel", repo.assistantModel)
        repo.preferredReminderHour = if (!root.has("preferredReminderHour") || root.isNull("preferredReminderHour")) null else root.optInt("preferredReminderHour")
        repo.dismissedExpensePatternKeys = root.optJSONArray("dismissedExpensePatternKeys")?.toStringSet() ?: emptySet()
        root.optJSONObject("studySlotModel")?.let { repo.studySlotModelState = studySlotModelFromJson(it) }
    }
}

// ---- Small JSON helpers ----

private fun JSONArray.toStringSet(): Set<String> = buildSet {
    for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let(::add)
}

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
    val out = ArrayList<T>(length())
    for (i in 0 until length()) out.add(transform(getJSONObject(i)))
    return out
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

// ---- Per-entity (de)serializers ----

private fun classSessionToJson(c: ClassSession) = JSONObject().apply {
    put("id", c.id); put("code", c.code); put("section", c.section)
    put("title", c.title); put("day", c.day); put("type", c.type)
    put("start", c.start); put("end", c.end); put("room", c.room); put("instructor", c.instructor)
    put("absences", c.absences)
}

private fun classSessionFromJson(o: JSONObject) = ClassSession(
    id = o.getString("id"),
    code = o.getString("code"),
    section = o.optStringOrNull("section"),
    title = o.optStringOrNull("title"),
    day = o.getString("day"),
    type = o.getString("type"),
    start = o.getString("start"),
    end = o.getString("end"),
    room = o.optStringOrNull("room"),
    instructor = o.optStringOrNull("instructor"),
    // Older backups (pre-roadmap #4) won't have this key — default to 0.
    absences = o.optInt("absences", 0)
)

private fun attendanceRecordToJson(a: AttendanceRecord) = JSONObject().apply {
    put("occurrenceKey", a.occurrenceKey); put("sessionId", a.sessionId); put("classCode", a.classCode)
    put("occurrenceDate", a.occurrenceDate); put("scheduledStart", a.scheduledStart)
    put("status", a.status); put("loggedAt", a.loggedAt); put("source", a.source)
}

private fun attendanceRecordFromJson(o: JSONObject) = AttendanceRecord(
    occurrenceKey = o.getString("occurrenceKey"),
    sessionId = o.getString("sessionId"),
    classCode = o.optString("classCode", "Class"),
    occurrenceDate = o.getString("occurrenceDate"),
    scheduledStart = o.optString("scheduledStart", "00:00"),
    status = o.getString("status"),
    loggedAt = o.optLong("loggedAt", System.currentTimeMillis()),
    source = o.optString("source", "restore")
)

private fun flashcardDeckToJson(d: FlashcardDeck) = JSONObject().apply {
    put("id", d.id); put("name", d.name); put("courseCode", d.courseCode); put("description", d.description)
    put("createdAt", d.createdAt); put("updatedAt", d.updatedAt)
}

private fun flashcardDeckFromJson(o: JSONObject) = FlashcardDeck(
    id = o.getString("id"), name = o.getString("name"),
    courseCode = o.optStringOrNull("courseCode"), description = o.optStringOrNull("description"),
    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
)

private fun flashcardToJson(c: Flashcard) = JSONObject().apply {
    put("id", c.id); put("deckId", c.deckId); put("front", c.front); put("back", c.back); put("hint", c.hint)
    put("createdAt", c.createdAt); put("updatedAt", c.updatedAt); put("dueAt", c.dueAt); put("lastReviewedAt", c.lastReviewedAt)
    put("reviewCount", c.reviewCount); put("correctCount", c.correctCount); put("mastery", c.mastery)
    put("tags", c.tags); put("starred", c.starred); put("reverseEnabled", c.reverseEnabled); put("cardType", c.cardType)
    put("imageUri", c.imageUri); put("occlusionJson", c.occlusionJson)
}

private fun flashcardFromJson(o: JSONObject) = Flashcard(
    id = o.getString("id"), deckId = o.getString("deckId"), front = o.getString("front"), back = o.getString("back"),
    hint = o.optStringOrNull("hint"), createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()), dueAt = o.optLong("dueAt", 0L),
    lastReviewedAt = if (!o.has("lastReviewedAt") || o.isNull("lastReviewedAt")) null else o.optLong("lastReviewedAt"),
    reviewCount = o.optInt("reviewCount", 0), correctCount = o.optInt("correctCount", 0), mastery = o.optInt("mastery", 0),
    tags = o.optString("tags", ""), starred = o.optBoolean("starred", false),
    reverseEnabled = o.optBoolean("reverseEnabled", false), cardType = o.optString("cardType", "BASIC"),
    imageUri = o.optStringOrNull("imageUri"), occlusionJson = o.optString("occlusionJson", "[]")
)

private fun quizToJson(q: Quiz) = JSONObject().apply {
    put("id", q.id); put("title", q.title); put("courseCode", q.courseCode); put("description", q.description)
    put("passingScore", q.passingScore); put("shuffleQuestions", q.shuffleQuestions); put("shuffleChoices", q.shuffleChoices)
    put("timeLimitMinutes", q.timeLimitMinutes); put("feedbackMode", q.feedbackMode)
    put("createdAt", q.createdAt); put("updatedAt", q.updatedAt)
}

private fun quizFromJson(o: JSONObject) = Quiz(
    id = o.getString("id"), title = o.getString("title"), courseCode = o.optStringOrNull("courseCode"),
    description = o.optStringOrNull("description"), passingScore = o.optInt("passingScore", 70).coerceIn(1, 100),
    shuffleQuestions = o.optBoolean("shuffleQuestions", true), shuffleChoices = o.optBoolean("shuffleChoices", true),
    timeLimitMinutes = if (!o.has("timeLimitMinutes") || o.isNull("timeLimitMinutes")) null else o.optInt("timeLimitMinutes").takeIf { it > 0 },
    feedbackMode = o.optString("feedbackMode", "IMMEDIATE"),
    createdAt = o.optLong("createdAt", System.currentTimeMillis()), updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
)

private fun quizQuestionToJson(q: QuizQuestion) = JSONObject().apply {
    put("id", q.id); put("quizId", q.quizId); put("type", q.type); put("prompt", q.prompt)
    put("optionsJson", q.optionsJson); put("correctAnswer", q.correctAnswer); put("explanation", q.explanation); put("tags", q.tags)
    put("metadataJson", q.metadataJson); put("imageUri", q.imageUri)
    put("createdAt", q.createdAt); put("updatedAt", q.updatedAt)
}

private fun quizQuestionFromJson(o: JSONObject) = QuizQuestion(
    id = o.getString("id"), quizId = o.getString("quizId"), type = o.optString("type", "MULTIPLE_CHOICE"),
    prompt = o.getString("prompt"), optionsJson = o.optString("optionsJson", "[]"), correctAnswer = o.getString("correctAnswer"),
    explanation = o.optStringOrNull("explanation"), tags = o.optString("tags", ""),
    metadataJson = o.optString("metadataJson", "{}"), imageUri = o.optStringOrNull("imageUri"),
    createdAt = o.optLong("createdAt", System.currentTimeMillis()), updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
)

private fun quizAttemptToJson(a: QuizAttempt) = JSONObject().apply {
    put("id", a.id); put("quizId", a.quizId); put("startedAt", a.startedAt); put("completedAt", a.completedAt)
    put("score", a.score); put("total", a.total); put("durationMs", a.durationMs); put("incorrectQuestionIdsJson", a.incorrectQuestionIdsJson)
}

private fun quizAttemptFromJson(o: JSONObject) = QuizAttempt(
    id = o.getString("id"), quizId = o.getString("quizId"), startedAt = o.getLong("startedAt"),
    completedAt = o.getLong("completedAt"), score = o.optInt("score", 0), total = o.optInt("total", 0),
    durationMs = o.optLong("durationMs", 0L), incorrectQuestionIdsJson = o.optString("incorrectQuestionIdsJson", "[]")
)

private fun jsonImportRecordToJson(r: JsonImportRecord) = JSONObject().apply {
    put("fileType", r.fileType); put("contentId", r.contentId); put("importedAt", r.importedAt); put("destinationId", r.destinationId)
}

private fun jsonImportRecordFromJson(o: JSONObject) = JsonImportRecord(
    fileType = o.getString("fileType"), contentId = o.getString("contentId"),
    importedAt = o.optLong("importedAt", System.currentTimeMillis()), destinationId = o.optStringOrNull("destinationId")
)

private fun studyTopicToJson(x: StudyTopic) = JSONObject().apply {
    put("id", x.id); put("courseCode", x.courseCode); put("name", x.name); put("parentTopicId", x.parentTopicId)
    put("examDate", x.examDate); put("priority", x.priority); put("createdAt", x.createdAt); put("updatedAt", x.updatedAt)
}
private fun studyTopicFromJson(o: JSONObject) = StudyTopic(
    id=o.getString("id"), courseCode=o.getString("courseCode"), name=o.getString("name"), parentTopicId=o.optStringOrNull("parentTopicId"),
    examDate=o.optStringOrNull("examDate"), priority=o.optInt("priority",3).coerceIn(1,5), createdAt=o.optLong("createdAt",System.currentTimeMillis()), updatedAt=o.optLong("updatedAt",System.currentTimeMillis())
)
private fun studyNoteToJson(x: StudyNote)=JSONObject().apply{put("id",x.id);put("courseCode",x.courseCode);put("topicId",x.topicId);put("title",x.title);put("body",x.body);put("tags",x.tags);put("createdAt",x.createdAt);put("updatedAt",x.updatedAt)}
private fun studyNoteFromJson(o:JSONObject)=StudyNote(id=o.getString("id"),courseCode=o.optStringOrNull("courseCode"),topicId=o.optStringOrNull("topicId"),title=o.getString("title"),body=o.getString("body"),tags=o.optString("tags",""),createdAt=o.optLong("createdAt",System.currentTimeMillis()),updatedAt=o.optLong("updatedAt",System.currentTimeMillis()))
private fun formulaReferenceToJson(x:FormulaReference)=JSONObject().apply{put("id",x.id);put("courseCode",x.courseCode);put("topicId",x.topicId);put("title",x.title);put("expression",x.expression);put("variables",x.variables);put("units",x.units);put("workedExample",x.workedExample);put("createdAt",x.createdAt);put("updatedAt",x.updatedAt)}
private fun formulaReferenceFromJson(o:JSONObject)=FormulaReference(id=o.getString("id"),courseCode=o.optStringOrNull("courseCode"),topicId=o.optStringOrNull("topicId"),title=o.getString("title"),expression=o.getString("expression"),variables=o.optStringOrNull("variables"),units=o.optStringOrNull("units"),workedExample=o.optStringOrNull("workedExample"),createdAt=o.optLong("createdAt",System.currentTimeMillis()),updatedAt=o.optLong("updatedAt",System.currentTimeMillis()))
private fun mistakeRecordToJson(x:MistakeRecord)=JSONObject().apply{put("id",x.id);put("sourceType",x.sourceType);put("sourceId",x.sourceId);put("courseCode",x.courseCode);put("topicTag",x.topicTag);put("prompt",x.prompt);put("userAnswer",x.userAnswer);put("correctAnswer",x.correctAnswer);put("explanation",x.explanation);put("confidence",x.confidence);put("missedAt",x.missedAt);put("retryAt",x.retryAt);put("resolved",x.resolved);put("timesMissed",x.timesMissed)}
private fun mistakeRecordFromJson(o:JSONObject)=MistakeRecord(id=o.getString("id"),sourceType=o.getString("sourceType"),sourceId=o.getString("sourceId"),courseCode=o.optStringOrNull("courseCode"),topicTag=o.optStringOrNull("topicTag"),prompt=o.getString("prompt"),userAnswer=o.optStringOrNull("userAnswer"),correctAnswer=o.getString("correctAnswer"),explanation=o.optStringOrNull("explanation"),confidence=o.optString("confidence","UNSET"),missedAt=o.optLong("missedAt",System.currentTimeMillis()),retryAt=o.optLong("retryAt",System.currentTimeMillis()),resolved=o.optBoolean("resolved",false),timesMissed=o.optInt("timesMissed",1).coerceAtLeast(1))
private fun studyGoalToJson(x:StudyGoal)=JSONObject().apply{put("id",x.id);put("courseCode",x.courseCode);put("topicTag",x.topicTag);put("title",x.title);put("goalType",x.goalType);put("targetValue",x.targetValue);put("progressValue",x.progressValue);put("dueDate",x.dueDate);put("completed",x.completed);put("createdAt",x.createdAt);put("updatedAt",x.updatedAt)}
private fun studyGoalFromJson(o:JSONObject)=StudyGoal(id=o.getString("id"),courseCode=o.optStringOrNull("courseCode"),topicTag=o.optStringOrNull("topicTag"),title=o.getString("title"),goalType=o.optString("goalType","CUSTOM"),targetValue=o.optInt("targetValue",1).coerceAtLeast(1),progressValue=o.optInt("progressValue",0).coerceAtLeast(0),dueDate=o.optStringOrNull("dueDate"),completed=o.optBoolean("completed",false),createdAt=o.optLong("createdAt",System.currentTimeMillis()),updatedAt=o.optLong("updatedAt",System.currentTimeMillis()))
private fun studyPlanItemToJson(x:StudyPlanItem)=JSONObject().apply{put("id",x.id);put("courseCode",x.courseCode);put("topicTag",x.topicTag);put("title",x.title);put("plannedDate",x.plannedDate);put("minutes",x.minutes);put("kind",x.kind);put("completed",x.completed);put("createdAt",x.createdAt);put("updatedAt",x.updatedAt)}
private fun studyPlanItemFromJson(o:JSONObject)=StudyPlanItem(id=o.getString("id"),courseCode=o.optStringOrNull("courseCode"),topicTag=o.optStringOrNull("topicTag"),title=o.getString("title"),plannedDate=o.optString("plannedDate",java.time.LocalDate.now().toString()),minutes=o.optInt("minutes",25).coerceIn(1,1440),kind=o.optString("kind","REVIEW"),completed=o.optBoolean("completed",false),createdAt=o.optLong("createdAt",System.currentTimeMillis()),updatedAt=o.optLong("updatedAt",System.currentTimeMillis()))
private fun flashcardReviewEventToJson(x:FlashcardReviewEvent)=JSONObject().apply{put("id",x.id);put("cardId",x.cardId);put("deckId",x.deckId);put("courseCode",x.courseCode);put("rating",x.rating);put("reviewedAt",x.reviewedAt)}
private fun flashcardReviewEventFromJson(o:JSONObject)=FlashcardReviewEvent(id=o.getString("id"),cardId=o.getString("cardId"),deckId=o.optStringOrNull("deckId"),courseCode=o.optStringOrNull("courseCode"),rating=o.optString("rating","GOOD"),reviewedAt=o.optLong("reviewedAt",System.currentTimeMillis()))
private fun quizAnswerResultToJson(x:QuizAnswerResult)=JSONObject().apply{put("id",x.id);put("attemptId",x.attemptId);put("quizId",x.quizId);put("questionId",x.questionId);put("userAnswer",x.userAnswer);put("correctAnswer",x.correctAnswer);put("correct",x.correct);put("confidence",x.confidence);put("answeredAt",x.answeredAt)}
private fun quizAnswerResultFromJson(o:JSONObject)=QuizAnswerResult(id=o.getString("id"),attemptId=o.getString("attemptId"),quizId=o.getString("quizId"),questionId=o.getString("questionId"),userAnswer=o.optString("userAnswer",""),correctAnswer=o.optString("correctAnswer",""),correct=o.optBoolean("correct",false),confidence=o.optString("confidence","UNSET"),answeredAt=o.optLong("answeredAt",System.currentTimeMillis()))
private fun questionBankItemToJson(x:QuestionBankItem)=JSONObject().apply{put("id",x.id);put("courseCode",x.courseCode);put("type",x.type);put("prompt",x.prompt);put("optionsJson",x.optionsJson);put("correctAnswer",x.correctAnswer);put("explanation",x.explanation);put("tags",x.tags);put("metadataJson",x.metadataJson);put("imageUri",x.imageUri);put("createdAt",x.createdAt);put("updatedAt",x.updatedAt)}
private fun questionBankItemFromJson(o:JSONObject)=QuestionBankItem(id=o.getString("id"),courseCode=o.optStringOrNull("courseCode"),type=o.getString("type"),prompt=o.getString("prompt"),optionsJson=o.optString("optionsJson","[]"),correctAnswer=o.getString("correctAnswer"),explanation=o.optStringOrNull("explanation"),tags=o.optString("tags",""),metadataJson=o.optString("metadataJson","{}"),imageUri=o.optStringOrNull("imageUri"),createdAt=o.optLong("createdAt",System.currentTimeMillis()),updatedAt=o.optLong("updatedAt",System.currentTimeMillis()))

private fun expenseToJson(e: Expense) = JSONObject().apply {
    put("id", e.id); put("amount", e.amount); put("category", e.category)
    put("date", e.date); put("note", e.note); put("ruleId", e.ruleId); put("isRecurring", e.isRecurring)
    put("isFixed", e.isFixed)
}

private fun expenseFromJson(o: JSONObject) = Expense(
    id = o.getString("id"),
    amount = o.getDouble("amount"),
    category = o.getString("category"),
    date = o.getString("date"),
    note = o.optStringOrNull("note"),
    ruleId = o.optStringOrNull("ruleId"),
    isRecurring = o.optBoolean("isRecurring", false),
    isFixed = o.optBoolean("isFixed", false)
)

private fun expenseRuleToJson(r: ExpenseRule) = JSONObject().apply {
    put("id", r.id); put("amount", r.amount); put("category", r.category)
    put("note", r.note); put("startDate", r.startDate); put("repeat", r.repeat); put("lastGenerated", r.lastGenerated)
    put("isFixed", r.isFixed)
}

private fun expenseRuleFromJson(o: JSONObject) = ExpenseRule(
    id = o.getString("id"),
    amount = o.getDouble("amount"),
    category = o.getString("category"),
    note = o.optStringOrNull("note"),
    startDate = o.getString("startDate"),
    repeat = o.getString("repeat"),
    lastGenerated = o.getString("lastGenerated"),
    isFixed = o.optBoolean("isFixed", false)
)

private fun deadlineToJson(d: Deadline) = JSONObject().apply {
    put("id", d.id); put("title", d.title); put("course", d.course); put("due", d.due)
    put("type", d.type); put("priority", d.priority); put("done", d.done)
    put("ruleId", d.ruleId); put("isRecurring", d.isRecurring)
}

private fun deadlineFromJson(o: JSONObject) = Deadline(
    id = o.getString("id"),
    title = o.getString("title"),
    course = o.optStringOrNull("course"),
    due = o.getString("due"),
    type = o.getString("type"),
    priority = o.getString("priority"),
    done = o.optBoolean("done", false),
    ruleId = o.optStringOrNull("ruleId"),
    isRecurring = o.optBoolean("isRecurring", false)
)

private fun deadlineRuleToJson(r: DeadlineRule) = JSONObject().apply {
    put("id", r.id); put("title", r.title); put("course", r.course); put("type", r.type)
    put("priority", r.priority); put("startDate", r.startDate); put("repeat", r.repeat)
}

private fun deadlineRuleFromJson(o: JSONObject) = DeadlineRule(
    id = o.getString("id"),
    title = o.getString("title"),
    course = o.optStringOrNull("course"),
    type = o.getString("type"),
    priority = o.getString("priority"),
    startDate = o.getString("startDate"),
    repeat = o.optString("repeat", "weekly")
)

private fun semesterToJson(s: Semester) = JSONObject().apply { put("id", s.id); put("label", s.label) }

private fun semesterFromJson(o: JSONObject) = Semester(id = o.getString("id"), label = o.getString("label"))

private fun gradeCourseToJson(c: GradeCourse) = JSONObject().apply {
    put("id", c.id); put("semesterId", c.semesterId); put("code", c.code)
    put("title", c.title); put("units", c.units); put("grade", c.grade)
}

private fun gradeCourseFromJson(o: JSONObject) = GradeCourse(
    id = o.getString("id"),
    semesterId = o.getString("semesterId"),
    code = o.getString("code"),
    title = o.optStringOrNull("title"),
    units = o.optDouble("units", 0.0),
    grade = o.optString("grade", "")
)

private fun archiveToJson(a: Archive) = JSONObject().apply {
    put("id", a.id); put("createdAt", a.createdAt); put("label", a.label)
    put("scheduleJson", a.scheduleJson); put("deadlinesJson", a.deadlinesJson)
}

private fun archiveFromJson(o: JSONObject) = Archive(
    id = o.getString("id"),
    createdAt = o.getLong("createdAt"),
    label = o.getString("label"),
    scheduleJson = o.optString("scheduleJson", "[]"),
    deadlinesJson = o.optString("deadlinesJson", "[]")
)

private fun studySessionToJson(s: StudySession) = JSONObject().apply {
    put("id", s.id); put("courseCode", s.courseCode)
    put("startedAt", s.startedAt); put("endedAt", s.endedAt)
    put("plannedMinutes", s.plannedMinutes); put("actualSeconds", s.actualSeconds)
    put("completed", s.completed); put("cyclesInSession", s.cyclesInSession)
    put("endReason", s.endReason); put("suggestionId", s.suggestionId)
}

private fun studySessionFromJson(o: JSONObject) = StudySession(
    id = o.getString("id"),
    courseCode = o.optStringOrNull("courseCode"),
    startedAt = o.getLong("startedAt"),
    endedAt = o.getLong("endedAt"),
    plannedMinutes = o.getInt("plannedMinutes"),
    actualSeconds = o.getInt("actualSeconds"),
    completed = o.optBoolean("completed", false),
    cyclesInSession = o.optInt("cyclesInSession", 1),
    endReason = o.optString("endReason", if (o.optBoolean("completed", false)) "COMPLETED" else "STOPPED_EARLY"),
    suggestionId = o.optStringOrNull("suggestionId")
)


private fun studySuggestionEventToJson(e: StudySuggestionEvent) = JSONObject().apply {
    put("id", e.id); put("suggestionId", e.suggestionId); put("occurredAt", e.occurredAt)
    put("outcome", e.outcome); put("slotHour", e.slotHour); put("dayOfWeek", e.dayOfWeek)
    put("urgencyDays", e.urgencyDays); put("availableMinutes", e.availableMinutes)
    put("deadlineId", e.deadlineId); put("courseCode", e.courseCode); put("sessionId", e.sessionId)
}

private fun studySuggestionEventFromJson(o: JSONObject) = StudySuggestionEvent(
    id = o.getString("id"),
    suggestionId = o.getString("suggestionId"),
    occurredAt = o.getLong("occurredAt"),
    outcome = o.getString("outcome"),
    slotHour = o.optInt("slotHour", 12),
    dayOfWeek = o.optInt("dayOfWeek", 1),
    urgencyDays = o.optInt("urgencyDays", 7),
    availableMinutes = o.optInt("availableMinutes", 25),
    deadlineId = o.optStringOrNull("deadlineId"),
    courseCode = o.optStringOrNull("courseCode"),
    sessionId = o.optStringOrNull("sessionId")
)

private fun notificationEventToJson(e: NotificationEvent) = JSONObject().apply {
    put("id", e.id); put("notificationKey", e.notificationKey); put("workerName", e.workerName)
    put("notificationType", e.notificationType); put("occurredAt", e.occurredAt)
    put("localHour", e.localHour); put("outcome", e.outcome)
}

private fun notificationEventFromJson(o: JSONObject) = NotificationEvent(
    id = o.getString("id"),
    notificationKey = o.getString("notificationKey"),
    workerName = o.optString("workerName", "unknown"),
    notificationType = o.optString("notificationType", "general"),
    occurredAt = o.getLong("occurredAt"),
    localHour = o.optInt("localHour", 12),
    outcome = o.getString("outcome")
)

private fun studySlotModelToJson(model: StudySlotModelState) = JSONObject().apply {
    put("weights", JSONArray(model.weights)); put("bias", model.bias)
    put("sampleCount", model.sampleCount); put("version", model.version)
}

private fun studySlotModelFromJson(o: JSONObject): StudySlotModelState {
    val arr = o.optJSONArray("weights")
    val weights = if (arr != null) List(arr.length()) { index -> arr.optDouble(index, 0.0) } else emptyList()
    return StudySlotModelState(
        weights = weights.takeIf { it.size == StudySlotModelState.FEATURE_COUNT }
            ?: List(StudySlotModelState.FEATURE_COUNT) { 0.0 },
        bias = o.optDouble("bias", 0.0),
        sampleCount = o.optInt("sampleCount", 0),
        version = o.optInt("version", 1)
    )
}

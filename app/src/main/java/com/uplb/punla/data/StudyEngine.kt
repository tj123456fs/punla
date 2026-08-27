package com.uplb.punla.data

import com.uplb.punla.data.entity.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Pure functions behind Study Hub. Kept Android-free so they are easy to test. */
object StudyEngine {
    data class WeakTopic(
        val key: String,
        val courseCode: String?,
        val topic: String,
        val misses: Int,
        val attempts: Int,
        val mastery: Int,
        val readiness: Int
    )

    enum class QueueKind { FLASHCARD, MISTAKE, PLAN, QUIZ, NOTE, FORMULA }

    data class QueueItem(
        val id: String,
        val kind: QueueKind,
        val courseCode: String?,
        val topic: String?,
        val title: String,
        val subtitle: String,
        val priority: Int
    )

    fun weakTopics(
        mistakes: List<MistakeRecord>,
        cards: List<Flashcard>,
        decks: List<FlashcardDeck>,
        questions: List<QuizQuestion>,
        quizzes: List<Quiz>,
        results: List<QuizAnswerResult>
    ): List<WeakTopic> {
        data class Bucket(
            var misses: Int = 0,
            var attempts: Int = 0,
            var masterySum: Int = 0,
            var masteryCount: Int = 0,
            var unresolved: Int = 0,
            var course: String? = null,
            var topic: String = "General"
        )
        val map = linkedMapOf<String, Bucket>()

        // Performance history is the primary evidence. A wrong flashcard/quiz
        // can also create a MistakeRecord, so counting mistakes first would
        // double-count the exact same failure.
        val deckById = decks.associateBy { it.id }
        cards.forEach { c ->
            val deck = deckById[c.deckId]
            val topic = c.tagList().firstOrNull() ?: deck?.name ?: "General"
            val key = "${deck?.courseCode.orEmpty().lowercase()}|${topic.lowercase()}"
            val b = map.getOrPut(key) { Bucket(course = deck?.courseCode, topic = topic) }
            b.masterySum += c.mastery.coerceIn(0, 5)
            b.masteryCount++
            b.attempts += c.reviewCount.coerceAtLeast(0)
            b.misses += (c.reviewCount - c.correctCount).coerceAtLeast(0)
        }

        val questionById = questions.associateBy { it.id }
        val quizById = quizzes.associateBy { it.id }
        results.forEach { r ->
            val q = questionById[r.questionId] ?: return@forEach
            val quiz = quizById[r.quizId]
            val topic = q.tagList().firstOrNull() ?: quiz?.title ?: "General"
            val key = "${quiz?.courseCode.orEmpty().lowercase()}|${topic.lowercase()}"
            val b = map.getOrPut(key) { Bucket(course = quiz?.courseCode, topic = topic) }
            b.attempts++
            // A lucky guess is intentionally a weak signal even when scored
            // correct, but it is counted only once here.
            if (!r.correct || r.confidence == StudyConfidence.GUESSED) b.misses++
        }

        // Mistake rows contribute "still needs work" pressure without counting
        // the same historical miss again. Standalone/imported mistakes that
        // have no source performance evidence can still seed a weak topic.
        mistakes.filter { !it.resolved }.forEach { m ->
            val topic = m.topicTag?.takeIf { it.isNotBlank() } ?: "General"
            val key = "${m.courseCode.orEmpty().lowercase()}|${topic.lowercase()}"
            val b = map.getOrPut(key) { Bucket(course = m.courseCode, topic = topic) }
            b.unresolved++
            if (b.attempts == 0 && b.masteryCount == 0) {
                val misses = m.timesMissed.coerceAtLeast(1)
                b.attempts += misses
                b.misses += misses
            }
        }

        return map.map { (key, b) ->
            val mastery = if (b.masteryCount == 0) 0
            else ((b.masterySum / (5.0 * b.masteryCount)) * 100).roundToInt()

            val accuracy = if (b.attempts <= 0) 50
            else (((b.attempts - b.misses).coerceAtLeast(0) * 100.0) / b.attempts).roundToInt()

            val base = if (b.masteryCount > 0) {
                (mastery * 0.55 + accuracy * 0.45).roundToInt()
            } else {
                accuracy
            }
            // Unresolved items are an urgency/remediation signal, capped so
            // they cannot erase all performance evidence.
            val unresolvedPenalty = b.unresolved.coerceAtMost(5) * 3
            WeakTopic(
                key = key,
                courseCode = b.course,
                topic = b.topic,
                misses = maxOf(b.misses, b.unresolved),
                attempts = b.attempts,
                mastery = mastery,
                readiness = (base - unresolvedPenalty).coerceIn(0, 100)
            )
        }.sortedWith(compareBy<WeakTopic> { it.readiness }.thenByDescending { it.misses })
    }

    fun readinessForCourse(
        courseCode: String,
        weak: List<WeakTopic>,
        dueCards: Int,
        unresolvedMistakes: Int,
        examDate: LocalDate?
    ): Int {
        val courseTopics = weak.filter { it.courseCode.equals(courseCode, true) }
        // 50 is deliberately neutral when there is no performance history.
        // Exam proximity changes urgency/queue priority, not knowledge itself,
        // so it must not artificially lower "readiness".
        val topicBase = if (courseTopics.isEmpty()) 50
        else courseTopics.map { it.readiness }.average().roundToInt()
        val duePenalty = (dueCards.coerceAtMost(40) * 0.5).roundToInt()
        val mistakePenalty = (unresolvedMistakes.coerceAtMost(20) * 1.2).roundToInt()
        examDate?.let { /* urgency is handled by smartQueue() */ }
        return (topicBase - duePenalty - mistakePenalty).coerceIn(0, 100)
    }

    /** Interleaves courses so one deck/question type does not dominate the whole session. */
    fun smartQueue(
        cards: List<Flashcard>,
        decks: List<FlashcardDeck>,
        mistakes: List<MistakeRecord>,
        planItems: List<StudyPlanItem>,
        examDates: Map<String, LocalDate>,
        now: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now()
    ): List<QueueItem> {
        val deckById = decks.associateBy { it.id }
        val normalizedExamDates = examDates.entries.associate { it.key.lowercase() to it.value }
        fun examFor(course: String?): LocalDate? = course?.let { normalizedExamDates[it.lowercase()] }
        val items = mutableListOf<QueueItem>()
        cards.filter { it.isDue(now) }.forEach { c ->
            val deck = deckById[c.deckId]
            val days = examFor(deck?.courseCode)?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it).toInt() }
            val cramBoost = if (days != null && days in 0..3) 25 else 0
            val weaknessBoost = (5 - c.mastery).coerceAtLeast(0) * 5
            items += QueueItem(c.id, QueueKind.FLASHCARD, deck?.courseCode, c.tagList().firstOrNull(), deck?.name ?: "Flashcard review", if (c.isNew()) "New card" else "Mastery ${c.mastery}/5", 50 + cramBoost + weaknessBoost + if (c.starred) 8 else 0)
        }
        mistakes.filter { !it.resolved && it.retryAt <= now }.forEach { m ->
            val days = examFor(m.courseCode)?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it).toInt() }
            val cramBoost = if (days != null && days in 0..3) 25 else 0
            items += QueueItem(m.id, QueueKind.MISTAKE, m.courseCode, m.topicTag, m.prompt, "Retry mistake · missed ${m.timesMissed}×", 85 + cramBoost + m.timesMissed.coerceAtMost(5) * 3)
        }
        planItems.filter { !it.completed && runCatching { LocalDate.parse(it.plannedDate) <= today }.getOrDefault(false) }.forEach { p ->
            items += QueueItem(p.id, QueueKind.PLAN, p.courseCode, p.topicTag, p.title, "${p.minutes} min · ${p.kind.lowercase().replace('_', ' ')}", 70 + if (p.plannedDate < today.toString()) 12 else 0)
        }

        // Keep priority meaningful while still interleaving subjects. We only
        // switch away from the highest-priority course when another course is
        // reasonably close in priority; a low-priority alphabetic course can
        // no longer jump ahead of an urgent exam item.
        val grouped = items.sortedByDescending { it.priority }
            .groupBy { it.courseCode?.lowercase() ?: "~" }
            .mapValues { it.value.toMutableList() }
            .toMutableMap()
        val out = mutableListOf<QueueItem>()
        var lastCourse: String? = null
        while (grouped.values.any { it.isNotEmpty() }) {
            val candidates = grouped.entries
                .filter { it.value.isNotEmpty() }
                .sortedWith(compareByDescending<Map.Entry<String, MutableList<QueueItem>>> { it.value.first().priority }
                    .thenBy { it.key })
            val best = candidates.firstOrNull() ?: break
            val alternate = candidates.firstOrNull {
                it.key != lastCourse && it.value.first().priority >= best.value.first().priority - 12
            }
            val chosen = if (best.key == lastCourse && alternate != null) alternate else best
            chosen.value.removeFirstOrNull()?.let(out::add)
            lastCourse = chosen.key
        }
        return out
    }

    fun generateExamPlan(courseCode: String, examDate: LocalDate, topics: List<String>, minutesPerDay: Int = 50): List<StudyPlanItem> {
        val today = LocalDate.now()
        if (examDate.isBefore(today)) return emptyList()
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, examDate).toInt().coerceAtLeast(1)
        val topicList = topics.filter { it.isNotBlank() }.ifEmpty { listOf("General review") }
        val items = mutableListOf<StudyPlanItem>()
        for (offset in 0 until days) {
            val date = today.plusDays(offset.toLong())
            val topic = topicList[offset % topicList.size]
            val kind = when {
                offset == days - 1 -> StudyPlanKinds.PRACTICE_TEST
                offset % 3 == 0 -> StudyPlanKinds.FLASHCARDS
                offset % 3 == 1 -> StudyPlanKinds.QUIZ
                else -> StudyPlanKinds.NOTES
            }
            items += StudyPlanItem(
                courseCode = courseCode,
                topicTag = topic,
                title = "Exam prep: $topic",
                plannedDate = date.toString(),
                minutes = minutesPerDay.coerceIn(10, 180),
                kind = kind
            )
        }
        return items
    }

    fun meaningfulStudyDays(
        sessions: List<StudySession>,
        attempts: List<QuizAttempt>,
        flashcardReviews: List<FlashcardReviewEvent>,
        zone: ZoneId = ZoneId.systemDefault()
    ): Set<LocalDate> {
        val days = mutableSetOf<LocalDate>()

        // A streak should represent real study, not opening a tool or tapping a
        // single card. Five focused minutes is the shared minimum.
        sessions.filter { it.actualSeconds >= 5 * 60 }.forEach {
            days += Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
        }
        attempts.filter { it.total >= 5 || it.durationMs >= 5 * 60 * 1000L }.forEach {
            days += Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate()
        }
        flashcardReviews
            .groupingBy { Instant.ofEpochMilli(it.reviewedAt).atZone(zone).toLocalDate() }
            .eachCount()
            .filterValues { it >= 5 }
            .keys
            .let(days::addAll)
        return days
    }

    fun currentStreak(days: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        var day = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (day in days) { streak++; day = day.minusDays(1) }
        return streak
    }

    fun evaluate(question: QuizQuestion, answer: String): Boolean {
        return when (question.type) {
            QuizQuestionTypes.NUMERIC -> {
                val actual = answer.trim().toDoubleOrNull()
                val expected = question.correctAnswer.trim().toDoubleOrNull()
                if (actual == null || expected == null) false else {
                    val tolerance = runCatching { JSONObject(question.metadataJson).optDouble("tolerance", 0.0) }
                        .getOrDefault(0.0)
                        .coerceAtLeast(0.0)
                    kotlin.math.abs(actual - expected) <= tolerance
                }
            }
            QuizQuestionTypes.MULTI_SELECT -> {
                val expected = parseAnswerSet(question.correctAnswer)
                expected.isNotEmpty() && parseAnswerSet(answer) == expected
            }
            QuizQuestionTypes.ORDERING -> {
                val expected = parseAnswerList(question.correctAnswer)
                expected.isNotEmpty() && parseAnswerList(answer) == expected
            }
            QuizQuestionTypes.MATCHING -> {
                val expected = normalizeJsonObjectOrNull(question.correctAnswer)
                val actual = normalizeJsonObjectOrNull(answer)
                expected != null && expected.isNotEmpty() && actual != null && actual == expected
            }
            else -> question.isCorrect(answer)
        }
    }

    private fun parseAnswerSet(raw: String): Set<String> =
        parseAnswerList(raw).map { QuizQuestion.normalizeAnswer(it) }.filter { it.isNotEmpty() }.toSet()

    private fun parseAnswerList(raw: String): List<String> = runCatching {
        val arr = JSONArray(raw)
        List(arr.length()) { arr.optString(it).trim() }.filter { it.isNotEmpty() }
    }.getOrElse { raw.split('|').map { it.trim() }.filter { it.isNotEmpty() } }

    private fun normalizeJsonObjectOrNull(raw: String): Map<String, String>? = runCatching {
        val obj = JSONObject(raw)
        if (obj.length() == 0) return@runCatching emptyMap()
        obj.keys().asSequence().associateWith { key ->
            QuizQuestion.normalizeAnswer(obj.optString(key))
        }.filterValues { it.isNotEmpty() }.toSortedMap()
    }.getOrNull()
}

/** Tiny dependency-free formatter for the most common study math markup. */
object StudyMathText {
    private val superscripts = mapOf('0' to '⁰','1' to '¹','2' to '²','3' to '³','4' to '⁴','5' to '⁵','6' to '⁶','7' to '⁷','8' to '⁸','9' to '⁹','+' to '⁺','-' to '⁻','=' to '⁼','(' to '⁽',')' to '⁾')
    private val subscripts = mapOf('0' to '₀','1' to '₁','2' to '₂','3' to '₃','4' to '₄','5' to '₅','6' to '₆','7' to '₇','8' to '₈','9' to '₉','+' to '₊','-' to '₋','=' to '₌','(' to '₍',')' to '₎')

    /** Supports \\frac{a}{b}, \\sqrt{x}, ^{n}, _{n}, \\times, \\cdot, \\pm. */
    fun render(source: String): String {
        var out = source
            .replace("\\times", "×").replace("\\cdot", "·").replace("\\pm", "±")
            .replace(Regex("\\\\sqrt\\{([^{}]+)}")) { "√(${it.groupValues[1]})" }
            .replace(Regex("\\\\frac\\{([^{}]+)}\\{([^{}]+)}")) { "${it.groupValues[1]}⁄${it.groupValues[2]}" }
        out = Regex("\\^\\{([^{}]+)}").replace(out) { it.groupValues[1].map { c -> superscripts[c] ?: c }.joinToString("") }
        out = Regex("_\\{([^{}]+)}").replace(out) { it.groupValues[1].map { c -> subscripts[c] ?: c }.joinToString("") }
        return out
    }
}

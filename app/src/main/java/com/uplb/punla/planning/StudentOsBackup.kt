package com.uplb.punla.planning

import android.content.Context
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

data class StudentOsBackupData(val preferences: List<TaskPreferences>, val captures: List<InboxCapture>,
    val blocks: List<DayPlanBlock>, val life: List<LifeCommitment>, val settings: List<OsSetting>,
    val attachments: Map<String, ByteArray>)

/** All validation happens before the existing transactional restore deletes any rows. */
object StudentOsBackup {
    suspend fun export(context: Context, dao: StudentOsDao): JSONObject {
        val captures = dao.captures()
        val attachments = JSONObject()
        var bytes = 0L
        captures.mapNotNull { it.attachment }.distinct().forEach { name ->
            val file = CaptureAttachments.file(context, name)
            require(file.isFile) { "Inbox attachment $name is missing. Restore it before exporting." }
            bytes += file.length()
            require(bytes <= 64L * 1024 * 1024) { "Inbox attachments exceed the 64 MB backup limit." }
            require(file.length() <= CaptureAttachments.MAX_BYTES) { "An inbox attachment exceeds 8 MB." }
            attachments.put(name, Base64.getEncoder().encodeToString(file.readBytes()))
        }
        return JSONObject().apply {
            put("version", 1)
            put("preferences", JSONArray(dao.preferences().map { x -> JSONObject().apply {
                put("id", x.id); put("estimatedMinutes", x.estimatedMinutes); put("progress", x.progress)
                put("importance", x.importance); put("effort", x.effort); put("dueTime", x.dueTime)
                put("pinned", x.pinned); put("dismissedUntil", x.dismissedUntil)
            } }))
            put("captures", JSONArray(captures.map { x -> JSONObject().apply {
                put("id", x.id); put("text", x.text); put("attachment", x.attachment ?: JSONObject.NULL)
                put("createdAt", x.createdAt); put("processed", x.processed)
            } }))
            put("blocks", JSONArray(dao.blocks().map { x -> JSONObject().apply {
                put("id", x.id); put("taskId", x.taskId); put("title", x.title); put("startAt", x.startAt)
                put("endAt", x.endAt); put("status", x.status); put("locked", x.locked)
            } }))
            put("life", JSONArray(dao.life().map { x -> JSONObject().apply {
                put("id", x.id); put("title", x.title); put("startTime", x.startTime); put("endTime", x.endTime)
                put("days", x.days); put("category", x.category); put("enabled", x.enabled)
            } }))
            put("settings", JSONArray(dao.settings().map { x -> JSONObject().put("key", x.key).put("value", x.value) }))
            put("attachments", attachments)
        }
    }
    fun parse(root: JSONObject?): StudentOsBackupData {
        if (root == null) return StudentOsBackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
        require(root.getInt("version") == 1) { "Unsupported planning backup version." }
        fun <T> rows(key: String, read: (JSONObject) -> T): List<T> {
            val array = root.getJSONArray(key)
            require(array.length() <= 100000) { "Too many planning records." }
            return List(array.length()) { read(array.getJSONObject(it)) }
        }
        val profiles = rows("preferences") { o -> TaskPreferences(o.getString("id"), o.getInt("estimatedMinutes"),
            o.getInt("progress"), o.getInt("importance"), o.getInt("effort"), o.getString("dueTime"), o.getBoolean("pinned"), o.getLong("dismissedUntil")) }
        profiles.forEach { require(it.id.isNotBlank() && it.estimatedMinutes in 5..10080 && it.progress in 0..100 && it.importance in 1..5 && it.effort in 1..3); LocalTime.parse(it.dueTime) }
        val captures = rows("captures") { o -> InboxCapture(o.getString("id"), o.getString("text"),
            if (o.isNull("attachment")) null else o.getString("attachment"), o.getLong("createdAt"), o.getBoolean("processed")) }
        captures.forEach { require(it.id.isNotBlank() && it.text.length <= 20000 && it.createdAt >= 0) }
        val blocks = rows("blocks") { o -> DayPlanBlock(o.getString("id"), o.getString("taskId"), o.getString("title"),
            o.getLong("startAt"), o.getLong("endAt"), o.getString("status"), o.getBoolean("locked")) }
        blocks.forEach { require(it.id.isNotBlank() && it.taskId.isNotBlank() && it.startAt >= 0 && it.window().minutes in 1..10080 &&
            it.status in listOf("PLANNED", "DONE", "SKIPPED")) }
        val life = rows("life") { o -> LifeCommitment(o.getString("id"), o.getString("title"), o.getString("startTime"),
            o.getString("endTime"), o.getString("days"), o.getString("category"), o.getBoolean("enabled")) }
        life.forEach { require(it.id.isNotBlank() && it.title.isNotBlank()); LocalTime.parse(it.startTime); LocalTime.parse(it.endTime)
            require(it.startTime != it.endTime && it.days.split(',').all { day -> day.toIntOrNull() in 1..7 }) }
        val settings = rows("settings") { o -> OsSetting(o.getString("key"), o.getString("value")) }
        val prefs = settings.associate { it.key to it.value }
        require(LocalTime.parse(prefs["dayStart"] ?: "08:00") < LocalTime.parse(prefs["dayEnd"] ?: "22:00"))
        prefs["travelMinutes"]?.let { require(it.toIntOrNull() in 0..120) }
        listOf(profiles.map { it.id }, captures.map { it.id }, blocks.map { it.id }, life.map { it.id }, settings.map { it.key }).forEach {
            require(it.toSet().size == it.size) { "Duplicate planning record IDs." }
        }
        val stored = root.getJSONObject("attachments")
        val attachments = mutableMapOf<String, ByteArray>(); var bytes = 0L
        stored.keys().forEach { name ->
            require(Regex("[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]{1,10}").matches(name)) { "Invalid attachment name." }
            val content = stored.getString(name)
            require(content.length <= CaptureAttachments.MAX_BYTES * 4 / 3 + 8)
            val decoded = Base64.getDecoder().decode(content)
            require(decoded.size <= CaptureAttachments.MAX_BYTES)
            bytes += decoded.size; require(bytes <= 64L * 1024 * 1024)
            attachments[name] = decoded
        }
        require(captures.mapNotNull { it.attachment }.all { it in attachments }) { "An inbox attachment is missing from this backup." }
        return StudentOsBackupData(profiles, captures, blocks, life, settings, attachments)
    }
    /** New names prevent a failed restore from replacing files referenced by the current DB. */
    fun stageAttachments(context: Context, data: StudentOsBackupData): StudentOsBackupData {
        val renamed = mutableMapOf<String, String>()
        try {
            data.attachments.forEach { (old, bytes) ->
                val name = "${java.util.UUID.randomUUID()}.${old.substringAfterLast('.')}"
                val file = CaptureAttachments.file(context, name); file.parentFile?.mkdirs()
                renamed[old] = name; file.writeBytes(bytes)
            }
        } catch (e: Exception) {
            renamed.values.forEach { CaptureAttachments.file(context, it).delete() }; throw e
        }
        return data.copy(captures = data.captures.map { it.copy(attachment = it.attachment?.let(renamed::getValue)) },
            attachments = data.attachments.mapKeys { renamed.getValue(it.key) })
    }
    suspend fun restore(dao: StudentOsDao, data: StudentOsBackupData) {
        dao.clearPreferences(); dao.clearCaptures(); dao.clearBlocks(); dao.clearLife(); dao.clearSettings()
        data.preferences.forEach { dao.save(it) }; data.captures.forEach { dao.save(it) }
        data.blocks.forEach { dao.save(it) }; data.life.forEach { dao.save(it) }; data.settings.forEach { dao.save(it) }
    }
}

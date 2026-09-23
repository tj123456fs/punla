package com.uplb.punla.planning

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudentOsBackupTest {
    private fun empty() = JSONObject("""{"version":1,"preferences":[],"captures":[],"blocks":[],"life":[],"settings":[],"attachments":{}}""")
    @Test fun attachmentBytesSurviveJsonRoundTrip() {
        val root = empty()
        val bytes = byteArrayOf(0, 1, 127, -128, -1)
        root.getJSONObject("attachments").put("notes.pdf", java.util.Base64.getEncoder().encodeToString(bytes))
        root.getJSONArray("captures").put(JSONObject("""{"id":"x","text":"PDF","attachment":"notes.pdf","createdAt":100,"processed":false}"""))
        val data = StudentOsBackup.parse(JSONObject(root.toString()))
        assertArrayEquals(bytes, data.attachments.getValue(data.captures.single().attachment!!))
    }
    @Test(expected = IllegalArgumentException::class) fun malformedAttachmentCannotSilentlyLoseBytes() {
        val root = empty(); root.getJSONObject("attachments").put("notes.pdf", "not valid base64!!!")
        StudentOsBackup.parse(root)
    }
    @Test fun oldBackupsHaveEmptyPlanningData() {
        assertTrue(StudentOsBackup.parse(null).blocks.isEmpty())
    }
    @Test fun validRowsKeepUserControlAndTiming() {
        val root = empty()
        root.getJSONArray("preferences").put(JSONObject("""{"id":"deadline:d1","estimatedMinutes":120,"progress":20,"importance":5,"effort":3,"dueTime":"23:59","pinned":true,"dismissedUntil":0}"""))
        root.getJSONArray("blocks").put(JSONObject("""{"id":"b1","taskId":"deadline:d1","title":"Report","startAt":1800000000000,"endAt":1800001500000,"status":"PLANNED","locked":true}"""))
        val data = StudentOsBackup.parse(root)
        assertTrue(data.preferences.single().pinned)
        assertEquals(20, data.preferences.single().progress)
        assertTrue(data.blocks.single().locked)
        assertEquals(25, data.blocks.single().window().minutes)
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateIdsCannotOverwriteDuringRestore() {
        val root = empty()
        val row = JSONObject("""{"id":"capture1","text":"Read notes","attachment":null,"createdAt":100,"processed":false}""")
        root.getJSONArray("captures").put(row).put(row)
        StudentOsBackup.parse(root)
    }
    @Test(expected = IllegalArgumentException::class) fun corruptTimingFailsBeforeRestore() {
        val root = empty()
        root.getJSONArray("blocks").put(JSONObject("""{"id":"b1","taskId":"a","title":"Report","startAt":50000,"endAt":1000,"status":"PLANNED","locked":false}"""))
        StudentOsBackup.parse(root)
    }
    @Test(expected = IllegalArgumentException::class) fun missingAttachmentIsRejected() {
        val root = empty()
        root.getJSONArray("captures").put(JSONObject("""{"id":"x","text":"PDF","attachment":"missing.pdf","createdAt":100,"processed":false}"""))
        StudentOsBackup.parse(root)
    }
    @Test(expected = IllegalArgumentException::class) fun pathTraversalAttachmentIsRejected() {
        val root = empty(); root.getJSONObject("attachments").put("../../punla.db", "")
        StudentOsBackup.parse(root)
    }
    @Test(expected = IllegalArgumentException::class) fun reversedPlanningHoursAreRejected() {
        val root = empty()
        root.getJSONArray("settings").put(JSONObject("""{"key":"dayStart","value":"22:00"}"""))
        root.getJSONArray("settings").put(JSONObject("""{"key":"dayEnd","value":"08:00"}"""))
        StudentOsBackup.parse(root)
    }
}

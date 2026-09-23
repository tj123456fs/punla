package com.uplb.punla.planning

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.uplb.punla.MainActivity
import com.uplb.punla.data.PunlaDatabase
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

/** Share only captures a draft. It cannot mark tasks or attendance, or execute shared text. */
class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) { finish(); return }
        lifecycleScope.launch {
            var unsavedAttachment: String? = null
            try {
                check(intent.action == Intent.ACTION_SEND) { "Share one item at a time." }
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty().take(20000)
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                val attachment = if (uri != null) withContext(Dispatchers.IO) { CaptureAttachments.copy(this@CaptureActivity, uri) } else null
                unsavedAttachment = attachment
                check(text.isNotBlank() || attachment != null) { "No text or attachment was shared." }
                val title = text.ifBlank { intent.getStringExtra(Intent.EXTRA_SUBJECT)?.take(200) ?: "Shared material" }
                PunlaDatabase.get(this@CaptureActivity).studentOsDao().save(InboxCapture(text = title, attachment = attachment))
                unsavedAttachment = null
                Toast.makeText(this@CaptureActivity, "Saved to Punla Inbox", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@CaptureActivity, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_START_ROUTE, "student-os?tab=1").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Toast.makeText(this@CaptureActivity, e.message ?: "Could not capture the shared item.", Toast.LENGTH_LONG).show() }
            finally {
                unsavedAttachment?.let { CaptureAttachments.file(this@CaptureActivity, it).delete() }
                finish()
            }
        }
    }
}

object CaptureAttachments {
    const val MAX_BYTES = 8 * 1024 * 1024
    fun file(context: Context, name: String): File {
        require(Regex("[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]{1,10}").matches(name)) { "Invalid attachment name." }
        return File(File(context.filesDir, "inbox"), name)
    }
    fun copy(context: Context, uri: Uri): String {
        require(uri.scheme == "content") { "Share attachments from an Android content provider." }
        val mime = context.contentResolver.getType(uri)
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,10}")) } ?: "bin"
        val name = "${UUID.randomUUID()}.$extension"
        val target = file(context, name); target.parentFile?.mkdirs()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192); var size = 0
                    while (true) {
                        val read = input.read(buffer); if (read < 0) break
                        size += read; require(size <= MAX_BYTES) { "Attachments may be up to 8 MB." }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("Could not open the attachment.")
            return name
        } catch (e: Exception) { target.delete(); throw e }
    }
    fun open(context: Context, name: String) {
        runCatching {
            val file = file(context, name); check(file.exists()) { "Attachment is missing." }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.inbox", file)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { Toast.makeText(context, "No app could open this attachment.", Toast.LENGTH_LONG).show() }
    }
}

package com.uplb.punla.diagnostics

import android.content.Context
import java.io.File
import java.time.Instant

/**
 * Small local-only diagnostic log used by Phase 0 reliability work.
 *
 * The log never leaves the device automatically. It intentionally stores only
 * component names, short messages, and stack traces supplied by Punla itself;
 * callers should not pass study content, notes, API keys, or other user data.
 */
object PunlaDiagnostics {
    private const val FILE_NAME = "punla_diagnostics.log"
    private const val MAX_BYTES = 256 * 1024L
    private const val KEEP_BYTES = 128 * 1024
    private const val MAX_THROWABLE_CHARS = 12_000

    private val lock = Any()

    fun info(context: Context, tag: String, message: String) = write(context, "INFO", tag, message, null)

    fun warn(context: Context, tag: String, message: String, error: Throwable? = null) =
        write(context, "WARN", tag, message, error)

    fun error(context: Context, tag: String, message: String, error: Throwable? = null) =
        write(context, "ERROR", tag, message, error)

    fun read(context: Context): String = synchronized(lock) {
        runCatching { file(context).takeIf(File::exists)?.readText().orEmpty() }.getOrDefault("")
    }

    fun clear(context: Context) = synchronized(lock) {
        runCatching { file(context).delete() }
    }

    private fun write(context: Context, level: String, tag: String, message: String, error: Throwable?) {
        val safeTag = tag.replace(Regex("[\\r\\n\\t]+"), " ").take(80)
        val safeMessage = message.replace(Regex("[\\r\\n\\t]+"), " ").take(1_000)
        val stack = error?.stackTraceToString()?.take(MAX_THROWABLE_CHARS)
        val line = buildString {
            append(Instant.now()).append(' ').append(level).append(' ')
            append('[').append(safeTag).append("] ").append(safeMessage).append('\n')
            if (!stack.isNullOrBlank()) append(stack).append('\n')
        }
        synchronized(lock) {
            runCatching {
                val target = file(context)
                target.parentFile?.mkdirs()
                trimIfNeeded(target)
                target.appendText(line)
            }
        }
    }

    private fun trimIfNeeded(target: File) {
        if (!target.exists() || target.length() < MAX_BYTES) return
        val bytes = target.readBytes()
        val start = (bytes.size - KEEP_BYTES).coerceAtLeast(0)
        target.writeBytes(bytes.copyOfRange(start, bytes.size))
    }

    private fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)
}

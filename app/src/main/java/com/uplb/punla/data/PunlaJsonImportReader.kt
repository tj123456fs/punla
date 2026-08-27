package com.uplb.punla.data

import android.content.Context
import android.net.Uri

/**
 * Reads an import document without ever buffering an unbounded file in memory.
 * Call this from a background dispatcher. The parsers still enforce their own
 * schema/type limits after this guard.
 */
object PunlaJsonImportReader {
    fun readText(context: Context, uri: Uri, maxChars: Int): String {
        require(maxChars > 0) { "maxChars must be positive." }
        val resolver = context.contentResolver
        val approxMb = (maxChars / 1_000_000).coerceAtLeast(1)
        val tooLargeMessage = "This JSON file is too large for Punla. Keep imports under about ${approxMb} MB."

        // Provider lengths are optional and byte-based, while parser limits are
        // character-based. Use this only as an early rejection for obviously
        // huge documents; the bounded character loop below is authoritative.
        resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            val length = descriptor.length
            val generousByteCeiling = maxChars.toLong() * 4L + 4096L
            if (length >= 0L && length > generousByteCeiling) {
                throw IllegalArgumentException(tooLargeMessage)
            }
        }

        val stream = resolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Punla couldn't open that file.")
        return stream.bufferedReader().use { reader ->
            val out = StringBuilder(minOf(maxChars, 65_536))
            val buffer = CharArray(8_192)
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                if (out.length + read > maxChars) {
                    throw IllegalArgumentException(tooLargeMessage)
                }
                out.append(buffer, 0, read)
            }
            out.toString()
        }
    }
}

package com.uplb.punla.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.uplb.punla.data.StudyMathText
import com.uplb.punla.ui.theme.PunlaMono

internal sealed interface StudyDocBlock {
    data class Heading(val level: Int, val text: String) : StudyDocBlock
    data class Paragraph(val text: String) : StudyDocBlock
    data class Bullet(val text: String) : StudyDocBlock
    data class Numbered(val number: String, val text: String) : StudyDocBlock
    data class Quote(val text: String) : StudyDocBlock
    data class Code(val text: String) : StudyDocBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : StudyDocBlock
    data object Divider : StudyDocBlock
}

/**
 * Dependency-free Markdown-ish parser for reviewer notes. Punla deliberately
 * renders from the stored source instead of rewriting it, so export/backup
 * remains byte-for-byte compatible with existing study packs.
 */
internal fun parseStudyDocument(raw: String): List<StudyDocBlock> {
    if (raw.isBlank()) return emptyList()
    val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val out = mutableListOf<StudyDocBlock>()
    val paragraph = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            out += StudyDocBlock.Paragraph(paragraph.joinToString(" ").trim())
            paragraph.clear()
        }
    }

    fun splitTableRow(line: String): List<String> =
        line.trim().trim('|').split('|').map { it.trim() }

    fun isTableSeparator(line: String): Boolean {
        val cells = splitTableRow(line)
        return cells.isNotEmpty() && cells.all { it.matches(Regex(":?-{3,}:?")) }
    }

    var i = 0
    while (i < lines.size) {
        val original = lines[i]
        val line = original.trim()

        if (line.startsWith("```")) {
            flushParagraph()
            val code = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                code += lines[i]
                i++
            }
            out += StudyDocBlock.Code(code.joinToString("\n").trimEnd())
            if (i < lines.size) i++
            continue
        }

        if (line.isBlank()) {
            flushParagraph()
            i++
            continue
        }

        val heading = Regex("^(#{1,3})\\s+(.+)$").matchEntire(line)
        if (heading != null) {
            flushParagraph()
            out += StudyDocBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            i++
            continue
        }

        if (line.matches(Regex("^(-{3,}|_{3,}|\\*{3,})$"))) {
            flushParagraph()
            out += StudyDocBlock.Divider
            i++
            continue
        }

        if (line.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            flushParagraph()
            val headers = splitTableRow(line)
            val rows = mutableListOf<List<String>>()
            i += 2
            while (i < lines.size) {
                val row = lines[i].trim()
                if (row.isBlank() || !row.contains('|')) break
                rows += splitTableRow(row)
                i++
            }
            out += StudyDocBlock.Table(headers, rows)
            continue
        }

        val bullet = Regex("^[-*+]\\s+(.+)$").matchEntire(line)
        if (bullet != null) {
            flushParagraph()
            out += StudyDocBlock.Bullet(bullet.groupValues[1].trim())
            i++
            continue
        }

        val numbered = Regex("^(\\d+)[.)]\\s+(.+)$").matchEntire(line)
        if (numbered != null) {
            flushParagraph()
            out += StudyDocBlock.Numbered(numbered.groupValues[1], numbered.groupValues[2].trim())
            i++
            continue
        }

        if (line.startsWith('>')) {
            flushParagraph()
            out += StudyDocBlock.Quote(line.removePrefix(">").trim())
            i++
            continue
        }

        paragraph += line
        i++
    }
    flushParagraph()
    return out
}

@Composable
internal fun StudyRichText(raw: String, modifier: Modifier = Modifier) {
    val blocks = remember(raw) { parseStudyDocument(raw) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is StudyDocBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    Text(inlineStudyText(block.text), style = style, fontWeight = FontWeight.SemiBold)
                }
                is StudyDocBlock.Paragraph ->
                    Text(inlineStudyText(block.text), style = MaterialTheme.typography.bodyMedium)

                is StudyDocBlock.Bullet -> StudyListRow(marker = "•", text = block.text)
                is StudyDocBlock.Numbered -> StudyListRow(marker = "${block.number}.", text = block.text)

                is StudyDocBlock.Quote -> Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .55f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                        Text("❝", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(inlineStudyText(block.text), style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                    }
                }

                is StudyDocBlock.Code -> Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                        Text(
                            StudyMathText.render(block.text),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PunlaMono),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is StudyDocBlock.Table -> StudyTable(block)
                StudyDocBlock.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun StudyListRow(marker: String, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(marker, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
        Text(inlineStudyText(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StudyTable(table: StudyDocBlock.Table) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
            StudyTableRow(table.headers, header = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            table.rows.forEachIndexed { index, row ->
                StudyTableRow(row, header = false)
                if (index != table.rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
            }
        }
    }
}

@Composable
private fun StudyTableRow(cells: List<String>, header: Boolean) {
    Row {
        cells.forEach { cell ->
            Text(
                inlineStudyText(cell),
                modifier = Modifier.widthIn(min = 120.dp, max = 220.dp).padding(horizontal = 12.dp, vertical = 9.dp),
                style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/** Inline subset: **bold**, *italic*, _italic_, ~~strike~~ and `code`. */
@Composable
private fun inlineStudyText(source: String): AnnotatedString {
    val rendered = StudyMathText.render(source)
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    return remember(rendered, codeBackground) { buildInlineStudyText(rendered, codeBackground) }
}

internal fun buildInlineStudyText(source: String, codeBackground: Color = Color.Transparent): AnnotatedString = buildAnnotatedString {
    val token = Regex("(`[^`]+`|\\*\\*[^*]+\\*\\*|~~[^~]+~~|\\*[^*]+\\*|_[^_]+_)")
    var cursor = 0
    token.findAll(source).forEach { match ->
        if (match.range.first > cursor) append(source.substring(cursor, match.range.first))
        val raw = match.value
        when {
            raw.startsWith("**") -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(raw.removePrefix("**").removeSuffix("**"))
                pop()
            }
            raw.startsWith("~~") -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                append(raw.removePrefix("~~").removeSuffix("~~"))
                pop()
            }
            raw.startsWith('`') -> {
                pushStyle(SpanStyle(fontFamily = PunlaMono, background = codeBackground))
                append(raw.removePrefix("`").removeSuffix("`"))
                pop()
            }
            raw.startsWith('*') || raw.startsWith('_') -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(raw.substring(1, raw.length - 1))
                pop()
            }
            else -> append(raw)
        }
        cursor = match.range.last + 1
    }
    if (cursor < source.length) append(source.substring(cursor))
}

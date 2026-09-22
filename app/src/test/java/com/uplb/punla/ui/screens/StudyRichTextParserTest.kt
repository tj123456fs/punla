package com.uplb.punla.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyRichTextParserTest {
    @Test
    fun parsesCommonReviewerFormatting() {
        val blocks = parseStudyDocument(
            """
            # Big idea

            A **bold** paragraph that wraps
            onto the next source line.

            - first point
            2. second point
            > remember this
            ---
            """.trimIndent()
        )

        assertTrue(blocks[0] is StudyDocBlock.Heading)
        assertEquals(
            "A **bold** paragraph that wraps onto the next source line.",
            (blocks[1] as StudyDocBlock.Paragraph).text
        )
        assertTrue(blocks.any { it is StudyDocBlock.Bullet })
        assertTrue(blocks.any { it is StudyDocBlock.Numbered })
        assertTrue(blocks.any { it is StudyDocBlock.Quote })
        assertTrue(blocks.any { it is StudyDocBlock.Divider })
    }

    @Test
    fun parsesMarkdownTableAndCodeFence() {
        val blocks = parseStudyDocument(
            """
            | Variable | Meaning |
            | --- | --- |
            | v | velocity |

            ```
            v = d / t
            ```
            """.trimIndent()
        )

        val table = blocks.first() as StudyDocBlock.Table
        assertEquals(listOf("Variable", "Meaning"), table.headers)
        assertEquals(listOf("v", "velocity"), table.rows.first())
        assertEquals("v = d / t", (blocks.last() as StudyDocBlock.Code).text)
    }
}

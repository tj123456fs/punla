package com.uplb.punla.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyMathTextTest {
    @Test fun ordinaryReviewerTextIsUnchanged() {
        assertEquals("Read Chapter 3.", StudyMathText.render("Read Chapter 3."))
        assertEquals("", StudyMathText.render(""))
    }

    @Test fun rendersSupportedMathTogether() {
        assertEquals(
            "√(9) = 3; 1⁄2 × x² ± H₂ · 4",
            StudyMathText.render("\\sqrt{9} = 3; \\frac{1}{2} \\times x^{2} \\pm H_{2} \\cdot 4")
        )
    }

    @Test fun preservesIncompleteAndUnsupportedMarkup() {
        val source = "\\sqrt{ \\frac{1} x^{ H_{} {notes} \\unknown{a}"
        assertEquals(source, StudyMathText.render(source))
    }

    @Test fun replacementContentsAreLiteral() {
        assertEquals("√(\$1) and a⁄\$2", StudyMathText.render("\\sqrt{\$1} and \\frac{a}{\$2}"))
    }
}

package com.uplb.punla.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uplb.punla.data.entity.ClozeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on Android's ICU regex engine; desktop java.util.regex misses this crash. */
@RunWith(AndroidJUnit4::class)
class StudyRenderingAndroidTest {
    @Test fun plainReviewerTextDoesNotCrash() {
        assertEquals("Read Chapter 3.", StudyMathText.render("Read Chapter 3."))
        assertEquals("", StudyMathText.render(""))
    }

    @Test fun rootsFractionsAndScriptsRenderOnAndroid() {
        assertEquals(
            "√(9) = 3; 1⁄2 × x² ± H₂ · 4",
            StudyMathText.render("\\sqrt{9} = 3; \\frac{1}{2} \\times x^{2} \\pm H_{2} \\cdot 4")
        )
    }

    @Test fun malformedMarkupRemainsReadable() {
        val source = "\\sqrt{ \\frac{1} x^{ H_{} {notes} \\unknown{a}"
        assertEquals(source, StudyMathText.render(source))
    }

    @Test fun clozeCardsMaskAndRevealMultipleAnswers() {
        val source = "{{ Water }} becomes {{steam}}."
        assertTrue(ClozeText.hasCloze(source))
        assertEquals(listOf("Water", "steam"), ClozeText.answers(source))
        assertEquals("[•••••] becomes [•••••].", ClozeText.question(source))
        assertEquals("Water becomes steam.", ClozeText.revealed(source))
    }

    @Test fun incompleteClozeDoesNotCrashOrHideText() {
        val source = "Plain {text}, {{unfinished and {{}}"
        assertFalse(ClozeText.hasCloze(source))
        assertEquals(source, ClozeText.question(source))
        assertEquals(source, ClozeText.revealed(source))
        assertEquals(emptyList<String>(), ClozeText.answers(source))
    }
}

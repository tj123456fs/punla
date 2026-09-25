package com.uplb.punla.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAssistantParsingTest {
    @Test
    fun keywordMatchingUsesTokenBoundaries() {
        assertTrue(containsAssistantKeyword("classes tomorrow", "class", "classes"))
        assertFalse(containsAssistantKeyword("classical mechanics", "class", "classes"))
        assertFalse(containsAssistantKeyword("budgeting notes", "budget"))
    }

    @Test
    fun expenseAmountPrefersCurrencyMarker() {
        assertEquals(120.0, parseAssistantExpenseAmount("add 2 meals php 120") ?: error("missing amount"), 0.0)
        assertEquals(85.5, parseAssistantExpenseAmount("add expense ₱85.50 for lunch") ?: error("missing amount"), 0.0)
    }

    @Test
    fun expenseAmountFallsBackToFirstNumberAfterAdd() {
        assertEquals(75.0, parseAssistantExpenseAmount("yesterday 12 add 75 expense for fare") ?: error("missing amount"), 0.0)
        assertNull(parseAssistantExpenseAmount("expense 75 without an add command"))
    }
}

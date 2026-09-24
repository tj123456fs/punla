package com.uplb.punla.ui.screens

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.uplb.punla.PunlaApp
import com.uplb.punla.context.*
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ui.theme.PunlaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class UiFoundationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "ui-qa/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun primaryNavigationAndCaptureReachExistingDestinations() {
        lateinit var vm: PunlaViewModel
        compose.activityRule.scenario.onActivity { activity ->
            vm = ViewModelProvider(activity, ViewModelProvider.AndroidViewModelFactory.getInstance(activity.application))[PunlaViewModel::class.java]
        }
        compose.setContent { PunlaTheme(darkTheme = false) { PunlaApp(vm) } }
        compose.onNodeWithTag("nav:dashboard").assertIsSelected()
        screenshot("today-app")
        compose.onNodeWithText("Capture").performClick()
        compose.onNodeWithTag("capture-input").performTextInput("Review MATH 27 tomorrow")
        screenshot("capture-app")
        compose.onNodeWithText("Save to Inbox").performClick()
        compose.waitUntil(10000) { vm.studentOs.state.value.captures.any { it.text == "Review MATH 27 tomorrow" } }
        compose.onNodeWithText("Review", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Review & convert").assertExists()
        compose.onNodeWithText("Agenda").performClick()
        screenshot("plan-app")
        compose.onNodeWithTag("nav:study").performClick()
        compose.onNodeWithTag("nav:study").assertIsSelected()
        compose.onNodeWithTag("nav:more").performClick()
        compose.onNodeWithText("Schedule", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Schedule", useUnmergedTree = true).assertExists()
    }

    @Test fun capturePreservesDraftOnFailureAndDisablesDuplicateSave() {
        var saving by mutableStateOf(false)
        var error by mutableStateOf<String?>(null)
        var draft by mutableStateOf("")
        var saves = 0
        compose.setContent { PunlaTheme(darkTheme = false) {
            QuickCaptureSheet(draft, { draft = it }, saving, error, listOf("MATH 27"),
                onSave = { saves++; saving = true }, onDismiss = {}, onStructuredAdd = {})
        } }
        compose.onNodeWithText("Save to Inbox").assertIsNotEnabled()
        compose.onNodeWithTag("capture-input").performTextInput("Read notes")
        compose.onNodeWithText("Save to Inbox").performClick()
        compose.onNodeWithText("Saving…").assertIsNotEnabled()
        compose.runOnIdle { saving = false; error = "Could not save. Try again." }
        compose.onNodeWithTag("capture-input").assertTextContains("Read notes")
        compose.onNodeWithTag("capture-error").assertExists()
        assertEquals(1, saves)
    }

    @Test fun departureActionRemainsReadableAtLargeTextInDarkMode() {
        var openedMap = false
        val next = ClassContext("class1", "PHYS 51", "Physics", "A", "lec", "PH A-1", "2026-09-24", "11:00", "12:00", 0L, 0L)
        val state = StudentState(generatedAtEpochMillis = 0L, localDate = "2026-09-24", localTime = "10:50", day = "Thu",
            nextClass = next, freeMinutesBeforeNextCommitment = 10, travelBufferMinutes = 10, usableFreeMinutes = 0)
        compose.setContent { PunlaTheme(darkTheme = true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    TodayOverviewCard(state, "Study calculus", "Due today", "MATH 27", {}, {}, {}, { openedMap = true }, {})
                } }
            }
        } }
        compose.onNodeWithTag("today-primary").assertIsDisplayed().performClick()
        assertTrue(openedMap)
        compose.onNodeWithText("Start focus").assertDoesNotExist()
        screenshot("today-dark-large-text")
    }
}

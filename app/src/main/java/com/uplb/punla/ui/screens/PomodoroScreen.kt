package com.uplb.punla.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import com.uplb.punla.ui.PunlaViewModel
import com.uplb.punla.ui.pomodoro.PomodoroPhase
import com.uplb.punla.ui.theme.LocalPunlaPalette
import com.uplb.punla.ui.theme.PunlaDisplay
import com.uplb.punla.ui.theme.PunlaMono

/**
 * Roadmap Pomodoro 1.5 — the focus-timer screen. Reached from the Dashboard
 * card (1.6) or the "Focus" drawer item. All timer state/logic lives in
 * [PunlaViewModel] (see 1.3); this screen is purely a view over
 * `vm.pomodoroState`. [onOpenAnalysis] threads through to the Study
 * Analysis screen (roadmap 3) — a drill-down from the timer, not a
 * top-level destination, so it's a history icon here rather than a drawer
 * item.
 */
@Composable
fun PomodoroScreen(vm: PunlaViewModel, preselectedCourse: String? = null, onOpenAnalysis: () -> Unit = {}) {
    val state = vm.pomodoroState
    val classes by vm.classes.collectAsState()
    val haptics = LocalHapticFeedback.current
    val palette = LocalPunlaPalette.current

    // Free-Time Study Suggestions (Phase 1) — a Dashboard suggestion deep-
    // links here with a course already picked. Only applied once, and only
    // while idle, so it can't stomp on a session already in progress.
    LaunchedEffect(preselectedCourse) {
        if (!preselectedCourse.isNullOrBlank() && state.phase == PomodoroPhase.IDLE) {
            vm.setPomodoroCourse(preselectedCourse)
        }
    }

    val courseCodes = remember(classes) { classes.map { it.code }.distinct() }
    // Include the preselected/current course even if it has no matching
    // class row (a deadline's course string doesn't have to match one).
    val courseOptions = remember(courseCodes, state.courseCode) {
        listOf("No course") + (courseCodes + listOfNotNull(state.courseCode)).distinct()
    }
    val selectedLabel = state.courseCode ?: "No course"

    val phaseColor = when (state.phase) {
        PomodoroPhase.WORK -> palette.leaf
        PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> palette.mango
        PomodoroPhase.IDLE -> palette.leaf
    }
    val phaseLabel = when (state.phase) {
        PomodoroPhase.WORK -> "Focus"
        PomodoroPhase.SHORT_BREAK -> "Short break"
        PomodoroPhase.LONG_BREAK -> "Long break"
        PomodoroPhase.IDLE -> "Ready when you are"
    }

    val screenGutter = punlaScreenHorizontalPadding(maxContentWidth = 680.dp, compactPadding = 20.dp)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = screenGutter, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // History icon — drills into Study Analysis (roadmap 3).
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onOpenAnalysis) {
                Icon(
                    Icons.Default.History,
                    contentDescription = "Study history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Course picker — only editable while idle, so a session's tag can't
        // shift mid-run.
        PunlaDropdownField(
            "Studying for",
            selectedLabel,
            courseOptions,
            onSelect = { i -> if (state.phase == PomodoroPhase.IDLE) vm.setPomodoroCourse(courseOptions[i].takeIf { it != "No course" }) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(28.dp))

        // Big countdown ring
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
            val stagedSeconds = when (state.phase) {
                PomodoroPhase.WORK -> vm.pomodoroWorkMinutes * 60
                PomodoroPhase.SHORT_BREAK -> vm.pomodoroShortBreakMinutes * 60
                PomodoroPhase.LONG_BREAK -> vm.pomodoroLongBreakMinutes * 60
                PomodoroPhase.IDLE -> vm.pomodoroWorkMinutes * 60
            }
            val shownSeconds = if (!state.isRunning && state.remainingSeconds == 0 && state.phase != PomodoroPhase.IDLE) {
                stagedSeconds
            } else {
                state.remainingSeconds
            }
            val progress = if (state.totalSecondsForPhase > 0) {
                shownSeconds.toFloat() / state.totalSecondsForPhase.toFloat()
            } else 1f
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 10.dp,
                color = phaseColor,
                trackColor = phaseColor.copy(alpha = 0.15f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val minutes = shownSeconds / 60
                val seconds = shownSeconds % 60
                val display = if (state.phase == PomodoroPhase.IDLE) {
                    "%02d:00".format(vm.pomodoroWorkMinutes)
                } else {
                    "%02d:%02d".format(minutes, seconds)
                }
                Text(
                    display,
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = PunlaMono, fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    phaseLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = PunlaDisplay),
                    color = phaseColor
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Cycle dots — progress toward the next long break.
        val cycles = vm.pomodoroCyclesBeforeLongBreak.coerceAtLeast(1)
        val filled = if (cycles == 0) 0 else state.cycleCount % cycles
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(cycles) { i ->
                Box(
                    Modifier
                        .size(9.dp)
                        .background(
                            if (i < filled) palette.leaf else palette.leaf.copy(alpha = 0.2f),
                            CircleShape
                        )
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Controls
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.phase == PomodoroPhase.IDLE -> {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.startPomodoroWork(state.courseCode)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.paper)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Start focus", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
                !state.isRunning && state.remainingSeconds == 0 -> {
                    // A phase just completed and auto-start is off — offer to begin the next one.
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.startStagedPhase()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.paper)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Start $phaseLabel", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
                state.isRunning -> {
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.pausePomodoro()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pause", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    }
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.stopPomodoro()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.danger, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Stop", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.resumePomodoro()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.paper)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Resume", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.stopPomodoro()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Stop", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Durations are editable in Settings \u2192 Pomodoro.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

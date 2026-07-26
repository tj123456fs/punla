package com.uplb.punla.ui.pomodoro

enum class PomodoroPhase { IDLE, WORK, SHORT_BREAK, LONG_BREAK }

data class PomodoroUiState(
    val phase: PomodoroPhase = PomodoroPhase.IDLE,
    val remainingSeconds: Int = 0,
    val totalSecondsForPhase: Int = 0,
    val isRunning: Boolean = false,
    val cycleCount: Int = 0,          // completed WORK phases this run
    val courseCode: String? = null
)

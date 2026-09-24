# Session 34e — Study Flow Debug Hardening

This pass focuses on runtime safety in Study System 3.1 after real-device testing.

## Fixed

### Flashcard navigation race
`FlashcardsScreen` used `selectedDeck!!` inside `Crossfade`. Compose keeps outgoing content alive briefly, so clearing the selected deck could recompose that old branch with a null deck. Both study and deck-detail branches now snapshot and null-check the active deck.

### Quiz save survives navigation
Completed attempts are now launched in `PunlaViewModel.viewModelScope`. Leaving the result screen, switching tabs, or a Compose animation no longer owns/cancels the database write.

### Idempotent attempt persistence
`QuizAttempt.id` now acts as an idempotency key. The save transaction checks for an existing attempt before applying answer results, mistake history, and goal progress. This prevents duplicate side effects if a finished result screen is recreated while a save is in flight.

## Validation
- No force unwraps remain in QuizScreen, FlashcardsScreen, or StudyHubScreen.
- Changed Kotlin files have balanced structural delimiters.
- JSON examples remain parseable.
- Full Android compilation still belongs to GitHub Actions because this source archive does not carry the Gradle wrapper executable/JAR and the local container has no Android SDK.

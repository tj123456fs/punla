# Punla Session 33b — Study System 3.0 Build Fix

Version: 2.7.1 (`versionCode 19`)

This patch fixes the Kotlin compilation errors surfaced by the first GitHub Actions build of Session 33:

- `DashboardScreen.kt`: imports AutoMirrored `ArrowForward` used by the Study Now card.
- `FlashcardsScreen.kt`: imports Material `Image` used by image flashcards.
- `QuizScreen.kt`: imports the `height` layout modifier used by quiz feedback/results.
- `QuizScreen.kt`: makes all `initial.correctAnswer` reads null-safe in `QuestionEditorDialog`.

No database schema change was made. Room remains version 11.

# Quiz Maker Implementation

## Architecture

Quiz Maker is an offline Room-backed study feature surfaced as a drawer destination (`quizzes`). It shares the existing `PunlaViewModel` and backup system with Flashcards.

### Room entities

- `Quiz`
  - title/course/description
  - passing score
  - shuffle question/choice preferences
- `QuizQuestion`
  - MCQ / True-False / Identification
  - prompt
  - JSON-encoded choices
  - answer text
  - explanation
  - tags
- `QuizAttempt`
  - score / total
  - start/end timestamps
  - duration
  - missed-question IDs

Room migration 9→10 creates all quiz tables and their foreign-key indices.

## Quiz creation

A quiz can be created:

1. Manually.
2. From a flashcard deck.
3. From strict `punla.quiz` JSON.

Flashcard-to-quiz generation creates identification questions. Cloze cards use their hidden term(s) as the answer and their revealed sentence/back content as explanation context.

## Taking a quiz

- Optional question shuffle.
- Optional MCQ choice shuffle.
- One question at a time.
- Immediate correctness feedback.
- Explanation shown after checking when available.
- Final score and percentage.
- Passing threshold comparison.
- Attempt persistence.
- Retry only missed questions.
- Convert missed questions into a new starred flashcard deck.

## JSON safety

Quiz JSON requires:

- `punlaFileId = punla.quiz`
- supported `schemaVersion`
- UUID `contentId`

A flashcard or backup JSON is rejected before question parsing begins. Successfully imported `(fileType, contentId)` pairs are recorded in `json_import_records` for re-import warnings.

## Backup

Backup v6 persists quizzes, questions, attempts, and JSON-import records. Quiz JSON imports never accept external attempt/score history.

# Punla Study System 2.0 — Flashcards + Quiz Maker

## Release

Punla v2.4 (`versionCode 15`), Session 30.

## Flashcards 2.0

Flashcards keep the existing deck/card and spaced-repetition model, then add:

- **Cloze cards** using `{{answer}}` markup.
- **Reverse-direction study** for basic cards. A reverse-enabled card alternates front → back and back → front between reviews while still updating one spaced-repetition record.
- **Tags** stored as normalized comma-separated text and searchable together with front/back content.
- **Starred cards** with one-tap star/unstar from the card list.
- **Smart Study** shortcuts:
  - Due
  - Weak (`reviewCount > 0 && mastery <= 1`)
  - New
  - Starred
  - All
- **JSON export** from every deck.
- **Strict JSON import** with a Punla file-type ID and content UUID.
- **Exact front/back duplicate skipping** when importing into an existing deck.
- **Re-import warning** when the same JSON `contentId` was previously imported.

Imported flashcards always begin with clean review state. External JSON cannot set mastery, due dates, review counts, or Punla database IDs.

## Quiz Maker

A new drawer destination, **Quizzes**, supports:

- Manual quiz creation/editing/deletion.
- Manual question creation/editing/deletion.
- Question types:
  - Multiple choice
  - True / False
  - Identification / typed answer
- Configurable passing score.
- Optional question and choice shuffling.
- Immediate answer checking and explanations.
- Attempt score, percentage, duration, and missed-question tracking.
- Recent attempt history.
- **Retry mistakes** from the most recent attempt or immediately after a quiz.
- **Make flashcards from mistakes**, creating a starred flashcard deck for spaced repetition.
- **Create quiz from flashcards**, producing identification questions from a selected deck.
- Quiz JSON import and export.
- Full quiz/attempt backup and restore.

Identification matching is currently case-insensitive and collapses repeated whitespace, but otherwise expects the same answer text.

## JSON type safety

Every new Punla interchange JSON declares all of the following:

```json
{
  "punlaFileId": "...",
  "schemaVersion": 1,
  "contentId": "UUID"
}
```

Current file IDs:

| Purpose | `punlaFileId` |
| --- | --- |
| Flashcard deck | `punla.flashcards.deck` |
| Quiz | `punla.quiz` |
| Full Punla backup | `punla.backup` |

The Flashcards importer refuses a quiz/backup JSON. The Quizzes importer refuses a flashcard/backup JSON. The backup importer also recognizes the declared Punla file type and refuses interchange files.

`contentId` must be a valid UUID. After a flashcard or quiz JSON is imported, Punla records its `(punlaFileId, contentId)` pair in `json_import_records`. Selecting the same generated content again produces an explicit re-import warning.

The file ID answers **“what kind of Punla JSON is this?”** while the content ID answers **“have I already imported this exact content?”**.

## Room database

Database version: **10**.

Migration `9 → 10`:

- Adds to `flashcards`:
  - `tags`
  - `starred`
  - `reverseEnabled`
  - `cardType`
- Creates:
  - `quizzes`
  - `quiz_questions`
  - `quiz_attempts`
  - `json_import_records`

Existing flashcard decks and review progress remain intact. New flashcard columns receive safe defaults.

## Backup

Backup format version is now 6. New backups include:

- `punlaFileId = punla.backup`
- backup `contentId`
- upgraded flashcard fields
- quizzes
- quiz questions
- quiz attempts
- JSON import records

Older Punla backups without a declared `punlaFileId` remain accepted through the existing shape validation for backward compatibility.

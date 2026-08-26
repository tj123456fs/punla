# Punla Flashcard JSON Format v2

Punla v2.4 uses strict type-safe JSON for flashcard interchange.

## Required envelope

```json
{
  "punlaFileId": "punla.flashcards.deck",
  "schemaVersion": 2,
  "contentId": "550e8400-e29b-41d4-a716-446655440000",
  "deck": {
    "name": "AGRI 31 — Photosynthesis",
    "courseCode": "AGRI 31",
    "description": "Lecture review"
  },
  "cards": []
}
```

`contentId` must be a unique UUID for the deck/content being generated. ChatGPT-generated files should generate a fresh UUID instead of reusing an ID from another deck.

A quiz JSON (`punla.quiz`) or backup JSON (`punla.backup`) is rejected by the flashcard importer rather than being guessed from its fields.

## Card object

```json
{
  "front": "Photosynthesis occurs in the {{chloroplast}}.",
  "back": "Chloroplasts contain the photosynthetic machinery.",
  "hint": "An organelle",
  "tags": ["Photosynthesis", "Lecture 3"],
  "starred": true,
  "reverseEnabled": false,
  "cardType": "CLOZE"
}
```

Supported `cardType` values:

- `BASIC` — normal front/back card.
- `CLOZE` — front should contain at least one `{{answer}}` marker.

For basic cards, `reverseEnabled: true` alternates the study direction between reviews.

`tags` may be a JSON string array or a comma-separated string.

For compatibility, the importer still recognizes common field aliases such as `question`/`answer`, `prompt`/`response`, and `term`/`definition` **inside a correctly identified Punla flashcard file**.

## Security / review-state rule

The importer deliberately ignores externally supplied card IDs, mastery, review counts, due dates, and previous review timestamps. Imported cards start as new Punla reviews.

## Duplicate handling

Punla checks two kinds of duplicates:

1. `contentId` previously imported → Punla shows a re-import warning.
2. Importing into an existing deck with the same front and back → the exact duplicate card is skipped.

## Limits

- About 4 MB per JSON file.
- Up to 5,000 flashcards per import.

See `PUNLA_FLASHCARD_JSON_EXAMPLE.json` for a complete file.

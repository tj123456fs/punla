# Punla Quiz JSON Format v1

## Required envelope

```json
{
  "punlaFileId": "punla.quiz",
  "schemaVersion": 1,
  "contentId": "550e8400-e29b-41d4-a716-446655440000",
  "quiz": {
    "title": "AGRI 31 — Photosynthesis Quiz",
    "courseCode": "AGRI 31",
    "description": "Practice quiz",
    "passingScore": 70,
    "shuffleQuestions": true,
    "shuffleChoices": true
  },
  "questions": []
}
```

`contentId` must be a unique UUID. The Quizzes importer rejects any file whose `punlaFileId` is not exactly `punla.quiz`.

## Multiple choice

```json
{
  "type": "multiple_choice",
  "question": "Where do the light-dependent reactions occur?",
  "choices": ["Stroma", "Thylakoid membrane", "Cytoplasm", "Nucleus"],
  "correctAnswer": "Thylakoid membrane",
  "explanation": "The photosystems and electron transport chain are located in the thylakoid membrane.",
  "tags": ["Photosynthesis"]
}
```

`answer` is accepted as an alias for `correctAnswer`. For compatibility, a numeric answer is treated as a zero-based choice index, and letters `A` through `H` are accepted when they point to an existing choice. Using the exact answer text is preferred because it is unambiguous.

## True / False

```json
{
  "type": "true_false",
  "question": "The Calvin cycle directly consumes light photons.",
  "correctAnswer": false,
  "explanation": "It uses ATP and NADPH produced by the light-dependent reactions."
}
```

## Identification

```json
{
  "type": "identification",
  "question": "What organelle is the primary site of photosynthesis in plant cells?",
  "correctAnswer": "Chloroplast",
  "explanation": "Photosynthesis takes place in chloroplasts."
}
```

Identification checking ignores capitalization and repeated whitespace.

## Import safety

Punla imports question content only. JSON cannot inject scores, attempt history, or existing Punla question/database IDs.

A previously imported `contentId` triggers a warning before importing another copy.

## Limits

- About 4 MB per file.
- Up to 1,000 questions per import.

See `PUNLA_QUIZ_JSON_EXAMPLE.json` for a complete file.

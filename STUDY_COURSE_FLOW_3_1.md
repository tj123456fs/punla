# Punla Course Learning Path — Study System 3.1

**App version:** 2.8.0  
**Database:** 12  
**Study-pack schema:** 1 (backward compatible)

## Universal course flow

Every course uses the same learner-facing sequence:

```text
Course
├─ Module 1 → Review → Flashcards → Quiz
├─ Module 2 → Review → Flashcards → Quiz
├─ ...
└─ Overall Review → Overall Flashcards → Comprehensive Quiz
```

The app recommends the next incomplete stage but never hard-locks later stages. This keeps the flow useful for normal study, catch-up sessions, and cramming.

## Module model

- A top-level `StudyTopic` (`parentTopicId == null`) is a module.
- Descendant topics belong to that module's scope.
- `sortOrder` controls module display order.
- Notes and formulas linked to any topic in the module scope appear in its Review.
- Flashcard decks and quizzes linked to any topic in the module scope appear in its module stages.
- Course-level notes/formulas/decks/quizzes with no topic link are Overall Review material.

## Progress

- Review completion is explicit and stored in `study_review_progress`.
- Flashcards are considered completed for the path after every card in the scope has been reviewed at least once.
- A quiz stage is considered completed after every quiz in the scope has at least one passing attempt.
- These indicators guide the learner only; all stages remain accessible.

## JSON additions

Schema version remains 1. New optional fields are backward compatible:

```json
{
  "topics": [
    {"key": "photosynthesis", "name": "Photosynthesis", "sortOrder": 0}
  ],
  "flashcardDecks": [
    {"name": "Photosynthesis Flashcards", "topicKey": "photosynthesis", "cards": []}
  ],
  "quizzes": [
    {"title": "Photosynthesis Quiz", "topicKey": "photosynthesis", "questions": []}
  ]
}
```

Omit `topicKey` from course-wide material intended for the Overall Review section.

## Migration 11 → 12

- `study_topics.sortOrder`
- `flashcard_decks.topicId` + indexes
- `quizzes.topicId` + indexes
- new `study_review_progress` table

Existing data is preserved. Older decks and quizzes remain course-level until assigned to a module.

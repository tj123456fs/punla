# Flashcard Maker Implementation

## Release

Punla 2.3 (`versionCode 14`)

## Overview

Punla now includes an offline-first Flashcards destination for building and reviewing study decks without leaving the app.

## Features

- Deck library with optional course code and description.
- Manual card creation and editing.
- Bulk card creation using one card per line with either `front :: back` or tab-separated front/back text.
- Search within a deck.
- Card/deck deletion with confirmation.
- Due-card and all-card study modes.
- Tap/reveal study card interaction.
- `Again`, `Hard`, and `Good` self-ratings.
- Lightweight spaced repetition:
  - Again: returns in about 10 minutes and resets mastery.
  - Hard: returns the next day.
  - Good: grows intervals from 1 to 3, 7, 14, and 30 days as mastery increases.
- Deck mastery progress and due counts.
- Per-card review/mastery metadata.
- Backup/restore support.
- JSON deck import from the Android document picker.
- Import preview before saving.
- Canonical `punla-flashcards` v1 interchange format for ChatGPT-generated decks.
- Compatible `question`/`answer`, `prompt`/`response`, and `term`/`definition` aliases.
- Bare JSON card-array import.
- Safe import behavior that ignores external review/mastery metadata.

## Data model

Room database version is now 9.

New entities:

- `FlashcardDeck` (`flashcard_decks`)
- `Flashcard` (`flashcards`)

`Flashcard.deckId` is a foreign key with `ON DELETE CASCADE`, so deleting a deck removes its cards safely.

Migration `8 -> 9` creates both tables and indexes for `deckId` and `dueAt`.

## Navigation

Flashcards is a drawer destination alongside Focus and Assistant. It intentionally does not consume one of Punla's five daily-use bottom-navigation slots.

## Backup

Backup format version is now 5. Exports include `flashcardDecks` and `flashcards`; older backups remain valid because both arrays are optional during import.

## Build fixes bundled

This package also includes the two fixes discovered by the real GitHub Actions build:

- `animateColorAsState` now imports from `androidx.compose.animation`, and the Compose animation artifact is explicitly included.
- `BudgetScreen` safely checks the nullable edit expense with `initialExpense?.ruleId`.

## JSON import

See `FLASHCARD_JSON_IMPORT.md` and `PUNLA_FLASHCARD_JSON_EXAMPLE.json`. The library-level importer creates a new deck; the deck-level importer adds cards to the currently open deck. New-deck imports use a Room transaction.


## Session 30 — Flashcards 2.0

The data model now adds `tags`, `starred`, `reverseEnabled`, and `cardType` to each card through Room migration 9→10. `CLOZE` cards use `{{answer}}` markup; basic cards can alternate reverse direction based on review count. Smart Study filters are derived locally from existing mastery/due/review fields. Flashcard JSON moved to strict envelope v2 with type and UUID identity, and deck export uses the deck UUID as a stable content ID.

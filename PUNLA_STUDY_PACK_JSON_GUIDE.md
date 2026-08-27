# Punla Study Pack JSON Guide

**Target:** Punla Study System 3.1 / Session 34  
**App version:** 2.8.0  
**Study JSON schema:** 1

This guide defines the JSON format used by Punla's **Study Hub** for importing complete study packs.

A study pack can contain:

- course/topic hierarchy
- exam dates and topic priorities
- reviewer notes
- formula references
- flashcard decks
- quizzes
- images/diagrams where supported

Punla intentionally does **not** import fabricated mastery, scores, review history, quiz attempts, mistake history, or other private runtime state from a study pack.

---

# 0. Recommended Course Learning Path

For new complete course packs, Punla recommends organizing content by **module**. Top-level topics become modules. Each module should have a reviewer note, a flashcard deck, and a quiz. After all modules, include course-level material with no `topicKey` for the overall review.

```text
Module → Review → Flashcards → Quiz
(repeat for every module)
Overall Review → Overall Flashcards → Comprehensive Quiz
```

The sequence is guidance only; Punla does not hard-lock later stages.

### Module linking

Use the same topic key on notes, formulas, decks, and quizzes:

```json
{
  "topics": [
    { "key": "module-1", "name": "Module 1", "sortOrder": 0 }
  ],
  "notes": [
    { "title": "Module 1 Review", "topicKey": "module-1", "body": "..." }
  ],
  "flashcardDecks": [
    { "name": "Module 1 Flashcards", "topicKey": "module-1", "cards": [] }
  ],
  "quizzes": [
    { "title": "Module 1 Quiz", "topicKey": "module-1", "questions": [] }
  ]
}
```

For the Overall Review, omit `topicKey` from its note/formulas/deck/quiz.

---

# 1. Punla Study File IDs

Punla uses strict internal file IDs so a JSON file cannot be accidentally opened by the wrong importer.

## Complete study pack

```text
punla.study.bundle
```

Use this when the file may contain:

- topics
- notes
- formulas
- flashcards
- quizzes

## Notes-only study file

```text
punla.study.notes
```

Use this for:

- topics
- reviewer notes
- formulas

When `punlaFileId` is `punla.study.notes`, Punla ignores flashcard decks and quizzes even if they are present.

## Other Punla JSON IDs

```text
punla.flashcards.deck
punla.quiz
punla.study.notes
punla.study.bundle
punla.backup
```

Study packs should be imported from **Study Hub**.

---

# 2. Required JSON Envelope

Every Punla study pack must contain:

```json
{
  "punlaFileId": "punla.study.bundle",
  "schemaVersion": 1,
  "contentId": "1e8104e5-b6db-45cf-9073-e91b80f79ddb"
}
```

## `punlaFileId`

For a full study pack:

```json
"punlaFileId": "punla.study.bundle"
```

For notes/formulas only:

```json
"punlaFileId": "punla.study.notes"
```

## `schemaVersion`

Current Study System 3.1 format:

```json
"schemaVersion": 1
```

## `contentId`

Must be a valid UUID.

Example:

```json
"contentId": "1e8104e5-b6db-45cf-9073-e91b80f79ddb"
```

Generate a **new UUID for every newly generated study pack**.

Punla remembers previously imported content IDs. Re-importing the same `contentId` produces a warning so the user can decide whether another copy is intentional.

---

# 3. Recommended Study Pack Layout

The recommended structure is:

```json
{
  "punlaFileId": "punla.study.bundle",
  "schemaVersion": 1,
  "contentId": "1e8104e5-b6db-45cf-9073-e91b80f79ddb",

  "bundle": {
    "title": "AGRI 31 — Photosynthesis Study Pack",
    "courseCode": "AGRI 31",
    "description": "Reviewer, flashcards, formulas, and practice questions."
  },

  "topics": [],
  "notes": [],
  "formulas": [],
  "flashcardDecks": [],
  "quizzes": []
}
```

Punla also accepts the material arrays inside `bundle` or inside an object named `study`.

For consistency, **top-level material arrays are recommended**.

---

# 4. Bundle Metadata

Inside `bundle`:

```json
{
  "bundle": {
    "title": "AGRI 31 — Photosynthesis Study Pack",
    "courseCode": "AGRI 31",
    "description": "Lecture review and exam preparation."
  }
}
```

Supported aliases:

| Preferred | Also accepted |
|---|---|
| `title` | `name` |
| `courseCode` | `course`, `subject` |
| `description` | `notes` |

If no course is supplied, Punla imports the material under:

```text
General
```

The course code applies to the imported topics, notes, formulas, flashcard decks, and quizzes.

---

# 5. Topics and Topic Hierarchy

Topics let Study Hub organize material by course, unit, chapter, lesson, or concept.

Example:

```json
{
  "topics": [
    {
      "key": "photosynthesis",
      "name": "Photosynthesis",
      "priority": 5
    },
    {
      "key": "light-reactions",
      "name": "Light-dependent Reactions",
      "parentKey": "photosynthesis",
      "priority": 4
    },
    {
      "key": "calvin-cycle",
      "name": "Calvin Cycle",
      "parentKey": "photosynthesis",
      "examDate": "2026-09-15",
      "priority": 5
    }
  ]
}
```

## Topic fields

### `key`

A stable identifier used by notes and formulas to link to the topic.

Preferred:

```json
"key": "calvin-cycle"
```

Aliases:

```text
id
slug
```

Topic keys are **case-insensitive** inside a study pack.

For example:

```text
Photosynthesis
photosynthesis
PHOTOSYNTHESIS
```

refer to the same key.

Duplicate keys are skipped.

### `sortOrder`

Optional non-negative integer controlling module/topic display order. Lower numbers appear first. If omitted, Punla uses import order.

Aliases:

```text
order
```

### `name`

Human-readable topic name.

Aliases:

```text
title
```

### `parentKey`

Links the topic to another imported topic.

Alias:

```text
parent
```

Example:

```json
"parentKey": "photosynthesis"
```

If the parent key does not exist in the pack, Punla warns the user and imports the topic at the top level.

### `examDate`

ISO date:

```text
YYYY-MM-DD
```

Example:

```json
"examDate": "2026-09-15"
```

Alias:

```text
exam
```

Invalid dates are ignored with an import warning.

### `priority`

Integer from:

```text
1 to 5
```

Recommended interpretation:

| Priority | Meaning |
|---:|---|
| 1 | low priority |
| 2 | below normal |
| 3 | normal |
| 4 | important |
| 5 | very important / exam-critical |

Punla clamps values outside the range to 1–5.

Default:

```text
3
```

---

# 6. Reviewer Notes

Notes appear in Study Hub and can later be converted into flashcards or recall questions.

Example:

```json
{
  "notes": [
    {
      "title": "Photosynthesis Overview",
      "topicKey": "photosynthesis",
      "tags": ["Photosynthesis", "Lecture 4"],
      "body": "Photosynthesis converts light energy into chemical energy.\n\nLight reactions :: Reactions occurring in the thylakoid membrane.\n\nThe {{Calvin cycle}} occurs in the stroma."
    }
  ]
}
```

## Note fields

Required:

```text
title
body
```

Aliases:

| Preferred | Also accepted |
|---|---|
| `title` | `name` |
| `body` | `content`, `text` |
| `topicKey` | `topic` |

### `tags`

Can be an array:

```json
"tags": ["Photosynthesis", "Midterm"]
```

or comma-separated text:

```json
"tags": "Photosynthesis, Midterm"
```

Punla normalizes them into its internal comma-separated tag format.

## Reviewer lines that Punla can convert later

Punla can create cards/questions from explicit reviewer pairs:

```text
Question :: Answer
```

Example:

```text
Where do light-dependent reactions occur? :: Thylakoid membrane
```

Punla also recognizes cloze lines:

```text
The {{Calvin cycle}} occurs in the stroma.
```

For clean automatic generation, put one explicit pair or cloze statement per line.

---

# 7. Formula References

Formula references appear in Study Hub's formula/reviewer tools.

Example:

```json
{
  "formulas": [
    {
      "title": "Velocity",
      "topicKey": "kinematics",
      "expression": "v = d / t",
      "variables": "v = velocity; d = displacement; t = time",
      "units": "v: m/s; d: m; t: s",
      "workedExample": "If d = 20 m and t = 4 s, then v = 20/4 = 5 m/s."
    }
  ]
}
```

Required:

```text
title
expression
```

Aliases:

| Preferred | Also accepted |
|---|---|
| `title` | `name` |
| `expression` | `formula` |
| `topicKey` | `topic` |
| `workedExample` | `example` |

Optional:

```text
variables
units
workedExample
```

Punla supports plain text plus its small math/LaTeX-like display subset.

For maximum compatibility, formulas should still be understandable as plain text.

---

# 8. Flashcard Decks Inside a Study Pack

Use:

```json
"flashcardDecks": []
```

Alias:

```text
decks
```

Example:

```json
{
  "flashcardDecks": [
    {
      "name": "Photosynthesis Core",
      "description": "High-yield concepts",
      "cards": [
        {
          "front": "Where do light-dependent reactions occur?",
          "back": "In the thylakoid membranes.",
          "hint": "Inside the chloroplast",
          "tags": ["Photosynthesis", "Light reactions"],
          "starred": true,
          "reverseEnabled": false,
          "cardType": "BASIC"
        }
      ]
    }
  ]
}
```

## Deck fields

Required:

```text
name
cards
```

Aliases:

```text
name → title
description → notes
```

---

# 9. Flashcard Fields

Required card content:

```text
front
back
```

Accepted aliases:

| Preferred | Also accepted |
|---|---|
| `front` | `question`, `prompt`, `term` |
| `back` | `answer`, `response`, `definition` |
| `hint` | `clue` |
| `cardType` | `type` |
| `reverseEnabled` | `reverse` |
| `imageUri` | `image`, `imageUrl` |

## Basic card

```json
{
  "front": "What is ATP?",
  "back": "Adenosine triphosphate.",
  "cardType": "BASIC"
}
```

## Cloze card

```json
{
  "front": "The {{Calvin cycle}} occurs in the stroma.",
  "back": "The Calvin cycle uses ATP and NADPH to support carbon fixation.",
  "cardType": "CLOZE"
}
```

A CLOZE card must actually contain a marker such as:

```text
{{Calvin cycle}}
```

If `cardType` says `CLOZE` but the front has no `{{answer}}` marker, Punla imports it as a Basic card and displays a warning.

## Reverse cards

```json
"reverseEnabled": true
```

This allows Punla to study the card in both directions.

## Starred cards

```json
"starred": true
```

## Images

```json
"imageUri": "https://example.com/diagram.png"
```

Punla also supports persistable Android content URIs selected from the device.

For AI-generated study packs, omit `imageUri` unless a real stable image URL or valid user-provided reference exists.

Do **not** invent image URLs.

## Image occlusion

The importer accepts `occlusion` as an array, object, or encoded JSON array.

Example:

```json
{
  "imageUri": "https://example.com/cell.png",
  "occlusion": [
    {
      "x": 20,
      "y": 30,
      "width": 35,
      "height": 15
    }
  ]
}
```

The exact image must exist for image occlusion to be useful.

---

# 10. Quizzes Inside a Study Pack

Example:

```json
{
  "quizzes": [
    {
      "title": "Photosynthesis Practice",
      "description": "Mixed practice",
      "passingScore": 70,
      "questions": []
    }
  ]
}
```

Required:

```text
title
questions
```

Aliases:

```text
title → name
description → notes
```

`passingScore` is clamped between:

```text
1 and 100
```

Default:

```text
70
```

---

# 11. Supported Quiz Question Types

Study packs support:

```text
multiple_choice
true_false
identification
multi_select
numeric
ordering
matching
image_identification
```

Additional accepted aliases are documented below.

---

# 12. Multiple Choice

Example:

```json
{
  "type": "multiple_choice",
  "question": "Where do the light-dependent reactions occur?",
  "choices": [
    "Stroma",
    "Thylakoid membrane",
    "Cytoplasm",
    "Nucleus"
  ],
  "correctAnswer": "Thylakoid membrane",
  "explanation": "The photosystems and electron transport chain are located in the thylakoid membrane.",
  "tags": ["Photosynthesis"]
}
```

Question/prompt aliases:

```text
question
prompt
stem
```

Choice aliases:

```text
choices
options
items
```

Answer aliases:

```text
correctAnswer
answer
correct
```

## Recommended answer format

Use the exact answer text:

```json
"correctAnswer": "Thylakoid membrane"
```

Punla can also interpret:

- letters `A` through `H`
- a zero-based numeric option index

Example:

```json
"correctAnswer": "B"
```

Exact text is safer and less ambiguous.

Choices must remain unique after Punla normalizes capitalization and whitespace.

---

# 13. True / False

Accepted type names:

```text
true_false
true-false
tf
```

Recommended:

```json
{
  "type": "true_false",
  "question": "The Calvin cycle directly consumes light photons.",
  "correctAnswer": false,
  "explanation": "It uses ATP and NADPH produced by the light-dependent reactions."
}
```

Use real JSON booleans whenever possible:

```json
true
```

or:

```json
false
```

Punla also accepts the strings `"true"` and `"false"`.

---

# 14. Identification / Typed Recall

Accepted types:

```text
identification
short_answer
typed
text
```

Recommended:

```json
{
  "type": "identification",
  "question": "What organelle is the primary site of photosynthesis in plant cells?",
  "correctAnswer": "Chloroplast",
  "explanation": "Photosynthesis occurs in chloroplasts."
}
```

Keep the expected answer concise and unambiguous.

---

# 15. Multi-Select

Accepted types:

```text
multi_select
multiple_select
select_all
```

Recommended:

```json
{
  "type": "multi_select",
  "question": "Which molecules are produced by the light-dependent reactions?",
  "choices": [
    "ATP",
    "NADPH",
    "Oxygen",
    "Glucose"
  ],
  "correctAnswer": [
    "ATP",
    "NADPH",
    "Oxygen"
  ],
  "explanation": "ATP, NADPH, and oxygen are products of the light-dependent reactions."
}
```

Answers may use:

- an array of exact choice text
- choice letters
- comma-separated text
- pipe-separated text

Recommended:

```json
"correctAnswer": ["ATP", "NADPH", "Oxygen"]
```

Every selected answer must resolve to a real choice. Punla rejects a question rather than silently accepting a partial answer key.

---

# 16. Numeric Questions

Accepted types:

```text
numeric
number
calculation
```

Example:

```json
{
  "type": "numeric",
  "question": "A body travels 20 m in 4 s. What is its average speed in m/s?",
  "correctAnswer": 5,
  "tolerance": 0.01,
  "explanation": "20 ÷ 4 = 5 m/s."
}
```

`correctAnswer` must be parseable as a number.

Optional:

```json
"tolerance": 0.01
```

Tolerance cannot be negative.

Additional metadata may be supplied:

```json
"metadata": {
  "unit": "m/s"
}
```

Punla merges the top-level tolerance into question metadata.

---

# 17. Ordering Questions

Accepted types:

```text
ordering
order
sequence
```

Recommended:

```json
{
  "type": "ordering",
  "question": "Arrange the stages in order.",
  "items": [
    "Light absorption",
    "Electron transport",
    "ATP/NADPH production",
    "Carbon fixation"
  ],
  "correctAnswer": [
    "Light absorption",
    "Electron transport",
    "ATP/NADPH production",
    "Carbon fixation"
  ]
}
```

The answer must contain **every item exactly once**.

Punla also accepts letters such as:

```json
"correctAnswer": ["A", "C", "B", "D"]
```

If an ordering question omits `correctAnswer`, Punla treats the supplied `items` order as the correct order.

Do not use duplicate items.

---

# 18. Matching Questions

Accepted types:

```text
matching
match
```

Recommended:

```json
{
  "type": "matching",
  "question": "Match each structure to its function.",
  "pairs": {
    "Thylakoid membrane": "Light-dependent reactions",
    "Stroma": "Calvin cycle",
    "Chlorophyll": "Light absorption"
  }
}
```

Punla stores matching answers as a JSON object.

At least **two valid pairs** are required.

Avoid duplicate or ambiguous left/right values.

---

# 19. Image / Diagram Identification

Accepted types:

```text
image
image_identification
diagram
```

Example:

```json
{
  "type": "image_identification",
  "question": "Identify the highlighted organelle.",
  "imageUri": "https://example.com/diagram.png",
  "correctAnswer": "Chloroplast",
  "explanation": "The highlighted organelle is a chloroplast."
}
```

Image aliases:

```text
imageUri
image
imageUrl
```

An image-identification question without an image is rejected.

Do not generate fake URLs. Use image questions only when the user supplied an actual image/reference or a stable accessible image exists.

---

# 20. Explanations, Tags, and Metadata

Explanation aliases:

```text
explanation
feedback
rationale
```

Tags can be:

```json
"tags": ["Photosynthesis", "Midterm"]
```

or:

```json
"tags": "Photosynthesis, Midterm"
```

Generic custom metadata:

```json
"metadata": {
  "difficulty": "hard",
  "unit": "m/s"
}
```

Punla keeps supported metadata as JSON for the question.

---

# 21. Import Limits

Current Study System 3.1 limits:

| Content | Limit |
|---|---:|
| JSON file size | about 6 MB |
| Topics | 300 |
| Notes | 500 |
| Formulas | 500 |
| Flashcard decks | 50 |
| Flashcards total | 5,000 |
| Quizzes | 50 |
| Quiz questions total | 2,000 |
| Choices/items per question | 30 |

When possible, generate study packs well below these limits.

For very large courses, separate material by:

- exam
- unit
- chapter
- lecture block

---

# 22. Import Safety

Study Pack importing is intentionally defensive.

Punla:

- validates `punlaFileId`
- validates `schemaVersion`
- requires a real UUID `contentId`
- limits input size
- validates required fields
- skips invalid individual materials when safe
- reports warnings before import
- rejects files with no usable study material
- warns when the same `contentId` was imported before
- performs the actual database import atomically
- generates new internal database IDs
- does not trust external mastery/history values

A complete import either commits through Punla's Room transaction or returns an import error.

---

# 23. What Study Packs Must NOT Contain

Do not fabricate or inject:

```text
mastery
reviewCount
dueAt
quiz attempts
scores
study streaks
mistake history
confidence history
database row IDs
notification history
private app settings
```

Study packs should contain **study content**, not Punla runtime state.

---

# 24. Topic Linking Rules

Notes and formulas can link to an imported topic:

```json
"topicKey": "calvin-cycle"
```

That key should match:

```json
{
  "key": "calvin-cycle",
  "name": "Calvin Cycle"
}
```

Topic references are case-insensitive.

If a note/formula refers to a topic key that does not exist, it will not receive a valid topic link.

Best practice:

1. define topics first
2. use short stable keys
3. reuse those keys exactly
4. avoid spaces in keys where possible

Good:

```text
photosynthesis
light-reactions
calvin-cycle
```

---

# 25. Complete Example Study Pack

```json
{
  "punlaFileId": "punla.study.bundle",
  "schemaVersion": 1,
  "contentId": "2ba7bb02-3df2-4e1d-a602-e3f882bd0cc1",

  "bundle": {
    "title": "AGRI 31 — Photosynthesis Study Pack",
    "courseCode": "AGRI 31",
    "description": "Compact reviewer, flashcards, formulas, and mixed practice."
  },

  "topics": [
    {
      "key": "photosynthesis",
      "name": "Photosynthesis",
      "priority": 5
    },
    {
      "key": "light-reactions",
      "name": "Light-dependent Reactions",
      "parentKey": "photosynthesis",
      "priority": 4
    },
    {
      "key": "calvin-cycle",
      "name": "Calvin Cycle",
      "parentKey": "photosynthesis",
      "priority": 5
    }
  ],

  "notes": [
    {
      "title": "Photosynthesis Quick Reviewer",
      "topicKey": "photosynthesis",
      "tags": ["Photosynthesis", "Reviewer"],
      "body": "Photosynthesis converts light energy into chemical energy.\n\nWhere do light-dependent reactions occur? :: Thylakoid membrane\n\nThe {{Calvin cycle}} occurs in the stroma."
    }
  ],

  "formulas": [
    {
      "title": "Photosynthesis summary",
      "topicKey": "photosynthesis",
      "expression": "6 CO2 + 6 H2O + light → C6H12O6 + 6 O2",
      "variables": "CO2 = carbon dioxide; H2O = water; O2 = oxygen",
      "units": "Stoichiometric molecular ratio",
      "workedExample": "Six carbon dioxide molecules and six water molecules are represented in the summarized equation."
    }
  ],

  "flashcardDecks": [
    {
      "name": "Photosynthesis Core",
      "description": "High-yield recall cards",
      "cards": [
        {
          "front": "Where do light-dependent reactions occur?",
          "back": "Thylakoid membrane",
          "hint": "Inside the chloroplast",
          "tags": ["Light reactions"],
          "starred": true,
          "reverseEnabled": false,
          "cardType": "BASIC"
        },
        {
          "front": "The {{Calvin cycle}} occurs in the stroma.",
          "back": "The Calvin cycle uses ATP and NADPH in carbon fixation.",
          "tags": ["Calvin cycle"],
          "cardType": "CLOZE"
        }
      ]
    }
  ],

  "quizzes": [
    {
      "title": "Photosynthesis Practice",
      "description": "Mixed recall practice",
      "passingScore": 70,
      "questions": [
        {
          "type": "multiple_choice",
          "question": "Where do the light-dependent reactions occur?",
          "choices": [
            "Stroma",
            "Thylakoid membrane",
            "Cytoplasm",
            "Nucleus"
          ],
          "correctAnswer": "Thylakoid membrane",
          "explanation": "The photosystems and electron transport chain are located in the thylakoid membrane.",
          "tags": ["Light reactions"]
        },
        {
          "type": "true_false",
          "question": "The Calvin cycle directly consumes light photons.",
          "correctAnswer": false,
          "explanation": "The Calvin cycle uses ATP and NADPH produced by the light-dependent reactions.",
          "tags": ["Calvin cycle"]
        },
        {
          "type": "identification",
          "question": "What organelle is the primary site of photosynthesis in plant cells?",
          "correctAnswer": "Chloroplast",
          "tags": ["Photosynthesis"]
        },
        {
          "type": "multi_select",
          "question": "Which are products of the light-dependent reactions?",
          "choices": [
            "ATP",
            "NADPH",
            "Oxygen",
            "Glucose"
          ],
          "correctAnswer": [
            "ATP",
            "NADPH",
            "Oxygen"
          ],
          "tags": ["Light reactions"]
        }
      ]
    }
  ]
}
```

---

# 26. Notes-Only Example

Use this when the requested file should contain reviewer material but no decks/quizzes.

```json
{
  "punlaFileId": "punla.study.notes",
  "schemaVersion": 1,
  "contentId": "a817030e-07e0-4f58-878f-69a47e5d353c",

  "bundle": {
    "title": "PHYS 51 — Formula Reviewer",
    "courseCode": "PHYS 51"
  },

  "topics": [
    {
      "key": "kinematics",
      "name": "Kinematics",
      "priority": 5
    }
  ],

  "notes": [
    {
      "title": "Kinematics Reviewer",
      "topicKey": "kinematics",
      "body": "Average speed :: Total distance divided by total time."
    }
  ],

  "formulas": [
    {
      "title": "Average speed",
      "topicKey": "kinematics",
      "expression": "v = d / t",
      "variables": "v = speed; d = distance; t = time",
      "units": "m/s"
    }
  ]
}
```

---

# 27. Recommended File Names

Examples:

```text
AGRI31_Photosynthesis_StudyPack.punla.json
PHYS51_Kinematics_StudyPack.punla.json
MATH27_Exam1_StudyPack.punla.json
ABE30_Midterm_StudyPack.punla.json
KAS1_Module2_Reviewer.punla.json
```

The filename does not determine the importer.

The internal `punlaFileId` does.

---

# 28. Prompt ChatGPT to Generate a Study Pack

Use:

> Create a Punla Study Pack JSON file from the material I provide. Use `punlaFileId` = `punla.study.bundle`, `schemaVersion` = `1`, and generate a fresh UUID `contentId`. Preserve the terminology, organization, and facts in my source. Do not invent unsupported information. Organize the material into a sensible topic hierarchy with stable topic keys. Include reviewer notes, useful formulas when the source contains them, high-quality flashcards, and a balanced quiz using only question types that fit the material. Use exact unambiguous answer keys, plausible distractors, concise explanations, and useful tags. Do not include mastery, scores, history, attempts, or Punla database IDs. Validate the finished JSON against the Punla Study Pack schema before giving me the `.json` file.

For exam prep:

> Create a Punla Study Pack JSON for my upcoming exam from these files. Prioritize high-yield concepts, use topic priority 1–5, attach the exam date only when it is explicitly confirmed by the source or by me, include a concise reviewer, formulas where applicable, recall-focused flashcards, and an exam-level mixed quiz. Do not invent dates or facts.

For notes only:

> Create a Punla Study Notes JSON file. Use `punlaFileId` = `punla.study.notes`, `schemaVersion` = `1`, and a fresh UUID `contentId`. Include topic hierarchy, reviewer notes, and formulas only. Do not include flashcard decks or quizzes.

---

# 29. Generator Checklist

Before giving a study pack to the user:

## Envelope

- [ ] Valid JSON
- [ ] Correct `punlaFileId`
- [ ] `schemaVersion` = `1`
- [ ] Fresh valid UUID `contentId`

## Grounding

- [ ] Content comes from the user's requested source
- [ ] No invented facts
- [ ] No invented exam dates
- [ ] Terminology matches the source
- [ ] Unsupported material is omitted rather than guessed

## Topics

- [ ] Topic keys are unique
- [ ] Parent keys point to valid topics
- [ ] Exam dates use `YYYY-MM-DD`
- [ ] Priority values are 1–5

## Notes

- [ ] Every note has a title and body
- [ ] `topicKey` references a valid topic when used
- [ ] Reviewer pairs use `Question :: Answer` when automatic conversion is desired
- [ ] Cloze statements use `{{answer}}`

## Formulas

- [ ] Every formula has title + expression
- [ ] Variables/units are accurate
- [ ] Worked examples are source-supported or independently solved correctly

## Flashcards

- [ ] Every card has front + back
- [ ] CLOZE cards contain a real `{{answer}}`
- [ ] No mastery/review history is fabricated
- [ ] Images are real references, never invented URLs

## Quizzes

- [ ] Every question has one defensible answer key
- [ ] MCQ choices are unique
- [ ] Multi-select key contains only real options
- [ ] Ordering key is a complete permutation
- [ ] Matching has at least two valid pairs
- [ ] Numeric answers/tolerance are valid
- [ ] Image questions contain a real image reference
- [ ] Explanations agree with the answer key

## Limits

- [ ] Under ~6 MB
- [ ] ≤300 topics
- [ ] ≤500 notes
- [ ] ≤500 formulas
- [ ] ≤50 flashcard decks
- [ ] ≤5,000 flashcards total
- [ ] ≤50 quizzes
- [ ] ≤2,000 quiz questions total

---

# 30. Quick Reference

```text
FULL STUDY PACK
punlaFileId: punla.study.bundle
schemaVersion: 1

NOTES / FORMULAS ONLY
punlaFileId: punla.study.notes
schemaVersion: 1

REQUIRED ENVELOPE
punlaFileId
schemaVersion
contentId (UUID)

SUPPORTED MATERIAL
topics
notes / reviewers
formulas / formulaReferences
flashcardDecks / decks
quizzes

QUIZ TYPES
multiple_choice
true_false
identification
multi_select
numeric
ordering
matching
image_identification

LIMITS
~6 MB
300 topics
500 notes
500 formulas
50 decks
5,000 cards
50 quizzes
2,000 questions

IMPORT DESTINATION
Study Hub
```

---

# 31. Design Principle

A Punla Study Pack should be a **clean interchange format for academic content**.

It should answer:

- What course is this?
- What topics does it cover?
- How are the topics related?
- What should the student review?
- What formulas/references matter?
- What should the student recall with flashcards?
- What should the student practice in quizzes?

It should **not** pretend to know what the student has mastered before Punla has actually observed their study activity.


# Study System 3.1 Module Associations

## Flashcard deck `topicKey`

A deck may include:

```json
"topicKey": "module-1"
```

This attaches the entire deck to that module. Aliases `topic` and `module` are also accepted. Omit it for Overall Flashcards.

## Quiz `topicKey`

A quiz may include:

```json
"topicKey": "module-1"
```

This attaches the entire quiz to that module. Aliases `topic` and `module` are also accepted. Omit it for the Comprehensive Quiz / course-wide quiz section.

Older schema-1 packs remain valid. Decks and quizzes without `topicKey` import as course-level material.

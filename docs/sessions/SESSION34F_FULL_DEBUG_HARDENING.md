# Session 34f — Full Debug Hardening

**Punla:** v2.8.2 (`versionCode 22`)  
**Database:** Room v12  
**Backup format:** v9

## Scope

Session 34f is a consolidated stability/data-integrity pass over Punla, including Study System 3.1, quizzes, flashcards, backup/restore, budget/recurrence, notifications, Pomodoro, attendance, settings, and navigation.

## Major fixes

- Safe Room `@Upsert` writes replace destructive REPLACE semantics.
- Quiz completion persistence is lifecycle-independent and attempt-idempotent.
- Quiz/flashcard Crossfade back-navigation null crashes are removed.
- Complete backup data is validated before local Room data is cleared.
- Restore validates identifiers, relationships, topic cycles, dates/times, finite numbers, grades, attendance, quiz/study records, recurrence metadata, and preference ranges.
- Backup v9 preserves visual and Pomodoro settings; encrypted API-key preferences are excluded from Android backup/device transfer.
- Budget/grade/study numeric paths reject NaN/Infinity and invalid Float-backed values.
- Recurrence writes are transactional and old/corrupt recurrence cursors are bounded by each rule's declared start date.
- Study Pack module trees reject/break invalid parent links and cycles safely.
- Repeated notification/widget/Quick Add navigation uses request tokens rather than sticky route state.
- Coroutine cancellation is rethrown in async persistence/network paths instead of being displayed as an ordinary failure.

## Validation performed in this environment

- 114 Kotlin source/test files passed a delimiter, string, character, and comment structural scan.
- 13 XML files parsed successfully.
- 4 bundled JSON files parsed successfully.
- Main source contains no `!!` force unwraps.
- Main source contains no `OnConflictStrategy.REPLACE` upserts.
- Representative Room v11 → v12 migration SQL executed successfully against SQLite.
- Manifest/backup configuration checked: encrypted secret preferences are excluded from cloud/device backup and cleartext traffic is disabled.

## Build note

This editing environment does not contain Android SDK 34 or the complete Gradle runtime/dependency cache, so it cannot perform the authoritative Android + KSP build. Push this source to the existing Punla GitHub repository and let the **Assemble debug APK** workflow perform the final compiler/Room-schema check.

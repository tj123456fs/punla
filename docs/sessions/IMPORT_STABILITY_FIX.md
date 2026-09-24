# Punla JSON Import Stability Fix

## Problem

Flashcard/quiz JSON parsing already converted malformed JSON into a readable dialog, but the persistence half of the flow was still fire-and-forget. `PunlaViewModel` launched Room work in `viewModelScope` without returning a result to the screen. If a database, constraint, migration, or storage exception occurred, that coroutine exception could terminate the app. The screen also closed the preview and navigated immediately, before Room had confirmed that the import succeeded.

The document picker also used `readText()`, which buffered the entire selected document before the parser could enforce its 4 MB character limit. A mistakenly selected very large file therefore bypassed the intended memory guard until after allocation.

## Fix

### Bounded file reading

`PunlaJsonImportReader` reads in 8 KiB character chunks and aborts as soon as the configured parser limit would be exceeded. File reading and JSON parsing run on `Dispatchers.IO`.

### Awaited atomic imports

Flashcard deck imports, imports into an existing deck, and quiz imports now return `Result<Unit>`. The UI awaits the result. Room transactions remain atomic, so a failed import leaves no partial deck/cards/questions behind.

### Recoverable errors

Database/import-history exceptions are logged under `PunlaImport` and converted into an in-app error dialog. Coroutine cancellation is rethrown normally.

### Navigation safety

Punla no longer opens the new deck/quiz until the transaction reports success. While a transaction is active, Import/Cancel controls are disabled to prevent overlapping double-tap imports.

## Compatibility

- No database migration is required.
- Existing Flashcard JSON v2 files remain compatible.
- Existing Quiz JSON v1 files remain compatible.
- Existing `contentId` duplicate-detection records remain compatible.
- Existing flashcard and quiz data is unchanged.

## Version

Punla `2.6`, `versionCode 17`.

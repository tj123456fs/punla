# Session 37 — Phase 2A: Today + Review 2.0 + Typography 2.0

## Release
Punla 3.2.0 (`versionCode 35`)

## Phase 2A — Today
Home gains one compact context card driven by the Phase 1 `StudentState`:

- **Now** — current class or usable free time.
- **Next** — next class and room/time.
- **Recommended** — reuses Punla's existing free-slot study suggestion; Session 37 does not introduce a second ranking engine.
- **Later** — nearest deadline in the existing seven-day context window.

This is intentionally one compact card rather than another giant dashboard. Later Phase 2 sessions can simplify older Home widgets once this surface has been validated in normal use.

## Review 2.0
`StudyNote.body` remains stored exactly as before, but the Review reading screen now renders a dependency-free Markdown-ish subset:

- `#`, `##`, `###` headings
- paragraphs with wrapped source lines
- bullets and numbered steps
- `**bold**`, `*italic*`, `_italic_`, `~~strike~~`, and inline code
- `>` callouts / quotes
- horizontal dividers
- fenced code blocks
- simple pipe tables
- existing `StudyMathText` rendering continues to work
- reviewer editor shows a compact formatting cheat-sheet

No study-pack, backup, or Room schema change is required.

## Typography 2.0
- Removes remaining screen/header `PunlaDisplay` hard overrides so the selected font actually reaches headers.
- Keeps intentionally monospaced timer/numeric text unchanged.
- Adds **Playful**: Fraunces with its `SOFT` and `WONK` variable axes enabled for display text, with readable Inter body text.
- Adds **Handwritten**: Android cursive display family with readable Inter body text.
- Top app-bar font changes crossfade instead of snapping.
- Settings includes an animated live header preview.

## Safety / compatibility
- No Room migration.
- No backup schema bump: font choice is already exported/restored by enum name and automatically accepts the new enum values.
- No new permission, worker, receiver, alarm, network API, or font download.
- Existing bundled font files are reused; no font assets are distributed by this patch.

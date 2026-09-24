# Punla UI/UX improvement plan

Research date: 2026-09-23. These are recommendations, not UI changes shipped in 3.5.1.

## Implemented foundation — 3.6.0

The first implementation is merged in [PR #3](https://github.com/tj123456fs/punla/pull/3): Today hierarchy, four primary destinations, top-bar capture, an Inbox focused on review, a dated agenda and block detail sheets. Parsed capture details are editable during conversion. Exact recovery previews/undo, course-first Study and the reader redesign remain. See [SESSION42_UI_UX_FOUNDATION.md](SESSION42_UI_UX_FOUNDATION.md) for validation and the precise release boundary.

## Direction

Keep Punla's field-notebook identity: cream paper, crop green, restrained UP maroon and mango accents. Give the current action the strongest emphasis. Use a readable body font for reviewers, equations, dates, and task lists; keep decorative type optional for headings. Atmospheric effects should sit behind stable, opaque reading surfaces and respect the existing motion controls.

## Reference findings

| Reference | Observed pattern | Application to Punla |
| --- | --- | --- |
| [Structured](https://structured.app/) | The official mobile screenshot has a week strip, vertical time axis, activity durations, current-time marker, and a prominent add action. | Turn Plan's block cards into an agenda timeline. Show classes, walking buffers, study sessions, and routines together. Distinguish fixed commitments from movable work with icons and labels as well as color. |
| [Things: Today and This Evening](https://culturedcode.com/things/support/articles/4001304/) | The official screenshot groups calendar events at the top, then priorities, with a separate evening section. | Refine Today into one primary next action, compact next-class/leave-by context, and a quieter Later section. Keep the explanation for the recommendation available. |
| [Todoist Quick Add](https://www.todoist.com/help/todoist/features/use-task-quick-add-in-todoist-va4Lhpzz) | Official documentation describes task entry with date/project recognition and details revealed as needed. | Open a small capture sheet from the global add button, focus its text field, then show editable course/date/type chips and attachments. Keep explicit confirmation before converting a capture. |
| UX Pilot community catalog: “Bloom - The consumer app” | The connector returned a free, three-screen mobile template described as personalized watering reminders. | A candidate for Punla's growth/reminder tone. Its actual layouts were not visually verified, so it is not a selected component reference. |

The Structured and Things screenshots were inspected. Todoist's interaction was checked against its official documentation. Mobbin's connector returned INVALID_ARGUMENT for repeated valid searches; no Mobbin screens were successfully retrieved or evaluated. No unavailable screen is represented as a reviewed reference.

## Recommended sequence

### 1. Today: make the next action obvious

- Promote the relevant action into one filled button: Start focus, Leave now, Resume study, or Open schedule, depending on real context.
- Give Now the strongest heading; make Next and Later compact rows. Expand supporting statistics on demand.
- Example using illustrative data: “25 minutes free · Review MATH 27 derivatives · Quiz tomorrow · Start 20-minute review.”
- Keep energy check-in compact and editable. Don't imply a check-in is required before using the app.
- Acceptance: a student can identify what to do and why without scrolling; departure and active-focus states remain higher priority than generic study suggestions.

### 2. Navigation: organize by student intent

- Prototype four primary destinations: Today, Plan, Study, More. Keep a global add action and a visible Inbox badge.
- Place Pulse within Study; Timeline within Plan; routines, budget, campus map and settings remain reachable from More and contextual shortcuts.
- Reduce the current six-tab Plan & Inbox surface and seven-tab Study Hub by using course pages and contextual tools.
- Preserve existing deep links, widgets, notification actions, and quick access to Schedule and Budget. Validate the proposal against actual usage before replacing navigation.

### 3. Planner: show time and make recovery easy

- Use a date strip and vertical agenda with current-time marker, durations, and free gaps.
- Keep Focus and Done visible on the selected study block. Put Move, Resize, Lock and Skip in its detail sheet.
- Offer a concise recovery sheet for a missed block: Next opening, Split session, Choose time. Show the proposed change before confirmation and offer Undo.
- Provide explicit move controls alongside any drag interaction. Preserve overlap, deadline, ongoing-block and lock checks.

### 4. Capture: reduce typing and form clutter

- One text-first sheet; display parsed fields as editable chips after entry.
- Show attachment name/type and saved state. Allow drafts with unknown dates and courses.
- Distinguish Save to Inbox from Confirm task. Do not imply that retained PDFs/images have been OCR-processed.
- Acceptance: saving a rough thought needs no course/date form, while conversion cannot silently create duplicates.

### 5. Study: prioritize reading and the next review

- Course overview: Continue reading, Due reviews, Weak concepts, Upcoming assessment.
- Reader: adjustable type, comfortable line spacing, collapsible table of contents, persistent reading position, and accessible formula text. Long tables and formulas need deliberate overflow handling.
- Keep one main Study now action with concrete counts and duration where supported by real data.
- Move analytics and question-bank management into secondary tools. Preserve the manual choices for quizzes, notes, and flashcards.
- Show specific Pulse evidence such as overdue work or unresolved mistakes rather than an invented overall risk score.

### 6. Finish with consistency and accessibility

- Share spacing, corner, icon, button, sheet, and empty-state styles across the new surfaces.
- Check narrow phones, landscape, large text, dark mode, TalkBack, and reduced motion.
- Pair color with text/icons for status. Keep destructive actions distinct from completion and preserve Undo where feasible.
- Keep secondary actions easy to find without giving every card a row of equally prominent buttons.

## Release boundary

First verify the crash fix with existing study data, ordinary reviewer text, math markup, and cloze cards. Then prototype Today, navigation, and Plan in that order. Full-week reliability, background/OEM checks, and surveyed campus routes remain release gates from the Student OS roadmap. Accounts/sync remain deferred under that roadmap.

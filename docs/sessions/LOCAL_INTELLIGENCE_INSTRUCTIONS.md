# Local Intelligence — Pattern Learning + Assistant

Plan for a personal-pattern layer on top of Punla's existing Room data
(`StudySession`, `Expense`, `ClassSession`, `Deadline`) plus an optional
conversational layer on top of that. Staged like `STUDY_SUGGESTIONS_AND_STREAKS.md` —
Phase 1 is pure on-device statistics with no new dependency, Phase 2 makes
those statistics predictive, Phase 3 is the "Jarvis" conversational surface
and is the only phase that costs money or touches the network on its own.

**Design principle carried through all three phases**: nothing here should
require the remote collaborator's build workflow to change, and nothing
should send raw personal data anywhere unless Phase 3 is explicitly reached
and the person has opted in. Phases 1–2 are 100% on-device, zero-dependency,
zero-cost — matching Punla's existing "native APIs before new dependencies"
approach from the background-style and glass-card work.

---

## 1. What's already in place (reused, not rebuilt)

- `StudySession` + `StudySessionDao` — Pomodoro completion log (Session 4).
- `suggestStudySlot()` / `freeSlotsFor()` — free-slot + deadline matching
  (`ScheduleScreen.kt`, `ui/pomodoro/StudySuggestion.kt`).
- `currentStudyStreak` — already computed for the Dashboard/Study Analysis
  screens (Session 6 discovered Phase 2 of the streaks doc was already
  built this way).
- `Expense`/`ExpenseRule`, `isFixed` flag, weekly budget derivation
  functions in `PunlaRepository.kt` (Session 5).
- `BudgetWorker` / `DeadlineWorker` / `ChecklistReminderWorker` — existing
  notification cadence and opt-out patterns to extend, not replace.

Everything below is new aggregation *on top of* this data, not a new data
model.

---

## 2. Phase 1 — Pattern engine (no new dependency, on-device only)

New file: `ml/UserPatterns.kt` — pure functions only, no Compose/Room
dependency directly (same shape as `BackgroundPainters.kt`: takes lists in,
returns values out, so it's trivially unit-testable and callable from both
the UI layer and workers).

### Functions to build
- `studyTimeHistogram(sessions: List<StudySession>): Map<Int, Float>` —
  completion rate by hour-of-day, so `suggestStudySlot()` can eventually
  *rank* candidate slots instead of returning the first match.
- `sessionAbandonRate(sessions: List<StudySession>): Float` — sessions
  logged via the "early stop past 60s" path (already distinguished in the
  Pomodoro state machine, Session 4) vs. natural completions. Surface this
  quietly in Study Analysis, not as a nag.
- `recurringExpenseCandidates(expenses: List<Expense>): List<ExpensePattern>` —
  groups by vendor/amount similarity, flags anything appearing 3+ times
  that isn't already an `ExpenseRule`. Feeds a "make this recurring?"
  prompt on the Budget screen, not an automatic conversion.
- `absenceVelocity(session: ClassSession): Float` — absences per week
  elapsed so far this term, compared against the pace needed to stay under
  `allowedAbsences()`. Lets the existing near-limit/over-limit UI in
  `ClassCard` (`ScheduleScreen.kt`) warn *earlier* than "one away from the
  limit," while reusing that same color-tier pattern.
- `notificationEngagement(taps: List<NotificationTapEvent>): Map<Int, Float>` —
  requires one new small table (see below) tracking which hour a worker
  notification was tapped vs. dismissed/ignored, to eventually shift
  `BudgetWorker`/`DeadlineWorker` firing windows toward times that
  actually get a response.

### Minimal new persistence
One new Room entity, `NotificationTapEvent(id, workerName, firedAtHour,
tapped: Boolean)`, logged by each existing worker's notification intent —
not a new subsystem, just one more row-per-notification table alongside
the ones already in `PunlaDatabase.kt`. Bump schema version, rely on the
existing `fallbackToDestructiveMigration()` like every other bump so far.

### Wiring
- `suggestStudySlot()` gains an optional ranking pass using
  `studyTimeHistogram()` — falls back to current first-match behavior if
  there's insufficient history (say, <10 logged sessions), so a fresh
  install behaves exactly as it does today.
- Budget screen: a dismissible suggestion chip next to a matched
  `ExpensePattern`, styled like the existing "Fixed" tag.
- Nothing changes on the Dashboard cards visually until Phase 1 data
  exists to act on — no empty/placeholder states to design around.

### Testing checklist
- [ ] Fresh install (no `StudySession` history) — `suggestStudySlot()`
      behaves identically to pre-Phase-1 behavior.
- [ ] Histogram/pattern functions never crash on empty or single-item
      lists (matches the defensive style already in `parseTime()`).
- [ ] `recurringExpenseCandidates()` doesn't re-suggest an expense that's
      already been converted to an `ExpenseRule`.
- [ ] `NotificationTapEvent` logging doesn't add a new periodic/alarm
      wake-up — it's written synchronously from the existing tap intent,
      same no-new-wakeups constraint as the background-style widget work.

---

## 3. Phase 2 — Predictive layer (still on-device, still no dependency)

Only build this once Phase 1 has real data flowing (weeks, not days, of
`StudySession`/`Expense` history) — training anything on a handful of data
points just overfits to noise.

- **Online logistic regression**, hand-rolled (a few dozen lines — weights
  updated via simple SGD after each new `StudySession` is logged) predicting
  "will a suggested slot actually get used" from features like hour-of-day,
  day-of-week, minutes-until-deadline, and current streak length. Small
  enough that a hand-written implementation beats pulling in a real ML
  runtime for this data scale.
- **Markov chain over screen navigation** (optional, lower priority) — "after
  logging an expense, next screen is usually Budget" — could drive a
  post-action shortcut suggestion. Needs a lightweight navigation-event log,
  same shape as the notification-tap table above.
- Neither of these needs TensorFlow Lite or any training-capable ML
  framework — at personal-data scale (hundreds, not millions, of rows),
  a hand-rolled online model is both sufficient and fully inspectable,
  which matters for a "why did it suggest this" moment.

### Testing checklist
- [ ] Prediction confidence is withheld (falls back to Phase 1 heuristic
      ranking) below some minimum sample count, not shown as a false-confident
      number on sparse data.
- [ ] Model weights persist across process death (SharedPreferences-backed,
      same pattern as `lastStudySuggestionDismissedAt`) — not retrained from
      scratch every cold start.

---

## 4. Phase 3 — Conversational layer ("Jarvis") — opt-in, costs money

This is the only phase that talks to a network API and is the only phase
that should ever leave the device. Explicitly gated behind a Settings
toggle, off by default.

### Architecture
- **Local intent parsing first.** A query like "how much did I spend on
  food this week" should resolve to a structured intent
  (`{category: "food", period: "week"}`) either via simple keyword matching
  or, if it doesn't match a known pattern, escalate to the API. Most
  day-to-day queries ("what's my schedule today," "how many absences do I
  have left") should never need a network call at all — that's a plain
  Room query, same as anywhere else in the app.
- **API call only carries the relevant data slice**, never the full
  database — e.g. this week's 12 expense rows, not the whole `Expense`
  table. Keeps token cost near-fixed regardless of how much history the
  person has accumulated, and keeps most personal data off the wire by
  default.
- **Model choice**: the cheapest current-generation model tier is
  appropriate here — this is grounded lookup/summarization over a small
  data slice, not open-ended reasoning, so the fastest/cheapest tier
  should perform just as well as a larger one while costing meaningfully
  less per call. Confirm current model IDs/pricing against Anthropic's
  docs at build time rather than hardcoding a specific model string here,
  since pricing and model names shift over time.
- **Prompt caching** for the (mostly static) system prompt/instructions,
  since that's sent on every call — meaningful savings on a chat feature
  used often.

### New files
- `ml/JarvisQueryHandler.kt` — local intent classifier + Room query
  dispatch for the "no API needed" path.
- `data/AssistantApi.kt` — the escalation path, mirrors `RoutingApi.kt`'s
  own shape (Session 11): returns `null`/a typed failure on any error,
  every caller falls back to "I couldn't find that" rather than crashing
  or hanging.
- `ui/screens/AssistantScreen.kt` or a Dashboard chat card — UI surface,
  scoped after 1–2 are working end to end.

### Explicitly out of scope for this pass
- On-device LLM inference (e.g. MediaPipe LLM Inference API running a
  local Gemma model) — technically fully local and free per-call, but a
  multi-hundred-MB download and a real device-capability bar. Worth
  revisiting only if the API-cost or privacy tradeoff of Phase 3 stops
  feeling worth it once it's actually being used.

### Testing checklist
- [ ] Assistant toggle defaults off; app behavior is unchanged for anyone
      who never opts in.
- [ ] A query resolvable locally never triggers a network call — verify
      via logging, same spirit as `RoutingApi.kt`'s failure logging added
      in Session 12b.
- [ ] A failed/timed-out API call degrades to "couldn't reach the
      assistant right now," never a crash or infinite spinner.
- [ ] No conversation history or raw table dumps are ever included in a
      request — only the specific rows the resolved intent needs.

---

## 5. Open questions for whoever picks this up

- Where does the Phase 1 pattern data actually surface first — a new
  "Insights" section, or folded into existing cards (Study Analysis,
  Budget)? Folding into existing cards avoids a new empty destination
  with nothing in it on a fresh install.
- Is per-notification tap tracking (Phase 1) worth the new table given
  it only feeds one future feature (adaptive notification timing), or
  should that piece be deferred until Phase 1's other three functions
  prove useful on their own?
- Phase 3's API cost is genuinely per-use — worth deciding a rough usage
  budget/rate-limit before shipping the toggle, rather than after.

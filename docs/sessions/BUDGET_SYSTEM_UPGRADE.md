# Budget System Upgrade — Session 26

## Goals

Make the Budget tab answer the student's next decision — “can I afford this today?” — instead of only reporting historical spend.

## What changed

### Safe to spend today

- Monthly mode: `(monthly budget - spend through today - upcoming fixed recurring commitments) / days left in month`.
- Weekly mode: `(weekly discretionary budget - weekly discretionary spend) / days left in week`.
- Both mode: uses the tighter available daily amount so the recommendation does not violate either plan.
- Negative values are shown as an over-pace state rather than a misleading positive allowance.

### Fixed commitment reservation

Expense recurrence only materializes rows through today. `PunlaRepository.projectedFixedCommitmentsFromRules()` walks fixed weekly/monthly rules forward to the requested period end so future rent/subscription/tuition-like items can be reserved before discretionary guidance is calculated.

### Category guardrails

Optional monthly limits are stored as a JSON map in SharedPreferences. No Room schema change is required. Blank categories have no limit. Configured category cards show remaining/over amounts and progress against the cap.

### Expense correction

Budget entries can now be edited and backdated. New entries reject future dates. Editing a generated recurring occurrence preserves its rule link and changes only that occurrence.

### Recurring catch-up

Creating a backdated recurring expense now runs `RecurrenceEngine.generateRecurringExpenses()` immediately so any already-due weekly/monthly instances appear without relaunching the app.

### Surfaces

- Budget screen: safe-to-spend card, reserved fixed bills, category caps.
- Home: safe-today guidance and reserved fixed commitments.
- Tall Budget widget: safe-today line.
- Budget notifications: actionable pace guidance with BigTextStyle.

### Backup

Backup format version 4 adds `categoryBudgetLimits`. Older backups remain importable because the field is optional.

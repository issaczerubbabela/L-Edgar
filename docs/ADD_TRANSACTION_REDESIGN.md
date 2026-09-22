# Add Transaction Redesign

Design reference for reworking `LogScreen.kt` / `LogViewModel.kt` around a keypad-first
layout inspired by Buckwheat's Add Transaction UI, while keeping every field and behavior
already present in SheetSync. This doc is the implementation reference; the visual mockups
live in three Claude artifacts (not checked into the repo):

- v1 — first pass, Buckwheat-style layout mapped onto L-Edgar's fields:
  https://claude.ai/artifact/5NrFazr2ChjRty4gxJZYyu
- v2 — design-critique pass (`frontend-design` / `ui-ux-pro-max` skills): dropped AI-tell
  chrome, cut to two typefaces, real vector icons, 44dp touch targets, 4/8dp spacing rhythm:
  https://claude.ai/artifact/RU3x8YYf4vLVh8LSbovgeh
- v3 — current — adds inline category creation and a live per-category budget preview:
  https://claude.ai/artifact/LT2znLxWKyTcKiEufeQPwY

No code has been changed yet. This is a design spec to implement against.

## Why

Buckwheat's Add Transaction screen is a permanent editor: a dominant amount, a custom
numeric keypad, and one tag chip standing in for category/comment. L-Edgar carries more
state — `Expense / Income / Transfer`, category, account (or From/To account for
transfers), a date, a description, remarks, edit/delete, and a live sync chip — spread
across five stacked `OutlinedTextField`s today. The redesign keeps Buckwheat's keypad-first
interaction and chip toolbar, but gives each L-Edgar field its own chip instead of
collapsing everything into one, and adds two features on top:

1. **Inline category creation** — adding a brand-new category no longer requires leaving
   the screen for Settings → Manage Categories & Dropdowns.
2. **Live category budget preview** — replaces a generic "account balance" idea with the
   category budget math the Total tab already computes, scoped to the category currently
   selected and recalculated as the amount is typed.

## Layout

Top to bottom, replacing the current `TopAppBar` + stacked fields in `LogScreen.kt`:

1. **Top bar** — back arrow, sync status chip (`SyncStatusUi`, same four states as today),
   trash icon (edit mode only).
2. **Date pill** — single line, current date; tapping opens the same `DatePickerDialog`
   already wired to `vm.selectedDate`.
3. **Type row** — `Expense / Income / Transfer` segmented control, same three options and
   same reset-on-change behavior as `LogScreen.kt`'s `SingleChoiceSegmentedButtonRow`.
4. **Budget strip** (Expense only, see below) or **context line** (Transfer: `"Cash → HDFC
   Bank"`; Income: nothing — budgets aren't implemented for Income).
5. **Amount** — large, right-aligned, tabular numerals, tinted by type (coral for Expense,
   green for Income, neutral for Transfer).
6. **Chip row** — Category chip + Account chip (Expense/Income), or From/To chips
   (Transfer), plus a Note chip that opens both Description and Remarks in one sheet.
7. **Drag handle**, then the **custom keypad**: 7 8 9 ⌫ / 4 5 6 [commit, spans 3 rows] /
   1 2 3 / 0 [spans 2 cells] . — replaces the system decimal keyboard on the amount field.

## Live category budget preview

Sourced from the exact computation `TotalViewModel.buildBudgetItems()` already does for the
Total tab's `BudgetProgressUi` rows — this reuses that math, it doesn't invent new math:

```kotlin
// TotalViewModel.kt — existing per-category computation to reuse
val categorySpend = expenseRecords
    .filter { it.type == "Expense" && it.category.equals(budget.category, ignoreCase = true) }
    .sumOf { it.amount }

BudgetProgressUi(
    title = budget.category,
    budgetAmount = budget.amount,
    spentAmount = categorySpend,
    remainingAmount = budget.amount - categorySpend,
    progressPercent = percent(categorySpend, budget.amount),
    todayMarkerFraction = idealFraction   // calculateIdealFraction(selectedYm)
)
```

For the Add Transaction screen, the same computation needs one addition: fold in the
**currently unsaved amount** for the selected category before computing `spentAmount`, so
the strip updates live as the user types — matching Buckwheat's "budget preview
recalculates using the unsaved amount" behavior, just pointed at a real per-category budget
instead of a single daily total.

```kotlin
val liveSpend = categorySpendThisMonth(selectedCategory) +
    (if (selectedType == "Expense") amount.toDoubleOrNull() ?: 0.0 else 0.0)
```

**Data dependency**: this needs `BudgetRepository.observeBudgets(monthYear)` (already
exists, used by `BudgetSettingViewModel`) and the current month's Expense records for the
selected category (already available via `ExpenseRepository`). `LogViewModel` currently
injects neither — both need adding to its constructor.

**Visual spec**: reuse `IdealBudgetProgressBar` from `TotalTabScreen.kt` at a smaller scale
(6dp track height vs. 30dp) — same track color `#2D323C`, same fill color `IncomeBlue
(#1976D2)`, same over-budget color `FabRed (#E53935)` when `spentFraction > 1f`, same white
ideal-marker tick at `todayMarkerFraction`. Label row: category name (left), "₹X left"
(right, using `remainingAmount`).

**Scope**: only render the strip when `selectedType == "Expense"` and a category with a
configured budget for the current month is selected. `BudgetSettingViewModel` only reads
`EXPENSE_CATEGORY` dropdown options today — Income and Transfer have no budget concept in
the app, so they get a plain context line (Transfer: account flow) or nothing (Income)
instead of a fabricated bar.

**No configured budget for the category**: hide the strip rather than showing a
zero/empty bar — a budget row only exists in `budgets` once the user has set one via
`BudgetSettingScreen`.

## Inline category creation

Replaces the current dead-end message ("No categories found. Add from More > Manage
Categories & Dropdowns.") with a dashed **+ Add** chip appended to the category (or account)
picker sheet's option grid. Tapping it swaps the grid for a single text field + confirm
button, in the same sheet — the amount and keypad underneath keep their state.

Confirming calls the same insert path `DropdownManagementViewModel.addOption()` already
uses:

```kotlin
// DropdownManagementViewModel.kt — existing insert to reuse from the sheet
val maxOrder = options.maxOfOrNull { it.displayOrder } ?: -1
dropdownRepository.insert(
    DropdownOption(
        optionType = selectedType.key,   // "EXPENSE_CATEGORY" or "INCOME_CATEGORY"
        name = name,
        displayOrder = maxOrder + 1
    )
)
```

After insert, the sheet should auto-select the new option (`vm.selectedCategory = name`)
and close, rather than leaving the user to re-open the sheet and pick it. `optionType` is
`EXPENSE_CATEGORY` or `INCOME_CATEGORY` depending on `selectedType`, matching
`LogScreen.kt`'s existing `expenseCategories` / `incomeCategories` split. Reordering and
deleting still only happen in Settings — the sheet links out to
`More → Dropdowns` for that, it doesn't duplicate it.

## What doesn't change

- **Validation** — same rules as `LogViewModel.save()` today: Transfer needs From ≠ To,
  everything else needs category + account, amount must parse to > 0.
- **Sync model** — Room write first, then `enqueueUniqueWork(SyncWorker.TAG, REPLACE)`;
  the sync chip observes the same `WorkInfo` flow via `observeSyncStatus()`.
- **Edit mode** — pre-fills amount, chips, and date from the existing record; adds a trash
  icon next to the sync chip; the commit key calls `update()` instead of `save()`.

## Touchpoints for implementation

| Area | File |
|---|---|
| Screen layout | `ui/screens/LogScreen.kt` |
| Form state, save/update, sync observing | `viewmodel/LogViewModel.kt` |
| Category/account bottom sheets (new) | new composables in `ui/screens/LogScreen.kt` or `ui/components` |
| Inline category insert | `data/repository/DropdownOptionRepository.kt` (existing `insert()`) |
| Budget data for the strip | `data/repository/BudgetRepository.kt` (existing `observeBudgets()`) |
| Budget math to mirror | `viewmodel/TotalViewModel.kt` (`buildBudgetItems`, `calculateIdealFraction`, `percent`) |
| Progress bar visual to reuse at smaller scale | `ui/screens/TotalTabScreen.kt` (`IdealBudgetProgressBar`) |
| Custom keypad (new component) | new composable, e.g. `ui/components/NumericKeypad.kt` |

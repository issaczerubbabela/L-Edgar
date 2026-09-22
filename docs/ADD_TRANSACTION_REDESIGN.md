# Add Transaction Redesign

Reference for the keypad-first Add Transaction screen: a dominant amount display, tappable
chips that open bottom sheets for category/account/note, and a custom numeric keypad,
replacing the previous stack of `OutlinedTextField`s. Applies to both the full screen
(`LogScreen.kt`) and Quick Add (`QuickLogActivity.kt`).

## Why

The previous form put every field — date, type, category, account, description, amount,
remarks — into its own labeled text field, stacked and scrolled. The redesign makes the
amount the visual anchor and turns the rest into tappable chips, closer to how a numeric
entry screen should feel, while keeping every field and validation rule the old form had.

## What shipped

- **Custom numeric keypad** (`ui/components/NumericKeypad.kt`) — a weighted grid (`7 8 9
  backspace / 4 5 6 [commit, spans 3 rows] / 1 2 3 / 0 [spans 2 cells] .`) driving the
  `amount` string via `util/AmountInput.kt`'s `applyKeypadAction`, a pure function (unit
  tested in `AmountInputTest.kt`) that keeps the string always in a valid intermediate state
  (`""`, `"5"`, `"5."`, `"5.25"` — never `"5.."` or a leading-zero artifact like `"05"`).
- **Option picker sheet** (`ui/components/OptionPickerSheet.kt`) — a `ModalBottomSheet` with
  a search field and a chip grid, reused for Category, Account, From Account, and To Account.
- **Inline category creation** — a dashed "+ Add" chip in the sheet swaps to a text field +
  confirm button. Confirming calls `LogViewModel.addCategoryInline()` /
  `QuickLogViewModel.addCategoryInline()`, which case-insensitively matches against the
  existing options first (there is no unique index on `(optionType, name)` in
  `dropdown_options`, so a duplicate name would otherwise silently create a second row),
  otherwise inserts via `DropdownOptionRepository.insert()` with `maxOrder + 1` — the same
  path `DropdownManagementViewModel.addOption()` uses — then selects it and enqueues a sync.
  Only available for categories; accounts still require the full Accounts tab flow (adding an
  account needs a group, initial balance, etc. that don't fit an inline text field).
- **Note chip** — a single chip opens a small sheet with the Description and Remarks fields
  together, since neither is needed to glance at a transaction.
- **Date pill** — replaces the old date `OutlinedTextField`; tapping it still opens the same
  `DatePickerDialog`. For Transfer, it shows the selected From → To accounts inline since
  there's no category chip to carry that context.
- **Quick Add parity** — `QuickLogActivity`'s bottom sheet now uses the same
  `NumericKeypad` and `OptionPickerSheet` (with inline add) instead of a plain text field and
  dropdown, and `QuickLogViewModel.save()` now enqueues `SyncWorker` after a successful save,
  which it previously never did.
- **`DropdownField` moved** from `ui/screens/LogScreen.kt` into
  `ui/components/DropdownField.kt` (unchanged) so the five other screens that use it
  (`AccountDetailScreen`, `OverallAccountStatsScreen`, `AddEditAccountSheet`, `HistoryScreen`,
  `QuickLogActivity`) keep compiling once `LogScreen.kt` no longer defines it.

## Preserved from before

- **Validation** — same rules as before: Transfer needs From ≠ To, everything else needs
  category + account, amount must parse to > 0. The commit key just calls `vm.save()`.
- **Sync model** — Room write first, then `enqueueUniqueWork(SyncWorker.TAG, REPLACE)`; the
  sync chip in the top bar observes the same `WorkInfo` flow via `observeSyncStatus()`.
- **Edit mode** — pre-fills amount, chips, and date from the existing record; a trash icon
  appears next to the sync chip; `onSaved()` still only fires in edit mode (create mode shows
  a snackbar and stays on the screen, matching the pre-redesign behavior).
- **App-lock gate** — `LogScreen` is the start destination when app lock is enabled
  (`AppNavigation.kt`), with `showEnterAppButton` as the only way into the rest of the app for
  a locked user. Since the bottom of the screen is now the keypad, that button moved into the
  top bar instead of its old bottom-center position.

## Deferred: budget preview

An earlier design pass (see the four mockup artifacts linked below) explored a per-category
budget indicator — first as its own bordered strip, then folded into the date pill as a small
radial ring, reusing `TotalViewModel`'s existing spent/budget/remaining/ideal-marker math.
**This was deliberately left out of the shipped implementation** — budgets are being replaced
by a "buckets" model in separate work, so building against the current `Budget`/
`BudgetRepository` shape now would likely need reworking again shortly after. Revisit once the
buckets model lands; the math to reuse (`TotalViewModel.buildBudgetItems()`,
`calculateIdealFraction()`, `percent()`) and the intended placement (the date pill's right
slot) are still the right starting point.

## Design mockups (reference only, not checked into the repo)

- v1: https://claude.ai/artifact/5NrFazr2ChjRty4gxJZYyu
- v2 (design-critique pass): https://claude.ai/artifact/RU3x8YYf4vLVh8LSbovgeh
- v3 (budget strip, since reworked): https://claude.ai/artifact/LT2znLxWKyTcKiEufeQPwY
- v4 (budget ring, deferred — see above): https://claude.ai/artifact/PzjK1xNVWvYdJrbZngBW9V

## Touchpoints

| Area | File |
|---|---|
| Screen layout | `ui/screens/LogScreen.kt` |
| Form state, save/update, sync observing, inline category add | `viewmodel/LogViewModel.kt` |
| Shared dropdown field | `ui/components/DropdownField.kt` |
| Custom keypad | `ui/components/NumericKeypad.kt` |
| Category/account picker sheet | `ui/components/OptionPickerSheet.kt` |
| Amount-string keypad logic (+ tests) | `util/AmountInput.kt`, `app/src/test/.../util/AmountInputTest.kt` |
| Quick Add sheet | `QuickLogActivity.kt` |
| Quick Add state, inline category add, sync enqueue | `viewmodel/QuickLogViewModel.kt` |

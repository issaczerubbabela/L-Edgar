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
  `QuickLogViewModel.addCategoryInline()`, which delegate to
  `DropdownOptionRepository.addOptionIfAbsent()`: it matches case-insensitively against the
  existing options first (there is no unique index on `(optionType, name)` in
  `dropdown_options`, so a duplicate name would otherwise silently create a second row),
  otherwise appends at `maxOrder + 1` — the same ordering `DropdownManagementViewModel.addOption()`
  uses — and returns the name to select. `LogViewModel` only selects it if the transaction type
  is still the one the category was created for. Only available for categories; accounts still
  require the full Accounts tab flow (adding an account needs a group, initial balance, etc.
  that don't fit an inline text field).
- **Note chip** — a single chip opens a small sheet with the Description and Remarks fields
  together, since neither is needed to glance at a transaction.
- **Date pill** — replaces the old date `OutlinedTextField`; tapping it still opens the same
  `DatePickerDialog`. For Transfer, it shows the selected From → To accounts inline since
  there's no category chip to carry that context.
- **Quick Add parity** — `QuickLogActivity`'s bottom sheet mirrors the full screen's vertical
  structure (title, bucket strip, `AmountDisplay` taking the spare height, category chip,
  keypad pinned to the bottom) and uses the same shared parts from
  `ui/components/TransactionEntryParts.kt` (`AmountDisplay`, `ChipRow`), `NumericKeypad` and
  `OptionPickerSheet`, instead of its own text and chip. It fills a fixed fraction of the
  screen so the amount has bounded room to grow into.
- **`DropdownField` moved** from `ui/screens/LogScreen.kt` into
  `ui/components/DropdownField.kt` (unchanged) so the four other screens that use it
  (`AccountDetailScreen`, `OverallAccountStatsScreen`, `AddEditAccountSheet`, `HistoryScreen`)
  keep compiling once `LogScreen.kt` no longer defines it.

## Preserved from before

- **Validation** — same rules as before: Transfer needs From ≠ To, everything else needs
  category + account, amount must parse to > 0. The commit key just calls `vm.save()`.
- **Sync model** — Room write first; the ViewModels never enqueue work themselves.
  `sync/SyncTriggers` observes Room changes and calls `SyncScheduler.requestSync()`. The sync
  chip in the top bar shows `SyncScheduler.transactionSyncStatus`.
- **Edit mode** — pre-fills amount, chips, and date from the existing record; a trash icon
  appears next to the sync chip; `onSaved()` still only fires in edit mode (create mode shows
  a snackbar and stays on the screen, matching the pre-redesign behavior).
- **App-lock gate** — `LogScreen` is the start destination when app lock is enabled
  (`AppNavigation.kt`), with `showEnterAppButton` as the only way into the rest of the app for
  a locked user. Since the bottom of the screen is now the keypad, that button moved into the
  top bar instead of its old bottom-center position.

## Interaction polish pass

After the initial rebuild, a second pass added tactile feedback and transitions the static
first version lacked (informed by looking at how Buckwheat's own editor/keyboard handles
sizing and motion — same techniques, our own values and code, not reused source):

- **`NumericKeypad`** — every key (`KeypadKey` in `NumericKeypad.kt`) morphs its corner
  radius between an idle and a pressed value via `animateDpAsState`, driven by
  `collectIsPressedAsState()` on its own `MutableInteractionSource`, so presses read as a
  tactile "squish" rather than a flat ripple. The commit key's background now fades between
  its enabled/disabled colors with `animateColorAsState` instead of snapping the instant
  `commitEnabled` flips (e.g. on the first digit typed).
- **`AmountDisplay`** — pulses briefly (scale `1f → 1.06f → 1f` over ~250ms) on a successful
  save, driven by an `Animatable` keyed to a counter that increments in the existing
  `LaunchedEffect(vm.saveSuccess)` block, so there's a visible "it worked" moment beyond the
  sync chip changing color.
- **`ChipRow`** (`ui/components/TransactionEntryParts.kt`) — given a `heightIn(min = 44.dp)` for consistent height regardless of chip
  label length, and the category/account ↔ From/To chip swap on type change now runs through
  `AnimatedContent` (150ms fade + horizontal slide, `slideSwap()` in `ui/components/Transitions.kt`, shared with the picker) instead of relabeling instantly.
- **`OptionPickerSheet`** — the search field ↔ inline "add" field swap animates the same way,
  and the dashed "+ Add" chip fades and scales in/out instead of appearing/disappearing
  abruptly.

This was a Compose-only pass — no ViewModel, data, or validation changes.

## Layout fix: keypad height must be explicit

`NumericKeypad`'s commit key spans three rows via a `ColumnScope.weight(1f)`, which only
resolves against a *bounded* parent height. The first version left the outer `Row` to wrap
its content, so on a real device the commit key ballooned to fill whatever space Compose
handed the row, squeezing `AmountDisplay`'s own `weight(1f)` down to zero and making the
amount invisible. Fixed by giving the row an explicit height so the weighted commit key has
something real to resolve against. That height is now a clamped fraction of the screen height
(`KEYPAD_HEIGHT_FRACTION`, between `KEYPAD_MIN_HEIGHT` and `KEYPAD_MAX_HEIGHT`) rather than a
constant, and the key font and icon sizes scale with it. If this component is touched again,
keep the height explicit — don't let the row go back to wrapping its content.

The keypad does **not** pad for the system navigation bar. It used to, inside its fixed
height, which squeezed the bottom `0 .` row on devices with a nav-bar inset (and doubled the
inset under the `Scaffold`). The host owns the bottom inset: `LogScreen`'s `Scaffold` and the
Quick Add `ModalBottomSheet`.

## Bucket preview

A strip under the date row shows the bucket the selected category lands in, and how much of it
is left once the amount being typed is spent: the bucket's colour dot and name, "₹X left of ₹Y",
and a `PaceBar` with the pace tick. Overspending turns it into the striped red bar with the
"Over by" flag. It is on both Add Transaction and Quick Add.

A transaction has no bucket of its own: the category routes it (see `CONTEXT.md`, Bucket
category), so the preview is derived, never stored.

- **Live** — the amount is added to the bucket's spend as you type. When editing, the record's
  original amount is taken back out first so it is not counted twice.
- **Hidden** for Income and Transfer, before a category is chosen, for a category no bucket
  claims (it would count as Unbucketed), when there is no running cycle, and for a date before
  the running cycle started. A running cycle keeps absorbing spend past its end date, so a later
  date still shows.
- **Source** — `data/bucket/BucketPreviewSource` builds the same `CycleSummary` the Budget tab
  uses; `BucketPreviewCalculator` (pure, unit tested in `BucketPreviewCalculatorTest`) turns it
  into a `BucketPreview`. `BucketPreviewSlot` animates the strip in and out so the amount area
  absorbs the height change and the keypad does not jump.

The earlier mockups explored a radial ring inside the date pill (v4 below); the strip below the
date row won because it has room for the pace tick.

## Design mockups (reference only, not checked into the repo)

- v1: https://claude.ai/artifact/5NrFazr2ChjRty4gxJZYyu
- v2 (design-critique pass): https://claude.ai/artifact/RU3x8YYf4vLVh8LSbovgeh
- v3 (budget strip, the basis of the shipped preview): https://claude.ai/artifact/LT2znLxWKyTcKiEufeQPwY
- v4 (budget ring, not built — see above): https://claude.ai/artifact/PzjK1xNVWvYdJrbZngBW9V

## Touchpoints

| Area | File |
|---|---|
| Screen layout | `ui/screens/LogScreen.kt` |
| Form state, save/update, sync observing, inline category add | `viewmodel/LogViewModel.kt` |
| Shared dropdown field | `ui/components/DropdownField.kt` |
| Amount display, chip row (shared with Quick Add) | `ui/components/TransactionEntryParts.kt` |
| Bucket strip | `ui/components/BucketPreviewStrip.kt`, `data/bucket/BucketPreview.kt`, `data/bucket/BucketPreviewSource.kt` |
| Transaction type values | `util/TransactionType.kt` |
| Custom keypad | `ui/components/NumericKeypad.kt` |
| Category/account picker sheet | `ui/components/OptionPickerSheet.kt` |
| Amount-string keypad logic (+ tests) | `util/AmountInput.kt`, `app/src/test/.../util/AmountInputTest.kt` |
| Quick Add sheet | `QuickLogActivity.kt` |
| Quick Add state, inline category add | `viewmodel/QuickLogViewModel.kt` |

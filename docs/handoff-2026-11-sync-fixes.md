# L-Edgar: handoff from the Nov 2026 data clean-up

## Context

- **App:** L-Edgar, an offline-first Android app (Kotlin, Room). Read `docs/ARCHITECTURE.md`, `CONTEXT.md` (glossary) and the ADRs in `docs/adr/`, especially 0001, 0003 and 0004.
- **Backend:** `scripts/AppsScript.gs`, deployed as a web app on the Google Sheet `budget_tracker_v1` (ID `1tRZAlApmjhoM7cfp84ep5zg6umZuoEjJiRT2hfBAF9A`).
- **Two sync models:**
  - **Transactions** (`_responses`) sync both ways by ID, with a per-row revision hash (three-way merge in `sync/SheetMerge.kt`).
  - **Lists** (`_dropdowns`, `_accounts`, `_budgets`, `_cycles`, `_buckets`, `_bucket_categories`) are phone-master. The app's `BackupWorker` clears each tab and rewrites it after any local change, after a ~20 s delay. "Import from Google Sheets" in Settings goes the other way and overwrites the local lists.
- **The sheet holds real financial data.** Before any test against it, copy it in Drive. A backup copy from 16 Nov 2026 already exists.

## What was done in the sheet this session (no code changed)

- **Categories reorganised.**
  - Expense: Food & Dining, Groceries, Rent, Transportation, Bills & Subscriptions, Shopping, Personal Care, Health, Learning, Leisure & Travel, Gifts & Giving, Family Support, Insurance (plus Investments/Savings, now unused and due to be deleted).
  - Income: Salary, Bonus, Investment Income, Family Support, Gift, Refunds & Paybacks.
- **About 610 transactions recategorised.** The user then recreated the new category list by hand in the app, because sheet edits to the lists kept being overwritten (see issue 2).
- **Savings modelled as transfers into Savings/Investments-group accounts:** Insurance RD, Emergency Fund RD, Recurring Deposit, HDFC Large Cap Fund. Chit fund payments became Family Support expenses (the money goes to the user's father). The term-insurance premium became an Insurance expense.
- **Data fixes:** "UPI" account names repointed to City Union Bank; a salary/bonus row split; a duplicate row deleted; date and amount number formats made consistent.

## Code issues to fix

I read the code only enough to find these. Confirm each one before changing anything.

1. **Import drops dropdown roles.** `ExpenseRepositoryImpl.importFromGoogleSheets` builds `DropdownOption(id, optionType, name, displayOrder)` without `role`, although the DTO and the Apps Script both carry it. After an import, SAVING, REFUND, SAVINGS and LIABILITY are lost and Stats go wrong. Fix: pass `role = dto.role.orEmpty()`, and add a test.

2. **Backups silently overwrite edits made in the sheet.** The list backups clear and rewrite their tabs with no check. Edits made in the sheet were lost twice this session, when the app backed up before the user could run Import. Options:
   - (a) A revision or hash per list tab. The script rejects a backup when the tab has changed since the last one, and the app then offers Import or Overwrite.
   - (b) Make lists sync both ways like transactions.
   - (c) At minimum, warn in the app.

   This is a design decision: write an ADR first.

3. **Categories can't be renamed in the app.** Dropdown management has add, delete, move and set-role only. A rename should, in one Room transaction:
   - update the option
   - update every transaction's `category` and mark it pending sync
   - update budgets and bucket routings

   A bulk recategorise-by-IDs query already exists in `ExpenseDao`.

4. **Buckets can't follow savings transfers.** Buckets only route expense categories (see the Bucket glossary in `CONTEXT.md`). The user wants to see progress on RD and SIP transfers. Idea: let a bucket also track transfers into chosen accounts.

5. **Unknown account names fail silently on sync.** In `SheetTransactionMapper`, a name matching no account (by name or group) ends up with no account. 317 City Union Bank rows and 74 UPI or Bank Transfer rows did this unnoticed. Surface the list after a pull, with a quick "create account / map to…" action.

6. **Import may break transaction links to accounts.** Import overwrites accounts and inserts them with `id = 0`, so they get new IDs. Verify that every transaction's `accountId`, `fromAccountId` and `toAccountId` is rebuilt correctly afterwards. Add a test.

7. **Creating or renaming an account doesn't link existing transactions.** Name-to-ID resolution only happens when a row is pulled. When an account is created or renamed, link the transactions whose `accountName`, `fromAccountName` or `toAccountName` match it and whose ID is null. This would fix the 317 unlinked City Union Bank rows at once.

**Suggested order:** 1, 7 and 5 (small, high value), then 6 (verify), 3, 2 (ADR first), 4.

## Sheet format rules the code must keep

- **`_responses` columns:** Timestamp, Date, Type, Exp Category, Inc Category, Description, Amount, Account Name, From Account Name, To Account Name, Remarks, Synced At, Is Bookmarked, ID.
- **Date:** a real date shown as M/d/yyyy. **Amount:** a plain number.
- **Text columns** 4, 5, 6, 8, 9, 10, 11 and ID are formatted as `@`.
- **Transfers** use `From -> To` in Account Name, plus the From and To columns.
- **The revision hash** covers columns 2–11 and 13. Any content change makes the phone re-pull that row.
- **Balances** count only transactions dated strictly after an account's opening ("as of") date (`AccountMath.counts`).

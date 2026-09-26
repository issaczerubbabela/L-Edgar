# SheetSync

An offline-first Android expense tracker. Each user keeps their data on their phone and mirrors it to a Google Sheet they own, through an Apps Script they deploy themselves.

## Money

**Transaction**:
One movement of money: an Expense, an Income or a Transfer. Stored as `ExpenseRecord` in `expense_records`.
_Avoid_: record, entry, expense (when you mean any type)

**Transfer**:
A Transaction that moves money from one Account to another. It has a from-account and a to-account and no Category.
_Avoid_: payment between accounts

**Account**:
A place money is held, such as a bank account, card or cash wallet. Its balance is its Initial balance plus the Transactions dated on or after its As-of date.
_Avoid_: asset (the UI's "Edit All Assets" means Accounts), payment mode

**Account group**:
The named group an Account belongs to, picked from the `ACCOUNT_GROUP` Dropdown options.

**Initial balance**:
An Account's balance on its As-of date (`initialBalanceDate`). Transactions dated before the As-of date don't count towards the Account's balance.
_Avoid_: opening balance, starting balance

**Category**:
The label that classifies an Expense or Income, picked from the `EXPENSE_CATEGORY` or `INCOME_CATEGORY` Dropdown options.

**Budget**:
The legacy plan: an amount for one Category in one month. Each month and Category pair has at most one. Salary cycles replaced it, but it's still kept and backed up so old backups restore.

**Salary cycle**:
The money the user has to spend between two paydays. The running cycle has no close date, and it stays open past its end date until the next one starts.
_Avoid_: month, period (a cycle rarely lines up with either)

**Bucket**:
A named pot of money inside one Salary cycle. Buckets belong to their cycle, so carrying them into the next cycle copies them.
_Avoid_: envelope, budget (the legacy per-category plan)

**Bucket category**:
The routing of one expense Category into one Bucket for one cycle. A Category sits in at most one Bucket per cycle; Categories in none count as Unbucketed.

**Dropdown option**:
A user-editable choice for Categories and Account groups. `PAYMENT_MODE` options are legacy: Accounts replaced them, and they are never backed up.

**Bookmark**:
A flag the user puts on a Transaction to find it again in the Bookmarks list.

## Auto-capture

**Captured transaction**:
A bank or UPI alert the app has parsed but not yet turned into a Transaction. It waits in the review inbox until the user acts on it.
_Avoid_: pending transaction, alert, capture (as a standalone noun)

**Confirm**:
Turning a Captured transaction into a real Transaction. Only the user confirms; nothing about auto-capture does this on its own.
_Avoid_: auto-log, auto-add

**Merchant rule**:
A saved mapping from a normalized merchant name to a Category, used to categorize future Captured transactions from the same merchant. Either set by the user or learned from repeated confirmations.
_Avoid_: category rule

**Account alias**:
A saved mapping from a bank account's last four digits or a UPI VPA to one of the user's Accounts, used to work out which Account a Captured transaction belongs to.
_Avoid_: account mapping

**Confidence band**:
High, Check or Low: how sure auto-capture is about a Captured transaction's suggested Category. Only High, with its Account already resolved, notifies the user; everything else waits in the inbox.
_Avoid_: confidence score

## Google Sheets

**Sheet**:
The user's own Google Sheet. Its `_responses` tab holds one row per Transaction. The `_accounts`, `_dropdowns` and `_budgets` tabs hold Backups.
_Avoid_: server, backend, cloud database

**Apps Script**:
The web app each user deploys over their own Sheet from `scripts/AppsScript.gs`. It's the only thing the phone talks to.
_Avoid_: API, server

**Sync**:
Sending a Transaction's pending change (insert, update or delete) from the phone to the Sheet.
_Avoid_: upload, backup (for Transactions)

**Backup**:
Replacing a Sheet tab with the phone's full current list of Accounts, Dropdown options or Budgets. It runs on every Sync.
_Avoid_: sync (for these lists)

**Import**:
Reading the Sheet into the phone ("Import from Google Sheets"). It adds Transactions the phone doesn't have yet and reports Sync conflicts.
_Avoid_: restore, download

**Sync action**:
The change a Transaction is waiting to Sync: `INSERT`, `UPDATE` or `DELETE`, or `NONE` once it has synced. A deleted Transaction stays on the phone, hidden, until its delete has synced.

**Remote timestamp**:
The value in a Transaction's Timestamp column that identifies its row in the Sheet (`M/d/yyyy HH:mm:ss`).
_Avoid_: created time, sync time

**Sync conflict**:
A local Transaction and a Sheet row that share a Remote timestamp but differ in content. It's found during Import, and the user picks which one to keep.

## Entry points

**Quick log**:
Adding a Transaction without opening the main app, from the Quick Settings tile, the home-screen widget or the app shortcut.

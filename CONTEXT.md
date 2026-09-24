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

## Google Sheets

**Sheet**:
The user's own Google Sheet. Its `_responses` tab holds one row per Transaction, which the user may also edit by hand. The `_accounts`, `_dropdowns` and `_budgets` tabs hold Backups.
_Avoid_: server, backend, cloud database

**Apps Script**:
The web app each user deploys over their own Sheet from `scripts/AppsScript.gs`. It's the only thing the phone talks to.
_Avoid_: API, server

**Sync**:
Bringing the phone and the Sheet into agreement on Transactions: a Pull when needed, then a Push.
_Avoid_: upload, backup (for Transactions)

**Push**:
Sending the phone's pending changes to the Sheet as upserts and deletes by Transaction ID.

**Pull**:
Reading the whole Sheet and merging it into the phone, one Transaction ID at a time. It runs on app open, on "Sync now", and when the script refuses a stale write.
_Avoid_: import (for this automatic merge), download

**Transaction ID**:
The permanent ID shared by a Transaction on the phone and its row in the Sheet's ID column.
_Avoid_: row number, timestamp (as an identifier)

**Revision**:
The script's hash of a Sheet row's content. It changes whenever the row is edited, by anyone. The phone stores the Revision it last agreed on, to tell whether the Sheet changed since.
_Avoid_: version (that's the phone's `localVersion`), fingerprint

**Backup**:
Replacing a Sheet tab with the phone's full current list of Accounts, Dropdown options or Budgets. Before a phone's first Backup, the phone takes in whatever the Sheet's lists have that it lacks.
_Avoid_: sync (for these lists)

**Import**:
The "Import from Google Sheets" action: it replaces the phone's Accounts, Dropdown options and Budgets with the Sheet's, then Pulls Transactions.
_Avoid_: restore, download

**Sync action**:
The change a Transaction is waiting to Sync: `INSERT`, `UPDATE` or `DELETE`, or `NONE` once it has synced. A deleted Transaction stays on the phone, hidden, until its delete has synced.

**Remote timestamp**:
The value in a Transaction's Timestamp column (`M/d/yyyy HH:mm:ss`). It no longer identifies the row; it's used only once, to link rows from before Transaction IDs.
_Avoid_: created time, sync time

**Sync conflict**:
A Transaction changed differently on the phone and in the Sheet since they last agreed. It isn't pushed until the user picks the phone's version, the Sheet's, both, or deletes it everywhere.

**Held deletion**:
A Transaction a Pull found missing from the Sheet but didn't delete from the phone, because too many went missing at once. The user decides whether to delete it or put it back in the Sheet.

## Entry points

**Quick log**:
Adding a Transaction without opening the main app, from the Quick Settings tile, the home-screen widget or the app shortcut.

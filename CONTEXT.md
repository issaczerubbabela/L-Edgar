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

**Stats role**:
The part a Dropdown option plays in Stats, stored in its `role`: an expense Category can be **Saving**, an income Category can be **Refund**, and an Account group can be **Savings**. Most options have no role.
_Avoid_: flag, tag

**Earned**:
Income in a period, not counting Refunds.
_Avoid_: total income (when Refunds are included)

**Spent**:
Expenses in a period, not counting Saving Categories, minus Refunds.
_Avoid_: total expenses (when Saving Categories are included)

**Saved**:
Money put aside in a period: Expenses in Saving Categories, plus Transfers into a Savings Account group from outside it, minus Transfers out of one. Saved money is never Spent.
_Avoid_: invested, spent (for Saving Categories)

**Refund**:
An Income in a Refund Category. It reduces Spent instead of adding to Earned, and it isn't tied to any expense Category.

**Left over**:
Earned minus Spent minus Saved, for a week, month, year or custom range.
_Avoid_: kept, balance, savings

**Left to spend**:
A Salary cycle's spendable amount minus every Expense in it, Saving Categories included and Refunds not taken off: the same figure the Budget tab shows, because a Bucket can hold savings. Once the cycle is over, the Stats tab calls it "Left unspent".
_Avoid_: left over (which is based on Earned, not the spendable amount)

**Usual**:
What a Category normally costs in Stats: its average over the three periods of the same kind before this one (months for a month, cycles for a cycle).
_Avoid_: average (without saying over what)

**Chart palette**:
The set of colours Stats charts use for money in, money out and saved, picked in Settings. Every palette except Classic keeps the three apart for red-green colour blindness.

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

**Unparsed alert**:
A bank or UPI alert that showed an amount but that no parser could read. It is kept for 30 days so a change in a bank's wording can be noticed and supported.
_Avoid_: failed alert

**Confidence band**:
High, Check or Low: how sure auto-capture is about a Captured transaction's suggested Category. Only High, with its Account already resolved, notifies the user; everything else waits in the inbox.
_Avoid_: confidence score

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

## Trips

**Trip**:
A named, time-bound outing whose shared costs are recorded and split between its Members. It is Active while being recorded and Archived once Posted.
_Avoid_: group, event, split (as a noun)

**Member**:
A named person on a Trip. The user is always one of them; the others don't use the app.
_Avoid_: participant, friend, person

**Trip expense**:
One payment made during a Trip: an amount, date, purpose, the one Member who paid, and how it is split between Members. It is not a Transaction.
_Avoid_: trip transaction, bill

**Share**:
The part of a Trip expense that one Member owes.
_Avoid_: portion, cut

**Balance**:
What a Member paid on a Trip minus their Shares and adjusted by Settlements. Positive means they are owed money.

**Settlement**:
A repayment between two Members that moves their Balances towards zero. It never counts as spending.
_Avoid_: reimbursement, payback

**Settle-up plan**:
The fewest payments between Members that bring every Balance to zero.
_Avoid_: simplified debts

**Post**:
Turning the user's Shares of a Trip into Transactions, after the user reviews and edits them. Posting archives the Trip.
_Avoid_: push (that word means sending Transactions to the Sheet), export, sync

## Entry points

**Quick log**:
Adding a Transaction without opening the main app, from the Quick Settings tile, the home-screen widget or the app shortcut.

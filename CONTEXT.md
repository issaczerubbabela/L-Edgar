# SheetSync

An offline-first Android expense tracker. Each user keeps their data on their phone and mirrors it to a Google Sheet they own, through an Apps Script they deploy themselves.

## Money

**Transaction**:
One movement of money: an Expense, an Income, a Transfer or a Balance adjustment. Stored as `ExpenseRecord` in `expense_records`.
_Avoid_: record, entry, expense (when you mean any type)

**Transfer**:
A Transaction that moves money from one Account to another. It has a from-account and a to-account and no Category.
_Avoid_: payment between accounts

**Account**:
A place money is held or owed, such as a bank account, card, loan or cash wallet. Its balance is its Initial balance plus the Transactions dated after its As-of date. A balance below zero means money owed.
_Avoid_: asset (the UI's "Edit All Assets" means Accounts), payment mode

**Balance adjustment**:
A Transaction that only corrects one Account's balance to match the bank, for the difference found when Reconciling. It is never Earned, Spent or Saved.
_Avoid_: correction, manual balance edit

**Reconcile**:
Typing in what the bank shows for an Account today, so the app adds a Balance adjustment for any difference. The Initial balance stays as it was.
_Avoid_: edit balance, sync (for this)

**Account group**:
The named group an Account belongs to, picked from the `ACCOUNT_GROUP` Dropdown options.

**Liability**:
An Account in an Account group with the Liability role, such as a credit card or loan. Liabilities are the money owed across them: minus the sum of their balances, so an overpaid card lowers it.
_Avoid_: debt group (keywords in a group's name no longer decide this)

**Net worth**:
The sum of the balances of every Account included in totals: Assets minus Liabilities. It is the "Total" on the Accounts tab.
_Avoid_: balance (for the total), total assets

**Cash flow**:
Money moving into and out of the Accounts Included in totals in a period. A Transfer counts only when it crosses that boundary; Balance adjustments are shown apart from it.
_Avoid_: income/expense (for this; those are Earned and Spent)

**Included in totals**:
Whether an Account counts towards Assets, Liabilities and Net worth. It doesn't affect whether the Account is shown.

**Hidden**:
An Account left out of the Accounts list and the account pickers. A Hidden Account still counts in totals unless it is also not Included in totals.

**Archived**:
An Account that is both Hidden and not Included in totals, kept so its Transactions still have an Account.
_Avoid_: deleted (for an account that still exists)

**Initial balance**:
An Account's balance at the end of its As-of date (`initialBalanceDate`), as the bank would show it that evening. Transactions dated on or before the As-of date are already in it, so they don't count again.
_Avoid_: opening balance, starting balance

**Category**:
The label that classifies an Expense or Income, picked from the `EXPENSE_CATEGORY` or `INCOME_CATEGORY` Dropdown options.

**Budget**:
The legacy plan: an amount for one Category in one month. Each month and Category pair has at most one. Salary cycles replaced it, but it's still kept and backed up so old backups restore.

**Salary cycle**:
The money the user has to spend between two paydays. The running cycle has no close date, and it stays open past its end date until the next one starts, counting spend up to today; anything dated after both its end date and today is left for later.
_Avoid_: month, period (a cycle rarely lines up with either)

**Bucket**:
A named pot of money inside one Salary cycle. Buckets belong to their cycle, so carrying them into the next cycle copies them.
_Avoid_: envelope, budget (the legacy per-category plan)

**Bucket category**:
The routing of one expense Category into one Bucket for one cycle. A Category sits in at most one Bucket per cycle; Categories in none count as Unbucketed.

**Unallocated**:
The part of a Salary cycle's spendable amount not yet planned: spendable minus every Bucket's allocation and every Fund's Planned contribution. It's a planning figure, so spending doesn't change it; Unbucketed spend is shown next to it, not taken out of it.
_Avoid_: buffer, spare

**Fund**:
A label on money for a purpose, such as an emergency fund or a ministry fund. It lives outside Salary cycles and keeps its balance until the money is spent or released. It can have a target and a due date. It's a label, not a place: the money stays in the Accounts its Holdings name.
_Avoid_: sinking fund, goal, pot, rollover

**Holding**:
Where a Fund's money sits: either a **whole Account**, whose balance is the holding (a recurring deposit, a SIP, an Account kept only for giving), or a **fixed amount** in a shared Account, such as ₹70,000 of the salary Account. An Account is the whole holding of at most one Fund. A holding can start with an opening amount for money that was there before tracking began.
_Avoid_: kept in, earmark (as a noun)

**Free**:
Money in an Account, or in all of them, that no Fund holds and no running Salary cycle still plans to spend: Your money minus the Funds held in it (holdings in Accounts Included in totals) minus what is left of This cycle (never less than zero). Free can go below zero, which means the plan uses more money than there is.
_Avoid_: available, spare

**Your money**:
Net worth over the Accounts Included in totals, as the Budget tab shows it, split into Funds, This cycle and Free.
_Avoid_: balance, total

**Set aside**:
A Transfer in a Salary cycle from an Account no Fund holds whole into an Account a Fund holds whole. It's how a cycle puts money into a Fund. A Transfer between two such Fund Accounts is a move between Funds, and interest, growth and Balance adjustments grow a Fund without being Set aside.
_Avoid_: saved (Stats' figure), invested

**Planned contribution**:
How much a Salary cycle means to Set aside into one Fund. Carry over copies it into the next cycle, like a Bucket's allocation.
_Avoid_: set-aside amount, instalment

**Stats role**:
The part a Dropdown option plays in Stats, stored in its `role`: an expense Category can be **Saving**, an income Category can be **Refund**, and an Account group can be **Savings** or **Liability**. Most options have no role.
_Avoid_: flag, tag

**Earned**:
Income in a period, not counting Refunds.
_Avoid_: total income (when Refunds are included)

**Spent**:
Expenses in a period, not counting Saving Categories, minus Refunds.
_Avoid_: total expenses (when Saving Categories are included)

**Saved**:
Money put aside in a period: Expenses in Saving Categories, plus every Set aside Transfer (from an Account no Fund holds whole into one a Fund does, whatever its Account group). A Transfer between two Fund Accounts is a move, and a Transfer out of a Fund's Account to one no Fund holds whole is taken back, as the Savings group's were before Funds (the Budget tab's Set aside never nets them). Transfers that touch no Fund's Account still follow the Account groups: into a Savings group from outside it counts, out of one is taken back. Saved money is never Spent; an Expense paid from a Fund's Account is still Spent on its date. Stats breaks Saved down by Fund.
_Avoid_: invested, spent (for Saving Categories)

**Refund**:
An Income in a Refund Category. It reduces Spent instead of adding to Earned, and it isn't tied to any expense Category.

**Left over**:
Earned minus Spent minus Saved, for a week, month, year or custom range.
_Avoid_: kept, balance, savings

**Net**:
Income minus Expenses for the Transactions on screen, counted as they are: Saving Categories count as Expenses and Refunds as Income. Transfers and Balance adjustments are left out. It's what the Ledger shows, so it adds up to the rows you can see; Stats uses Earned, Spent and Left over instead.
_Avoid_: total, balance, left over (for this)

**Ledger**:
The tab that lists every Transaction, one month at a time: day by day, on a calendar, or as a year of months.
_Avoid_: Trans., history

**Left to spend**:
A Salary cycle's spendable amount minus every Expense in it, Saving Categories included and Refunds not taken off, minus its Set aside: the same figure the Budget tab shows, because a Bucket can hold savings. An Expense paid from an Account a Fund holds whole comes out of that Fund, not the cycle. Once the cycle is over, the Stats tab calls it "Left unspent". The Budget tab also calls it **This cycle** when splitting Your money.
_Avoid_: left over (which is based on Earned, not the spendable amount)

**Still due**:
The Expenses that Repeat rules will still add to a running Salary cycle before its end date: every occurrence not added yet, Ask me first ones waiting included, but not paused rules or Expenses paid from an Account a Fund holds whole. It's never taken off Left to spend; once added, it's an ordinary Transaction and counts as spent.
_Avoid_: upcoming bills, scheduled

**Daily pace**:
What a running Salary cycle can spend per day from today to its end date: Left to spend minus Still due, over the days left (the last day counts as one), never below zero. It's the only pace: every screen that shows one uses this. An overdue cycle has none.
_Avoid_: per day, burn rate

**Usual**:
What a Category normally costs in Stats: its average over the three periods of the same kind before this one (months for a month, cycles for a cycle).
_Avoid_: average (without saying over what)

**Chart palette**:
The set of colours Stats charts use for money in, money out and saved, picked in Settings. Every palette except Classic keeps the three apart for red-green colour blindness.

**Dropdown option**:
A user-editable choice for Categories and Account groups. `PAYMENT_MODE` options are legacy: Accounts replaced them, and they are never backed up.

**Bookmark**:
A flag the user puts on a Transaction to find it again in the Bookmarks list.

**Repeat rule**:
A saved Transaction template with a cadence (weekly, every 2 weeks, monthly, every 3 months or yearly). On each due date it either adds a Repeating transaction by itself or, if set to Ask me first, waits for the user. Changing or deleting a Repeat rule never touches the Transactions it already added.
_Avoid_: recurring rule, schedule, scheduled transaction

**Repeating transaction**:
A Transaction a Repeat rule added. Once added it is an ordinary Transaction: it syncs, counts and can be edited or deleted like any other.
_Avoid_: recurring transaction, upcoming transaction

**Suggested repeat**:
Transactions the user already logs on a schedule (weekly, monthly or every 3 months, each within a few days of its date and within 10% of the same amount), found in their own Ledger and offered under Suggested for you on More > Repeating transactions. Make it repeat turns one into an Ask me first Repeat rule; Not a repeat hides it for good on this phone. Neither changes the Transactions it was found in.
_Avoid_: detected subscription, recurring stream

## Auto-capture

**Alert**:
One message about money moving that auto-capture reads: a bank SMS (through Google Messages), a bank or UPI app's notification, or a bank's or Amazon Pay's email (through the Gmail notification). Where it came from is its **Alert source**.
_Avoid_: message, notification (as the domain noun)

**Captured transaction**:
One payment the app has read from one or more Alerts but not yet turned into a Transaction. Alerts from different Alert sources about the same payment become one Captured transaction (ADR-0012). It waits in the review inbox until the user acts on it.
_Avoid_: pending transaction, alert, capture (as a standalone noun)

**Not-a-payment alert**:
An Alert that shows an amount but reports no money moving: a payment request, a mandate set up or revoked, a deduction still to come, an instalment due, a declined or failed payment, a refund only initiated, or an offer. It is dropped: never a Captured transaction and never an Unparsed alert.
_Avoid_: promo, false positive

**Confirm**:
Turning a Captured transaction into a real Transaction. Only the user confirms; nothing about auto-capture does this on its own.
_Avoid_: auto-log, auto-add

**Link**:
Resolving a Captured transaction as the bank's alert for a Transaction the user had already logged (by hand, with Quick Add or by a Repeat rule), instead of confirming it into a second one. Like Confirm, only the user links ("Same one"); nothing new is written to the Ledger. A Captured transaction that looks like a logged Transaction is a **Possible duplicate** until the user decides.
_Avoid_: merge, match (as the user's action)

**Merchant rule**:
A saved mapping from a normalized merchant name and the bank's direction (debit or credit) to an outcome, and to the description the user last renamed it to, used for future Captured transactions from the same merchant. The outcome is an Expense or Income with a Category, or a Transfer with the other Account (the To-account of a debit, the From-account of a credit). It is either set by the user or learned from repeated identical confirmations. Changing the type or Category of a captured Transaction in the Ledger counts like a Confirm with that outcome. A rule whose Category the user no longer has is passed over, as if there were none, keeping only its rename.
_Avoid_: category rule

**Account alias**:
A saved mapping to one of the user's Accounts from what an Alert says about where the money moved: a bank account's last digits, or, for an Alert source with no digits (Amazon Pay Balance), the source itself. It is how a Captured transaction gets its Account.
_Avoid_: account mapping

**Unparsed alert**:
An Alert that showed an amount but that no parser could read, and that isn't a Not-a-payment alert. It is kept for 30 days so a change in a bank's wording can be noticed and supported.
_Avoid_: failed alert

**Confidence band**:
High, Check or Low: how sure auto-capture is about a Captured transaction's suggested Category. Only a saved Merchant rule reaches High (ADR-0010); a guess from history or the on-phone guesser is at most Check. Only High, with its Account already resolved, notifies the user; everything else waits in the inbox.
_Avoid_: confidence score

**Ready**:
A waiting Captured transaction that could be confirmed as it stands: it has a Category (or a Transfer's other Account), an Account that isn't merely guessed, and no Transaction in the Ledger it may repeat. To review filters by it, and the bulk sheet lists a Ready Check capture unticked. Anything not Ready needs a pick.
_Avoid_: complete, confirmable

**Suggested description**:
The Description auto-capture guesses for a Captured transaction from the user's own Transactions, by merchant, amount, time of day and weekday (₹80 at 1pm is "Lunch", ₹30 at 4:30pm is "Tea"). It fills the capture's title, marked as a guess, with up to two alternatives as chips. A saved rename on the Merchant rule wins over it until the merchant has had different Descriptions. It never changes the Confidence band, and keeping it doesn't rename the merchant.
_Avoid_: auto-description, predicted title

**Suggested Transfer**:
A waiting debit and credit that To review shows as one row because they look like one Transfer between the user's own Accounts: the same amount to the paisa, within 2 days, each the other's only match, and at least one side's digits an Account alias (ADR-0006). The user confirms it as one Transfer, which resolves both, or splits it back into two Captured transactions.
_Avoid_: merged capture, auto-transfer

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
The fewest Suggested payments between Members that bring every Balance to zero.
_Avoid_: simplified debts

**Suggested payment**:
One payment in the Settle-up plan, from a Member who owes to a Member who is owed. It becomes a Settlement once the user marks it paid.
_Avoid_: settlement (for a payment not yet made), debt

**Member colour**:
The colour that identifies a Member everywhere in a Trip. It never means money direction; gets back and owes keep their own colours.
_Avoid_: payer colour, tag

**Locked amount**:
A Share the user typed for one Member in a Custom split. Members without a Locked amount split what is left equally.
_Avoid_: adjustment, exact amount, override

**Post**:
Turning the user's Shares of a Trip into Transactions, after the user reviews and edits them. Posting archives the Trip.
_Avoid_: push (that word means sending Transactions to the Sheet), export, sync

## Entry points

**Quick log**:
Adding a Transaction without opening the main app, from the Quick Settings tile, the home-screen widget or the app shortcut.

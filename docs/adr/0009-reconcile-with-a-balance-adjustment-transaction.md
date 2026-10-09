---
status: accepted
---

# Reconcile an Account with a Balance adjustment, not by editing its Initial balance

When an Account's balance in L.Edgar differs from the bank's, the user Reconciles: they type the bank's figure and the app adds a Balance adjustment, a new Transaction type (`Adjustment`) for the difference, dated today on that one Account. The Initial balance is set when the Account is created and left alone after that. We chose this because editing the Initial balance silently rewrote every past balance, so last month's closing figure changed after the fact and the statement never showed where the gap came from.

## Considered Options

- **Keep editing the Initial balance.** No new type, but history moves under the user and the correction is invisible.
- **Re-anchor: set a new Initial balance with today as the As-of date.** Kept as "Start fresh from today" for a user who stopped tracking for a while, since it drops every earlier Transaction from the balance.
- **Record the gap as an Expense or Income in a special Category.** Rejected: it would leak into Earned, Spent, Buckets and Budgets.

## Consequences

- Earned, Spent, Saved, Buckets, Budgets and auto-capture ignore `Adjustment` Transactions; only Account balances count them.
- `Adjustment` rows sync to the Sheet's `_responses` tab like any Transaction, with the type column set to `Adjustment`, the Account in the account column and a signed amount (negative lowers the balance). Signed amounts already survive the Sheet round trip; from- and to-account columns don't, because Pull clears them for anything but a Transfer. An Apps Script that predates this stores them as-is, since the type is a free string.

---
status: accepted
---

# Stats roles live on Dropdown options

The Stats tab counted every Expense as spending and every Income as earnings. The monthly SIP (Category "Investments/Savings") then showed up as a fifth of all spending, and an Amazon refund (Category "Return") as income. Getting the numbers right needs to know which Categories are really saving, which are refunds, and which Account groups hold savings. Category and Account group names are user-editable, so hard-coding names would break as soon as someone renames one.

## Decision

- Dropdown options get one new `role` column (text, empty by default). The allowed values depend on the option type: `SAVING` for `EXPENSE_CATEGORY`, `REFUND` for `INCOME_CATEGORY`, and `SAVINGS` for `ACCOUNT_GROUP`.
- The Room migration gives the defaults once: "Investments/Savings" → `SAVING`, "Return" → `REFUND`, and the "Savings" and "Investments" Account groups → `SAVINGS`. The user can change them afterwards, and the migration never runs again.
- Stats counts money with these rules (see CONTEXT.md for the terms):
  - **Saved** is Expenses in Saving Categories, plus Transfers into a Savings Account group from outside it, minus Transfers out of one to outside it.
  - **Refunds** reduce Spent and don't count as Earned. They aren't tied to an expense Category, so category totals stay as they are and Refunds show as their own row.
- The role is part of the Dropdown option Backup. The `_dropdowns` tab gets a fifth `role` column. The app sends the role with each option, and Import reads it when it's there. A script deployed before this change keeps writing four columns and ignores the new field, so nothing breaks. The role just isn't backed up until the user redeploys.

## Considered options

- **A local preference listing Saving and Refund Categories.** Rejected: the role is a fact about the Category, it should survive a reinstall and an Import, and a preference would need its own rename handling.
- **Separate boolean columns (`countsAsSaving`, `countsAsRefund`).** Rejected: each role only makes sense for one option type, and three columns where two are always false for any given row is noise.
- **Letting the user pick which Category a Refund offsets when entering it.** Deferred: it would change Add Transaction, the Transaction schema and the Sheet, and the user doesn't need per-category accuracy yet.

## Consequences

A user whose everyday bank account is in the "Savings" Account group would see card-bill payments counted as money taken out of savings. Until the "Counts as…" control ships (Stats redesign step 4), the fix is to move that account into "Accounts".

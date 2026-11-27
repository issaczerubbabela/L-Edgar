---
status: accepted
---

# Funds are labels on Account money

The user wanted to see where all of their salary goes, not just the part spent through Buckets. They also wanted money in their Accounts that is meant for something to stay meant for it across Salary cycles. Their monthly recurring deposits, SIP and ministry money are logged as Transfers, so Stats shows no spikes. That also means the Budget never sees them, because it only counts Expenses. A matured deposit sat in the salary Account mixed in with spending money, and nothing in the app kept the two apart.

## Decision

- A **Fund** is a label on Account money that lives outside Salary cycles (CONTEXT.md). It has **Holdings**: either a whole Account (its balance is the holding) or a fixed amount in a shared Account, with an opening amount for money that was there before tracking.
- **Draws are by Account only.** An Expense paid from an Account a Fund holds whole comes out of that Fund, and every other Expense counts in the cycle. No Transaction is linked to a Fund, so `expense_records`, the Sheet's `_responses` tab and the sync contract don't change (ADR-0001, ADR-0003).
- **Set aside** is a Transfer from an Account no Fund holds whole into one that a Fund does. A Transfer between two Fund Accounts is a move. Balance adjustments (ADR-0009), Income and interest grow a Fund but are never Set aside.
- A cycle splits as Buckets + Planned contributions + Unallocated. **This cycle** (Left to spend) = spendable − Expenses not drawn from a Fund − Set aside. **Free** = Your money − Funds − max(This cycle, 0), so a Transfer into a Fund leaves Free unchanged and overspending never makes Free look larger.
- **The label doesn't follow the money.** A Transfer out of a Fund's Account releases it; the app offers "Keep earmarked" to add a fixed Holding where it lands.
- Funds are plan data, like Buckets. They aren't throwaway local state like captures (ADR-0005) or Trips (ADR-0008), so they're backed up through the Apps Script from the first slice that creates them. Backups key Holdings by Account **name** and Planned contributions by cycle **start date**, because Import rebuilds Account and cycle ids.
- Stats will count a Transfer into an Account a Fund holds whole as **Saved**, and take back one out of it into an Account no Fund holds whole, whatever the Account groups. This supersedes the Transfer half of ADR-0004's Saved rule once that slice ships, and Transfers into a Savings group with no Fund keep counting.

## Considered options

- **Rollover on Buckets (#111).** Rejected: it only carries money between cycles inside the Budget, so it can't label money that never came from a salary, like a matured deposit.
- **A label that follows Transfers.** Every Transfer out of a Fund's Account would move the label onto the destination. Rejected: it means replaying every Transfer, it double-counts Fund-to-Fund moves, and a Sheet edit to a Transfer would rewrite Fund history after the fact.
- **A Paid-from link on each Expense**, with Category defaults and overrides. Deferred: it needs its own table, its own backup and a picker on Add Transaction. Drawing by Account covers the real cases (giving from the ministry Account, spending a deposit after moving it).
- **The 2026 sinking-funds plan** (target-driven funds owning Categories, draws and spills). Folded in: a target and due date are optional on a Fund, and Category ownership waits for the Paid-from work.

## Consequences

- A yearly bill paid from the salary Account still counts in the cycle, even if a Fund was saving for it. Move the money to the Fund's Account first, or wait for Paid from.
- A Holding in an Account not Included in totals is counted in Funds but not in Your money. The app flags it instead of guessing.
- Renaming an Account must re-send its Transactions to the Sheet (#152). Otherwise Import, which matches Accounts by name, loses track of a Fund's Account.
- A chit, or anything else logged as an Expense, stays outside Funds until it is logged as a Transfer.

---
status: accepted
---

# Post the user's Share of a Trip, not what they paid

When a Trip is Posted, each Trip expense becomes one Expense Transaction for the user's Share only, dated on the expense date, in the expense's own Category, from one Account the user picks on the review screen. What the user fronted for others, what others fronted for the user, and every Settlement are never written to the ledger. We chose this because budgets, Buckets, Usuals and Stats should show what the user consumed: a ₹3,000 dinner split three ways is ₹1,000 of Food & Snacks, and a hotel Rahul paid for still costs the user their Share.

## Considered Options

- **Post the Share plus the money lent, as a Transfer into a "Trip receivables" Account, with repayments as Transfers back.** This keeps every Account balance exact, but it needs a receivable Account the app doesn't have, and the user preferred a ledger that only shows their own spending.
- **Post the full amount the user paid.** The simplest option, but it overstates spending whenever the user pays for the group and records nothing when someone else pays.

## Consequences

- Until a Trip is fully settled, the Account the user paid from shows a different balance in L.Edgar than at the bank. The user fronted more than their Share, and the repayments they receive are not recorded. Once every Settlement goes through that Account, the totals agree again.
- Auto-capture will see both the full payment and the incoming repayments. The full payment should be turned into a Trip expense with "Add to trip", and the repayments should be dismissed. Confirming either one as a personal Transaction double-counts.
- Each posted Transaction is linked back to its Trip expense. That link is what lets Un-archive delete exactly what the Post created.

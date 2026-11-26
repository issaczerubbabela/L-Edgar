# Auto-capture: suggest Transfers between my Accounts, confirm them from the notification, learn from Ledger edits

Ticket: [#143](https://github.com/issaczerubbabela/L-Edgar/issues/143) · Branch: `claude/issue-143-capture-transfer-suggestions` · Part of the [Auto-capture improvements spec](README.md)

## Problem
Three gaps remain after [#140](https://github.com/issaczerubbabela/L-Edgar/issues/140) and [#141](https://github.com/issaczerubbabela/L-Edgar/issues/141):
1. A self-transfer still arrives as an Expense and an Income. I have to notice it and switch it to Transfer myself.
2. A High Transfer rule (for example CRED → Transfer to the HDFC credit card) can't be confirmed from the notification.
3. Recategorising a captured Transaction later in the Ledger teaches the app nothing.

## Proposal
**Transfer suggestions (ADR-0006).**
- When a waiting DEBIT and CREDIT have the same amount (in paise), are within 2 days of each other, and **at least one side's digits resolve to an Account alias**, show both cards as one suggested Transfer, From → To. Amount and date alone never pair them.
- The user still Confirms it, or splits it back into two captures. One Undo covers both.
- Reuse the matcher from [#125](https://github.com/issaczerubbabela/L-Edgar/issues/125) and from [#140](https://github.com/issaczerubbabela/L-Edgar/issues/140)'s pairing.

**Notification Transfers.**
- A capture whose R1 rule is Transfer+Account, and whose own Account is known, posts the Confirm notification. Its unlocked text reads "Transfer ₹X to HDFC Credit Card", and the lock screen text stays generic.
- Confirm runs the same pairing as the inbox.

**Learning from Ledger edits.**
- When a Transaction with a linked capture (`confirmedExpenseId`) has its Category or type changed in the Ledger, record an override (or a confirmation of the new outcome) on that merchant's rule, exactly as a Confirm with that outcome would.
- Amount, date and Description edits don't teach the rule.

## Notes
- Blocked by [#140](https://github.com/issaczerubbabela/L-Edgar/issues/140) and by [#125](https://github.com/issaczerubbabela/L-Edgar/issues/125), which shares the matcher.
- Branch: `claude/issue-143-capture-transfer-suggestions`.
- Update `docs/AUTO_CAPTURE.md` §8 and §12 (the "Two captures are never merged" line goes).

## Acceptance criteria
- [ ] ₹5,000 DEBIT (HDFC, aliased) + ₹5,000 CREDIT (unknown digits) on the same day are shown as one suggested Transfer HDFC → ?. With neither side aliased they are not.
- [ ] Confirming the suggestion writes one Transfer, and both captures are resolved. Undo restores both.
- [ ] A High Transfer rule capture with a known Account posts the notification, and its Confirm writes the Transfer.
- [ ] Recategorising a captured Transaction twice in the Ledger drops a LEARNED rule, as two inbox overrides would.
- [ ] Changelog `added` entry, e.g. "Auto-capture: spots transfers between your own accounts and lets you confirm them from the notification".

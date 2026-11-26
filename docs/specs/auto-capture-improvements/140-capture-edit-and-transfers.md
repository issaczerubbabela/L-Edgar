# Auto-capture: make a capture a Transfer or edit it in full, and match its other half

Ticket: [#140](https://github.com/issaczerubbabela/L-Edgar/issues/140) · Branch: `claude/issue-140-capture-edit-and-transfers` · Part of the [Auto-capture improvements spec](README.md)

## Problem
In To review, a capture's type is fixed by the bank's debit or credit. I can change its Category, Account and title, but not its amount, date or Remarks, and I can't make it a **Transfer**. Paying my credit card, moving money between my own accounts, or paying CRED all have to be Dismissed and typed again by hand. Each self-transfer also produces two captures (the debit and the credit), and confirming both counts the money twice.

## Proposal
**On the card.** Add an **Expense / Income / Transfer** switch.
- **Transfer** replaces the Category pick with an Account pick for the other side.
- A DEBIT capture's Account is the From-account and the user picks the To-account. A CREDIT capture's Account is the To-account and the user picks the From-account. The bank's direction is never flipped.

**Edit….** It opens the Add Transaction form filled in from the capture: amount, date, type, Account(s), Category, Description, Remarks.
- Saving there is Confirm: it goes through `CaptureRepository.confirm` with the extra fields, so learning, aliases, pairing and the 5-second Undo are identical.
- The form shows the "Always use this for <merchant>" checkbox when it was opened from a capture.
- Back leaves the capture waiting, unchanged.

**Merchant rules can mean a Transfer.**
- `merchant_rules` today has `merchantNorm` as its only primary key and a `type` of Expense or Income. Change the key to (`merchantNorm`, `direction`) and add `toAccountId`, so a rule's result is Expense+Category, Income+Category, or Transfer+other-Account.
- Room migration v26 → v27: existing rules map Expense to DEBIT and Income to CREDIT.
- Learning is unchanged: CANDIDATE, then LEARNED after 3 identical outcomes in a row, dropped after 2 overrides, and USER rules are never changed. An "identical outcome" now compares type plus Category or other-Account.
- R1 then suggests Transfer+Account at 0.98 (High).

**Transfer pairing on Confirm (ADR-0006).**
- When a Transfer is confirmed, look for one waiting capture with the same amount (compared in paise), the opposite direction, within 2 days, whose account digits resolve, through an Account alias, to the Transfer's other Account.
- If exactly one is found, resolve it in the same Room transaction with a new status `PAIRED` and the same `confirmedExpenseId`.
- The snackbar reads "Transfer saved, and the matching HDFC credit too", and Undo restores both captures.
- If there's more than one candidate, pair none. Leave them waiting and say so in the snackbar.

## Notes
- Branch: `claude/issue-140-capture-edit-and-transfers`.
- Mark ADR-0006 accepted. Update CONTEXT.md (Merchant rule) and `docs/AUTO_CAPTURE.md` §1, §6 and §7.
- Sync: none beyond the ordinary unsynced `ExpenseRecord` a Confirm already writes. Capture tables stay local-only.

## Acceptance criteria
- [ ] A ₹5,000 DEBIT capture from HDFC switched to Transfer with To-account CUB confirms as one Transfer, HDFC → CUB.
- [ ] A waiting ₹5,000 CREDIT capture whose digits are CUB's alias, dated within 2 days, becomes `PAIRED` in the same Confirm. Undo restores both. Two candidates means neither is paired.
- [ ] Edit… opens Add Transaction filled in. Changing the amount, date and Remarks there and saving writes those values. Learning and Undo behave as on the card.
- [ ] Migration v26 → v27 keeps every existing rule, with Expense → DEBIT and Income → CREDIT. There's a migration test.
- [ ] A merchant confirmed as Transfer to CUB three times in a row gets a LEARNED Transfer rule, and its next capture is High with the switch already on Transfer.
- [ ] Changelog `added` entry, e.g. "Auto-capture: make a capture a Transfer or edit it in full, and its other half is matched for you".

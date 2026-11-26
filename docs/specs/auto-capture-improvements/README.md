# Auto-capture: edit captures, Transfers, smarter guesses and a faster To review

Spec: [#138](https://github.com/issaczerubbabela/L-Edgar/issues/138). The GitHub issues are the live tickets, and this folder is the saved copy agreed in the design session (2026-11-26).

## Goal
Auto-capture should take less work. Waiting captures pile up on the days I don't open the app, and clearing them one card at a time, typing a Description for each, is the pain point. This issue is the spec, and each slice below is its own ticket and PR.

## Decisions (agreed in a design session)
1. **Editing a capture.** The card gets a quick **Expense / Income / Transfer** switch next to the Category and Account picks. **Edit…** opens the full Add Transaction form filled in from the capture: amount, date, type, Account, To-account, Category, Description, Remarks. Saving there **is** Confirm, with the same learning, "Always use", Transfer pairing and Undo.
2. **Direction is fixed by the bank.** A debit puts the alert's Account on the From side of a Transfer, and a credit puts it on the To side. A wrong direction is a parser bug, not a per-capture edit.
3. **A Merchant rule can mean a Transfer.** Rules are keyed by merchant and **direction**. A rule's result is Expense+Category, Income+Category, or **Transfer+To-account**. The learning stays the same: Always use, LEARNED after 3 confirmations in a row, dropped after 2 overrides.
4. **Transfer pairing on Confirm.** Confirming a Transfer also resolves the waiting capture on the other side: same amount, opposite direction, within about 2 days, and its account digits match the other Account's alias (ADR-0006). Undo covers both.
5. **The predictor runs in this order, all on the phone:**
   1. R1, a saved rule (0.98, the only step that reaches **High**).
   2. **R2, history match** on past Transactions, including hand-typed ones.
   3. **Naive Bayes** on merchant words, amount range and time of day, capped at **Check**.
   4. R7, the keyword list.

   It also **guesses the Account from the source app** when the digits are unknown.
6. **Suggested description.** The top guess is filled in as the title and 2 alternatives show as chips. It learns from merchant, amount, time of day and weekday over every Transaction. It never changes the Confidence band.
7. **Re-guessing.** After each Confirm, waiting captures are guessed again, but only in fields the user hasn't touched.
8. **To review redesign.**
   - Grouped by day, with each day's total.
   - Compact rows that expand when tapped.
   - Swipe right to Confirm and left to Dismiss, with Undo.
   - **All / Needs a pick / Ready** filters.
   - **Confirm day** in each day's header.
9. **Bulk sheet.** High rows are ticked, Check rows with a Category and Account are listed unticked, and Low rows are left out. Every row is still the user's tap, so ADR-0005 holds.
10. **The notification Confirm covers Transfers** under the same High-and-Account-known condition. Its text reads "Transfer to <Account>".
11. **Ledger edits teach.** Changing the Category or type of a Transaction linked to a capture (`confirmedExpenseId`) counts as an override for its rule.
12. **Out of scope:** splitting one payment across Categories ([#51](https://github.com/issaczerubbabela/L-Edgar/issues/51)) and a one-at-a-time triage mode.

## Slices (in order)

Each ticket's saved copy sits next to this file, named after its branch.

- [ ] [#91](https://github.com/issaczerubbabela/L-Edgar/issues/91): "debited … NAME credited" alerts are an Expense (fixed by PR [#96](https://github.com/issaczerubbabela/L-Edgar/pull/96), waiting to merge)
- [ ] [#139](https://github.com/issaczerubbabela/L-Edgar/issues/139) VPA debits: show who I paid (`claude/issue-139-capture-vpa-merchant`)
- [ ] [#140](https://github.com/issaczerubbabela/L-Edgar/issues/140) Transfers and full editing (`claude/issue-140-capture-edit-and-transfers`)
- [ ] [#141](https://github.com/issaczerubbabela/L-Edgar/issues/141) Predictor (`claude/issue-141-capture-predictor`)
- [ ] [#142](https://github.com/issaczerubbabela/L-Edgar/issues/142) To review redesign (`claude/issue-142-capture-inbox-redesign`)
- [ ] [#125](https://github.com/issaczerubbabela/L-Edgar/issues/125) Link a capture to a Transaction I already logged (`claude/issue-125-capture-link-logged-transaction`)
- [ ] [#143](https://github.com/issaczerubbabela/L-Edgar/issues/143) Transfer suggestions, notification Transfers, Ledger learning (`claude/issue-143-capture-transfer-suggestions`)

Each slice branches from `main` as `claude/issue-<n>-<slug>`, after the slice before it merges.

## Docs that change with the slices
- ADR-0006 becomes accepted.
- New ADR-0010, "Only a saved rule reaches High".
- CONTEXT.md: the Merchant rule entry (it can mean a Transfer), and a new **Suggested description** term.
- `docs/AUTO_CAPTURE.md` sections 1, 6, 7 and 12. Section 12's "no machine-learning model" line becomes "an on-phone Naive Bayes guesser, no network".

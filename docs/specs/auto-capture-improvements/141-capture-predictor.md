# Auto-capture: guess Category, Description and Account from what I logged before

Ticket: [#141](https://github.com/issaczerubbabela/L-Edgar/issues/141) · Branch: `claude/issue-141-capture-predictor` · Part of the [Auto-capture improvements spec](README.md)

## Problem
Only a saved rule (R1) or a keyword (R7) suggests a Category. A merchant I've confirmed twice still says "Needs a category". No capture ever suggests a Description, so on the days I skip the app I end up with a backlog of "Payment" rows to name one by one, even though my lunch and snack spends repeat at the same merchants and amounts.

## Proposal
Add two steps between R1 and R7, plus Description and Account guesses. Everything runs on the phone with no network.

**R2, history match.**
- Look up the merchant (normalised) among the captures I've confirmed and the Transactions whose Description matches the merchant's saved rename.
- Suggest the Category I picked most often. Confidence comes from consistency: 4 of 5 the same gives about 0.8. It's capped at 0.89, so it never reaches High.
- Why text: "You picked Food for this 4 of 5 times".

**Naive Bayes.**
- A multinomial model over merchant tokens, an amount bucket (log-scaled), a time-of-day bucket and weekday/weekend. It is trained on **every** Expense and Income in `expense_records`, hand-typed ones included, and predicts the Category.
- Capped at Check (at most 0.89). It's only used when its top probability clears a threshold (start at 0.6, and tune on the user's own data in a unit test with fixtures).
- Why text: "Guessed from similar spends".
- Keep it in pure Kotlin in `capture/categorize/`. Rebuild the counts in memory from Room when ingesting or re-guessing, with no new table unless profiling shows a need.

**Suggested description.**
- Predicts the Description from merchant, amount, time of day and weekday over every Transaction. A merchant seen before outweighs everything else, and amount and time decide between that merchant's usual Descriptions (₹80 at 1pm → "Lunch", ₹30 at 4:30pm → "Tea").
- The top guess is filled in as the capture's title (marked as a guess), and up to 2 alternatives show as chips. Tapping a chip replaces the title.
- A saved rename on the merchant's rule still wins over the guess.
- It never changes the Confidence band.

**Account guess.** When the alert's digits have no Account alias, but the source app is a bank app (for example HDFC) and the user has exactly one Account that an existing alias for that bank points to, suggest it. It's shown as a suggestion and never saved as an alias until Confirm.

**Re-guessing.** After each Confirm (and after Undo becomes final), re-run the guesses on waiting captures, changing only the fields the user hasn't touched. The existing rule, "never changes a Category already chosen", still holds.

## Notes
- Blocked by [#140](https://github.com/issaczerubbabela/L-Edgar/issues/140) (rules keyed by direction).
- Branch: `claude/issue-141-capture-predictor`.
- Add **ADR-0010, "Only a saved rule reaches High"**: history match and Naive Bayes stop at Check, so the notification Confirm and the ticked bulk rows are only ever backed by a rule.
- Update CONTEXT.md (new term **Suggested description**) and `docs/AUTO_CAPTURE.md` §6 and §12 (Naive Bayes is built and runs on the phone).

## Acceptance criteria
- [ ] A merchant confirmed as Food 4 times and Snacks once, with no rule, suggests Food in Check with the "4 of 5" why text.
- [ ] With Transactions "Lunch" ₹80 around 1pm and "Tea" ₹30 around 4:30pm at the same merchant, a new ₹80 capture at 12:50 suggests "Lunch" with "Tea" as a chip.
- [ ] Naive Bayes never yields High, so a capture whose only guess comes from it never notifies and isn't ticked in bulk.
- [ ] Confirming one capture updates the guessed Category and Description on other waiting captures from the same merchant, unless the user edited them.
- [ ] Unit tests for R2, Naive Bayes (fixtures, threshold, cap), the description ranking and re-guessing.
- [ ] Changelog `added` entry, e.g. "Auto-capture: guesses Category and Description from what you logged before, like Lunch or Tea".

---
status: accepted
---

# Only a saved rule reaches High

Auto-capture guesses a Captured transaction's Category in order: a saved Merchant rule (R1), a match on the user's own history (R2), an on-phone Naive Bayes guesser, then the keyword list (R7). Only R1 gives High confidence (0.98). R2's confidence is how consistent the user's past picks are and Naive Bayes' is its top probability, and both are capped at 0.89, so they are at most Check. We chose this because High is what lets the app act with the least of the user's attention: a High capture with a known Account posts the notification whose Confirm saves without opening the app, and it is ticked in the bulk sheet. A rule is something the user made ("Always use this") or earned with three confirmations in a row, so they have already agreed to it. A history match or a statistical guess has not been agreed to, however consistent it looks: "4 of 5 times" also means one time in five it was something else. Capping them keeps every one-tap or ticked Confirm backed by a rule, and leaves ADR-0005's "every Confirm is the user's" true in spirit as well as letter.

## Considered Options

- **Let a very consistent history (say 10 of 10) reach High.** It would save taps on merchants the user never bothered to make a rule for, but a learned rule already appears after three same-Category confirmations, so the gap is small, and the notification would start confirming things the user never agreed to.
- **Let Naive Bayes reach High above a probability bar.** Rejected: its probabilities are not calibrated (it is confidently wrong on a lone lunch at a coffee shop), and it learns from hand-typed Transactions the user never meant as a rule.

## Consequences

- R2 and Naive Bayes stop at 0.89, the top of Check. A capture whose only guess comes from them never notifies and is never ticked in bulk; it waits in To review with "Check this guess".
- A Suggested description and an Account guessed from the source app never change the Confidence band either, and a guessed Account doesn't count as a known one for the notification or for bulk.
- Making a guess High means saving a rule: "Always use this" on Confirm, or three confirmations in a row.

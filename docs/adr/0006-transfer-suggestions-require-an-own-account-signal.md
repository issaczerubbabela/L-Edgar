---
status: accepted
---

# Transfer suggestions require an own-account signal, not amount and date alone

Auto-capture can suggest that two Captured transactions (a debit and a matching credit) are really one Transfer between the user's own Accounts. The obvious match is same amount and same calendar day, but at that width two unrelated payments that happen to share an amount on the same day would merge into a false Transfer. We decided a match also needs at least one side's account or VPA to resolve to an existing Account alias — proof this is actually the user's own money moving, not a coincidence. The cost is that a real Transfer between two accounts neither of which is aliased yet won't be suggested until one of them is.

Accepted with #140, where pairing happens when the user confirms a Transfer: the waiting capture on the other side is resolved with it only if it is the one capture with the opposite direction, the same amount to the paisa, a time within 2 days, and account digits that are an alias of the Transfer's other Account. Two or more such captures pair none.

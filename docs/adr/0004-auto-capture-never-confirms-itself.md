---
status: proposed
---

# Auto-capture never confirms itself

Auto-capture parses bank and UPI notifications into Captured transactions, but nothing about it writes a Transaction or creates a Dropdown option on its own — only the user's Confirm does that, and it does it through the same repository path a manual entry uses. We chose this because a wrong automatic write (a misparsed amount, a Category guessed from a canonical key with no mapping yet) is far harder to notice and undo once it's in Room and synced to the Sheet than a suggestion sitting in a review inbox, and because ADR-0001 already treats every write as something the user chose, not something a background process decided for them. The cost is that even a correctly-guessed Captured transaction needs one confirming tap.

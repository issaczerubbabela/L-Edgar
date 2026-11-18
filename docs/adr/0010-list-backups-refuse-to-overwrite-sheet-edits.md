---
status: accepted
---

# List Backups refuse to overwrite a tab that changed in the Sheet

The phone's Accounts, Dropdown options, Budgets and salary cycles are phone-master: `BackupWorker` clears each Sheet tab and rewrites it about 20 seconds after any change, and any Transaction change counts, because the Accounts tab carries current balances. Import goes the other way. A user who edits a list tab by hand has to run Import before the next Backup, and the next Backup can come at any moment, such as a Pull on app open that changes a single Transaction. In November 2026 that happened twice in one session: a reorganised Category list typed into `_dropdowns` was silently replaced by the phone's old one. ADR-0003 promised that no edit on either side is ever silently overwritten, but it only covered Transactions.

## Decision

- **A revision per list.** The Apps Script hashes a list's whole tab content (every cell, the same FNV-1a as row revisions) into a list revision. `accounts`, `dropdowns` and `budgets` each cover their own tab; `bucket_budgets` covers `_cycles`, `_buckets` and `_bucket_categories` together. Every list read (`GET ?target=...`) and every successful Backup reply carries the revision, read back after writing.
- **A Backup carries its base.** The phone stores the last revision it read or wrote for each list (`SyncStateRepository`) and sends it as `baseRevision`. If the tab's revision is different, someone edited the Sheet since: the script writes nothing and replies `sheetChanged: true` with the tab's current revision.
- **The user decides.** A stale list is not a failure, so other lists and Transactions carry on. The phone marks the list as changed in the Sheet, and More shows "Lists changed in your Sheet" until the user picks one:
  - **Import from Sheet** runs Import, which takes in the Sheet's lists and stores their revisions.
  - **Overwrite Sheet** forgets the stored revisions of the changed lists and backs up at once, without a base.
- **No base means overwrite.** A Backup without `baseRevision` overwrites as before. That keeps the web client, older apps and Overwrite working, and covers the first Backup after upgrading, before the phone has stored any revision.
- **Older scripts.** A script from before this change (version 2) ignores `baseRevision` and returns no revision, so Backups overwrite as they always did. The app keeps working with it. The protection starts once the user redeploys version 3.

## Considered options

- **Make lists two-way, like Transactions.** Rejected for now: it needs a permanent ID and revision per row for six tabs, three-way merges for lists that reference each other by name (Accounts by group, Buckets by Category), and conflict screens for each. A revision per tab gives the guarantee that matters, nothing lost silently, for a fraction of that.
- **Only warn in the app before a Backup.** Rejected: the phone can't see a Sheet edit without reading the tab, and a warning the user can't act on in time doesn't stop the overwrite.
- **Hash rows instead of the whole tab, and merge row by row.** Deferred: it only pays off together with two-way lists.

## Consequences

- An edit in the Sheet now pauses that list's Backups until the user decides, so later phone changes to the same list don't reach the Sheet in the meantime. More says so.
- Any change to a list tab counts, including one by the web client or a hand edit that is later undone. The user then chooses Import or Overwrite once.
- Changing the stored format of a list tab (new columns, number formats) changes its revision. Script changes that rewrite tabs must keep that in mind, or users see one extra prompt.

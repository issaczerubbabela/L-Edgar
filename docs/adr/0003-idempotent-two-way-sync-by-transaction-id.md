---
status: accepted
---

# Idempotent two-way Sync keyed by a permanent Transaction ID

Transactions used to be matched to Sheet rows by their Remote timestamp, which was generated each time a Sync was attempted and only saved after the Apps Script confirmed. Whenever the Sheet wrote a row but the phone never got the reply (a timeout, a cancelled worker, a killed app), the retry sent a new timestamp and the script appended the row a second time. Updates that couldn't find their row appended too. The user also edits the Sheet by hand and reinstalls the app, so the Sheet has to be treated as a second place where changes happen, not just a mirror.

A phone and a Sheet can't share a real transaction, so we don't aim for ACID across them. We aim for this: each change reaches the Sheet exactly once however many times it's retried, and no edit on either side is ever silently overwritten.

## Decision

- **Transaction ID.** Every Transaction created on the phone gets a random ID (a Room trigger assigns it on insert), stored in Room and in the column headed "ID" in `_responses`. It never changes. When the phone reads the Sheet, the script gives an ID, under its lock, to any row without one (typed in by hand) or with a repeated one (a copied row).
- **Idempotent writes.** Script writes are upserts and deletes by Transaction ID, and deleting an ID that isn't there counts as success. Each request runs under `LockService`, so repeating a batch changes nothing.
- **Revisions.** The script gives every row a revision: a hash of its stored content cells, so any edit changes it, including one typed by hand. The phone stores the revision it last agreed on. Every upsert carries that revision as its `base`. If the row has changed since, the script refuses that row and lists it as stale, and the phone pulls and merges instead of overwriting.
- **Local change tracking.** Each Transaction has a version number that a trigger raises on every change. Sync settles a Transaction only if its version hasn't moved since Sync read it, so an edit or delete made during a Sync is never lost.
- **Pull.** On app open, on "Sync now" and after a stale refusal, the phone reads the whole Sheet and merges each Transaction ID three ways. The phone changed a Transaction if it has an unsynced change. The Sheet changed it if the row's revision differs from the stored one. If only one side changed, that side wins. If both did, the user resolves it as a Sync conflict. If both hold the same content (compared in one normalised form, amounts to the paisa), they simply settle.
- **Deletes from the Sheet.** A synced Transaction whose ID is gone from the Sheet is deleted on the phone. The exception is a pull that would delete more than 10 Transactions, or at least 3 that are more than 10% of them: the app holds those deletes and asks whether to delete them or put them back in the Sheet. An edit on the phone to a row deleted in the Sheet puts the row back.
- **Scheduling.** A watcher on Room requests a Sync whenever Transactions wait to sync, and a Backup whenever what Backups copy changes. Screens never schedule work. A new Sync request never cancels a running one. Backups run as a separate, delayed job.
- **Lists before Backups.** Before a phone's first Backup, and before the first Pull on a fresh install, the phone takes in the Sheet's Accounts, Dropdown options and Budgets that it lacks, matched by name. A fresh install's defaults can therefore never replace the Sheet's lists, and pulled Transactions land on the right Accounts.
- **Old scripts.** Every script reply carries its version. A script older than version 2 ignores version-2 requests (they never use `records`, which older scripts append as transactions), so the app detects it from the missing version, pauses Transaction Sync, and asks the user to redeploy. It never falls back to the timestamp path (see ADR-0002).
- **Upgrading.** Rows from before Transaction IDs are linked to their Sheet rows once, by Remote timestamp. An unlinked change that never reached the Sheet gets a new ID and goes up. An unlinked synced row counts as deleted in the Sheet, so the delete rules apply. Sheet rows left over from the old duplicate bug come in as separate Transactions for the duplicate review screen, and only the user deletes them.

## Considered options

- **Keep the Remote timestamp as the key, but save it before sending.** Rejected: it only goes down to the second, so values collide, and its text depends on the Sheet's time zone and formatting.
- **A separate outbox table of pending operations.** Rejected as unnecessary: the version number and stored revision on each Transaction give the same guarantees with less machinery.
- **Compare content fingerprints computed by the app to detect Sheet edits.** Rejected: the app and the script would each have to normalise values identically, and any drift would show as a false conflict. Revisions are computed only by the script.
- **Pull before every push.** Rejected as too heavy: it would read the whole Sheet on every save. A stale refusal gives the same protection, and a pull happens only when it's actually needed.

## Consequences

The work shipped in two phases. Phase 1 changed only the app and works with scripts already deployed: jobs no longer cancel each other, Backups no longer block Transactions, the lost-update bug is fixed, and the Remote timestamp is saved before sending. Phase 2 needs every user to redeploy the script: Transaction IDs, revisions, the locked upsert script, Pull, upgrade linking and the review screens. Until a user redeploys, their Transaction Sync stays paused and the app tells them so. Moving amounts from `Double` to whole paise is left for later, as a separate change.

---
status: accepted
---

# Idempotent two-way Sync keyed by a permanent Transaction ID

Transactions used to be matched to Sheet rows by their Remote timestamp, which was generated each time a Sync was attempted and only saved after the Apps Script confirmed. Whenever the Sheet wrote a row but the phone never got the reply (a timeout, a cancelled worker, a killed app), the retry sent a new timestamp and the script appended the row a second time. Updates that couldn't find their row appended too. The user also edits the Sheet by hand and reinstalls the app, so the Sheet has to be treated as a second place where changes happen, not just a mirror.

A phone and a Sheet can't share a real transaction, so we don't aim for ACID across them. We aim for this: each change reaches the Sheet exactly once, however many times it's retried.

## Decision

- **Transaction ID.** Every Transaction gets a random UUID when it's created, stored in Room and in an ID column of `_responses`. It never changes. When the phone reads the Sheet, the script gives an ID (under its lock) to any row that doesn't have one, such as rows typed in by hand.
- **Idempotent writes.** Every script write is an upsert by Transaction ID, and deleting an ID that isn't there counts as success. Each request runs under `LockService`, so the same batch can be sent any number of times with the same result.
- **Local change tracking.** Each Transaction has a version number that goes up on every local edit, plus a copy of how it looked at its last Sync. A Sync marks a Transaction done only if its version hasn't changed since the Sync read it, in a single Room transaction. An edit made during a Sync can therefore never be lost.
- **Pull.** On app open and on "Sync now", the phone reads the whole Sheet and compares it by Transaction ID. If only one side changed since the last Sync, that side wins. If both changed, the user resolves it as a Sync conflict. Amounts are compared rounded to 2 decimals.
- **Deletes from the Sheet.** A synced Transaction whose ID has disappeared from the Sheet is deleted on the phone, unless one pull would delete more than 10 Transactions or more than 10% of them. In that case the app asks first.
- **Scheduling.** Transaction Sync is its own unique WorkManager job, and a new request never cancels one that's running. Backups run as a separate, delayed job and can't stop Transactions from syncing.
- **Old scripts.** Every script reply includes its version. If the deployed script is older than the app needs, the app pauses Transaction Sync and asks the user to redeploy. It never falls back to the unsafe path (see ADR-0002).
- **Upgrading.** On the first Sync after the upgrade, local Transactions are linked to Sheet rows once, by Remote timestamp. Anything that can't be linked goes to a duplicate review screen and is never sent blindly. Suspected duplicates are only ever deleted by the user.

## Considered options

- **Keep the Remote timestamp as the key, but save it before sending.** Rejected: it only goes down to the second, so values collide, and its text depends on the Sheet's time zone and formatting.
- **A separate outbox table of pending operations.** Rejected as unnecessary: the version number plus the last-synced copy on each Transaction gives the same guarantees with less machinery.

## Consequences

The work ships in two phases. Phase 1 changes only the app and works with scripts already deployed: jobs no longer cancel each other, Backups no longer block Transactions, the lost-update bug is fixed, and the Remote timestamp is saved before sending. Phase 2 needs every user to redeploy: Transaction IDs, the locked upsert script, Pull, the upgrade linking and the duplicate review screen. Moving amounts from `Double` to whole paise is left for later, as a separate change.

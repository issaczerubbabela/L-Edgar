# ADR 001: Offline-First Architecture with Eventual Sync

**Date**: 2025-09-24  
**Status**: Accepted

## Problem

SheetSync targets users in environments with intermittent network connectivity. Users need immediate, responsive local transactions (reading history, planning budgets) regardless of network state. However, they also need their data backed up to a persistent, shared location (Google Sheets) and synchronized across devices.

A synchronous "call the server first" pattern would:
- Block UI during network outages
- Create unpredictable latency spikes
- Require elaborate offline caching and conflict resolution

## Decision

Implement **offline-first, eventual-sync** architecture:

1. **All writes to Room first** — UI commits transactions to local Room database immediately, marking them `isSynced=false`.
2. **Background sync via WorkManager** — A `SyncWorker` periodically (and on-demand) reads all `isSynced=false` records and sends them to Google Apps Script.
3. **Google Sheets as canonical backup** — AppsScript persists synced records to Google Sheets; Sheets also serves as an import source for restore/cross-device sync.
4. **Soft deletes until remote ack** — Deleted records remain in Room with `syncAction=DELETE` until the remote delete succeeds, then are hard-deleted locally.

## Consequences

**Positive:**
- UI is always responsive; no network blocking.
- Sync failures are non-fatal — retries happen in background without user intervention.
- Local-first development is simpler: test UI logic against Room without mocking network.
- Easy to add offline features (budgets, analytics) that don't depend on sync.

**Negative:**
- Eventual consistency — data may be out-of-sync across devices for seconds/minutes.
- Requires careful state tracking (`isSynced`, `syncAction`, `remoteTimestamp`) to avoid conflicts.
- Cross-device sync depends on Sheets polling/import, not real-time push.
- Soft-delete cleanup must be robust; lingering `syncAction=DELETE` rows become clutter if sync fails repeatedly.

**Mitigation:**
- Repository pattern abstracts sync state; ViewModels don't directly reference sync flags.
- WorkManager retries + exponential backoff reduce failure windows.
- Changelog and migration strategies document breaking changes to the sync contract.

## References

- `CONTEXT.md` — Core philosophy
- `docs/ARCHITECTURE.md` — Data flow, WorkManager integration
- `app/src/main/java/com/issaczerubbabel/ledgar/sync/SyncWorker.kt` — Implementation

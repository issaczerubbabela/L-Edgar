# ADR 002: Room as Source of Truth

**Date**: 2025-09-24  
**Status**: Accepted

## Problem

With an offline-first architecture, multiple data sources can conflict: local Room tables, unacknowledged in-flight API requests, stale Sheets data, and cached ViewModels. Without a clear authority, app state becomes ambiguous, and sync logic becomes fragile.

## Decision

**Room is the single source of truth for all app state:**

1. **All reads** come through Room. ViewModels subscribe to StateFlows backed by Room queries.
2. **All writes** (from UI or sync) go through Room first.
3. **Retrofit** never directly modifies Room — only the `SyncWorker` and import flows touch Room via Repository.
4. **Sheets** is a backup/restore channel, not a source during normal operation. Import flows read from Sheets and write to Room.
5. **Sync state** (`isSynced`, `syncAction`, `remoteTimestamp`) is tracked in Room; this state drives retry logic and conflict resolution.

## Consequences

**Positive:**
- Single version of truth eliminates state inconsistency.
- ViewModels' reactive subscriptions always reflect the actual app state.
- Offline development is straightforward — no need to mock or stub external systems.
- Migration and schema changes happen in one place (Room migrations).

**Negative:**
- All data must fit in Room. For large datasets, this may require pagination or archival strategies.
- Sync state bloats schema; every synced table gets `isSynced`, `syncAction`, and `remoteTimestamp` columns.
- Retrofit responses must be transformed into Room entities before being useful; no passthrough caching.

**Mitigation:**
- Repository pattern hides this transformation; ViewModels don't see the complexity.
- Room queries are indexed for performance; schema design favors fast reads.
- WorkManager + Hilt keeps sync decoupled from the critical path.

## References

- `CONTEXT.md` — Offline-first philosophy
- `app/src/main/java/com/issaczerubbabel/ledgar/data/repository/` — Repository pattern
- `app/src/main/java/com/issaczerubbabel/ledgar/data/local/` — Room schema

# SheetSync Context

SheetSync (package `com.issaczerubbabel.ledgar`) is an offline-first Android expense tracker built with **Kotlin + Jetpack Compose**, MVVM architecture, and Room as the local source of truth.

## Core Philosophy

**No network calls on the critical path.** Every write commits to Room first, then WorkManager syncs in the background to a Google Apps Script web app that persists to Google Sheets.

## Key Concepts

- **Offline-first**: UI and local database are always responsive; sync is eventual.
- **Room as source of truth**: All reads/writes go through Room first; sync state (`isSynced`, `syncAction`, `remoteTimestamp`) tracks background operations.
- **Background sync**: WorkManager + Hilt-injected `SyncWorker` handles all network I/O asynchronously.
- **Repository pattern**: ViewModels depend only on repository interfaces, never directly on DAOs or Retrofit.
- **Soft deletes**: Deletions are marked `syncAction=DELETE` until the remote delete succeeds, then hard-deleted.

## Architecture Layers

1. **Presentation** (Compose screens + ViewModels with StateFlow)
   - Top-level tabs: Trans, Stats, Budget, Accounts, More
   - Form-heavy flows managed reactively via ViewModels
   
2. **Domain** (Repository interfaces + business logic)
   - Duplicate detection during import
   - Budget aggregation and running-balance calculations
   - Cycle and bucket-based salary budgeting
   
3. **Data** (Room + Retrofit)
   - Room tables: expense_records, account_records, budgets, budget_cycles, budget_buckets, bucket_categories, dropdown_options
   - Retrofit syncs via Google Apps Script endpoint
   - Import/export via Google Sheets
   
4. **Background** (WorkManager + SyncWorker)
   - Async sync of unsynced records
   - Backup of dropdowns and budgets on every sync run
   - Retry semantics on network failure

## Navigation

Five top-level destinations:
- **Trans**: Daily, Calendar, Monthly history views + log transaction flow
- **Stats**: Insights and analytics
- **Budget**: Salary-cycle bucket budgeting with cycle setup, planning, and detail screens
- **Accounts**: Account/group management with running-balance calculations
- **More**: Settings, dropdown management, etc.

Quick surfaces (widgets, shortcuts, quick-log activity) also contribute transactions.

## Data Model

**Core tables:**
- `expense_records` — transactions with sync state (`isSynced`, `syncAction`, `remoteTimestamp`)
- `account_records` — accounts/groups, referenced by transfers
- `budgets` — per-month, per-category budgets (legacy; kept for backup/rollback)
- `budget_cycles` / `budget_buckets` / `bucket_categories` — salary-cycle budgeting
- `dropdown_options` — configurable dictionaries (categories, payment modes, etc.)

**Sync contract:**
- App → Google Apps Script web app (Retrofit)
- Script persists to Google Sheets
- Script also backs up and restores budgets via Sheets-based import

## Key Files

- Full architecture diagrams: `docs/ARCHITECTURE.md`
- Source tree: `app/src/main/java/com/issaczerubbabel/ledgar/`
- Build config: `app/build.gradle.kts` (where `APPS_SCRIPT_URL` is injected)
- AppsScript contract: `scripts/AppsScript.gs` (also copied in UI for user setup)
- Changelog: `app/src/main/java/com/issaczerubbabel/ledgar/ui/screens/ChangelogData.kt` (**must update with every code change**)

## Design Decisions

See `docs/adr/` for rationales on:
- Offline-first + eventual sync strategy
- Room as source of truth
- Salary-cycle bucket budgeting model
- And more.

## Related Commands

- **Build debug APK**: `./gradlew assembleDebug`
- **Install on device**: `./gradlew installDebug`
- **Run tests**: `./gradlew test`
- **Full setup**: See CLAUDE.md for JDK, local.properties, and APPS_SCRIPT_URL requirements.

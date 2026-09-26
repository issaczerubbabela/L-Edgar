# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SheetSync (package `com.issaczerubbabel.ledgar`) is an offline-first Android expense tracker: Kotlin + Jetpack Compose UI, MVVM + repository pattern, Room as local source of truth, with background sync to a Google Apps Script web app that writes to a Google Sheet. Core philosophy: no network calls on the critical path — every write commits to Room first, then WorkManager syncs it in the background.

## Build & Run Commands

- Build debug APK: `./gradlew assembleDebug`
- Install on connected device/emulator: `./gradlew installDebug`
- Run unit tests: `./gradlew test`
- Run a single unit test class: `./gradlew test --tests "com.issaczerubbabel.ledgar.util.DateParsingTest"`
- Clean build: `./gradlew clean build`

### First-time setup required before building

1. Copy `local.properties.example` to `local.properties` and set `sdk.dir`.
2. The app syncs to the Apps Script URL entered in its Database Setup screen (deploy `scripts/AppsScript.gs` as a web app, execute as Me, access Anyone). The debug build installs as `com.issaczerubbabel.ledgar.debug` ("L.Edgar (Debug)") next to the release app, and falls back to `APPS_SCRIPT_URL_DEBUG` from `local.properties` (injected as `BuildConfig.DEFAULT_SCRIPT_URL`) so development can use a test Sheet.
3. JDK 17 required (`gradle/gradle-daemon-jvm.properties` pins toolchain vendor `oracle` / version 17).

## Architecture

Full diagrams (data flow, sync sequence, navigation map, Room ER diagram, DI graph) live in `docs/ARCHITECTURE.md` — read it before making structural changes.

Source root: `app/src/main/java/com/issaczerubbabel/ledgar/`

- `data/local` — Room entities, DAOs, database class.
- `data/remote` — Retrofit `ApiService` / DTOs for the Apps Script endpoint.
- `data/repository` — Repository interfaces + impls; ViewModels talk only to repositories, never DAOs or Retrofit directly.
- `data/preferences` — DataStore-backed preferences (theme, etc).
- `sync` — `SyncTriggers` (started in `SheetSyncApp`; watches Room and decides when to sync/back up), `SyncScheduler` (the only place work is queued), `SyncWorker` + `TransactionSyncer` (Transactions), `BackupWorker` (accounts, dropdowns, budgets).
- `di` — Hilt modules.
- `ui/screens`, `ui/components`, `ui/navigation`, `ui/theme` — Compose screens and navigation graph.
- `viewmodel` — StateFlow-based ViewModels per feature.
- `util` — helpers (e.g. date parsing).

### Data flow

UI (Compose) → ViewModel (StateFlow) → Repository → Room (instant local write, marks `isSynced=false`) → `SyncTriggers` sees the unsynced row and calls `SyncScheduler.requestSync()` (ViewModels never schedule sync work themselves) → `SyncWorker` reads unsynced records → Retrofit `ApiService` → Apps Script web app → Google Sheet. Never enqueue `SyncWorker` with `REPLACE` (it cancels a Sync mid-request) or write a synced flag unconditionally: settle rows with `markSyncedIfUnchanged`, which checks the trigger-maintained `localVersion` (see ADR-0003). Deletes are soft (`syncAction=DELETE`) until the remote delete succeeds, then hard-deleted locally. Any change to accounts, dropdown options, budgets or transactions also makes `SyncTriggers` schedule a delayed `BackupWorker` that backs those lists up, which supports Sheets-based restore/import.

### Room model

Core tables: `expense_records` (transaction ledger + sync state: `isSynced`, `syncAction`, `remoteTimestamp`), `account_records` (accounts/groups, referenced by `accountId`/`fromAccountId`/`toAccountId` for transfers), `budgets` (unique on `monthYear`+`category`; legacy per-category model, superseded by the bucket tables but kept for backup/rollback), `budget_cycles` / `budget_buckets` / `bucket_categories` (salary-cycle bucket budgeting; unique on `cycleId`+`category` so a category lives in exactly one bucket), `dropdown_options` (configurable dictionaries: `EXPENSE_CATEGORY`, `INCOME_CATEGORY`, `ACCOUNT_GROUP`, `PAYMENT_MODE`).

### Extending the app (typical flow)

1. Add/update Room entities + DAO queries.
2. Expose operations via repository interfaces/impls.
3. Add ViewModel state + actions (StateFlow).
4. Build/update Compose screens, wire navigation.
5. If remote-facing, update `scripts/AppsScript.gs` contract and the Retrofit DTO/API methods together — the two must stay in sync since there's no shared schema. The script also exists as a copy in `ui/screens/AppsScriptSetupScreen.kt` (what users paste into Apps Script): change both, and run `node --test scripts/tests/transaction-sync.test.js` and `node --test scripts/tests/bucket-budgets.test.js`, which fail if they drift. Never send new data in `records`: a script older than your change files `records` as transactions. Bump `SCRIPT_VERSION` in the script and `TransactionSyncer.REQUIRED_SCRIPT_VERSION` together when the app starts depending on a new script behaviour.
6. Validate sync behavior for insert/update/delete and import paths.

## Mandatory changelog policy

Any prompt that changes a repository file (code, UI, sync, schema, or instructions) MUST also update `app/src/main/java/com/issaczerubbabel/ledgar/ui/screens/ChangelogData.kt` in the same run, before finishing:

- Add a bullet under the right Keep a Changelog section of the current working `ChangelogRelease`: `added`, `changed` or `fixed` for what users notice (short, plain, user-facing), or `developer` for internal work (tests, docs, CI, refactors), which only appears in `CHANGELOG.md`.
- Regenerate `CHANGELOG.md` from it: `UPDATE_CHANGELOG=1 ./gradlew testDebugUnitTest --tests "*ChangelogMarkdownTest"`. The test fails if the two drift.
- Only create a new `ChangelogRelease` entry when explicitly asked to bump the version, and bump `appVersionName` in `app/build.gradle.kts` to match (versionCode is derived from it).
- Skip this only when the change is genuinely read-only (no repository files modified).

## Releasing

Pushing a `vX.Y.Z` tag that matches `appVersionName` runs `.github/workflows/release.yml`: tests, a signed release APK, and a GitHub Release whose notes are that version's section of `CHANGELOG.md`. Signing needs the repository secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`; locally the same names in `local.properties` (with `RELEASE_STORE_FILE`) sign `assembleRelease`. Try a shrunk build on a phone with `./gradlew installStaging`, which installs next to the release app.

This is enforced by a `PreToolUse` hook (`.github/hooks/changelog-enforcer.json` → `scripts/enforce-changelog.ps1`) — expect tool calls to be blocked if the changelog isn't updated alongside other changes.

## Agent skills

### Issue tracker

Issues live in GitHub Issues (`issaczerubbabela/L-Edgar`), managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context: a `CONTEXT.md` glossary and ADRs in `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Repo-local skills

`.github/skills/` contains 17 Android-focused skills (architecture, Compose, Room/Retrofit data layer, coroutines, testing, performance, etc.) auto-discovered by keyword match — see `.github/copilot-instructions.md` for the full index. `.agents/skills/` has an additional `android-security-skill`. Prefer using the relevant skill for its domain (e.g. `compose-ui` for Composable work, `android-data-layer` for Room/Retrofit changes) rather than improvising patterns that conflict with them.

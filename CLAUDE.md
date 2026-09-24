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
2. Set `APPS_SCRIPT_URL` in `local.properties` to a deployed Google Apps Script web app URL (built from `scripts/AppsScript.gs`, deployed as web app, execute as Me, access Anyone). This value is injected into `BuildConfig.APPS_SCRIPT_URL` in `app/build.gradle.kts`. Without it, sync/import features fail but the app still builds.
3. JDK 17 required (`gradle/gradle-daemon-jvm.properties` pins toolchain vendor `oracle` / version 17).

## Architecture

Full diagrams (data flow, sync sequence, navigation map, Room ER diagram, DI graph) live in `docs/ARCHITECTURE.md` — read it before making structural changes.

Source root: `app/src/main/java/com/issaczerubbabel/ledgar/`

- `data/local` — Room entities, DAOs, database class.
- `data/remote` — Retrofit `ApiService` / DTOs for the Apps Script endpoint.
- `data/repository` — Repository interfaces + impls; ViewModels talk only to repositories, never DAOs or Retrofit directly.
- `data/preferences` — DataStore-backed preferences (theme, etc).
- `sync` — WorkManager `SyncWorker` (Hilt-injected).
- `di` — Hilt modules.
- `ui/screens`, `ui/components`, `ui/navigation`, `ui/theme` — Compose screens and navigation graph.
- `viewmodel` — StateFlow-based ViewModels per feature.
- `util` — helpers (e.g. date parsing).

### Data flow

UI (Compose) → ViewModel (StateFlow) → Repository → Room (instant local write, marks `isSynced=false`) → ViewModel enqueues unique `SyncWorker` via WorkManager → `SyncWorker` reads unsynced records → Retrofit `ApiService` → Apps Script web app → Google Sheet. Deletes are soft (`syncAction=DELETE`) until the remote delete succeeds, then hard-deleted locally. Sync also backs up dropdown options and budgets on every run, which supports Sheets-based restore/import.

### Room model

Core tables: `expense_records` (transaction ledger + sync state: `isSynced`, `syncAction`, `remoteTimestamp`), `account_records` (accounts/groups, referenced by `accountId`/`fromAccountId`/`toAccountId` for transfers), `budgets` (unique on `monthYear`+`category`; legacy per-category model, superseded by the bucket tables but kept for backup/rollback), `budget_cycles` / `budget_buckets` / `bucket_categories` (salary-cycle bucket budgeting; unique on `cycleId`+`category` so a category lives in exactly one bucket), `dropdown_options` (configurable dictionaries: `EXPENSE_CATEGORY`, `INCOME_CATEGORY`, `ACCOUNT_GROUP`, `PAYMENT_MODE`).

### Extending the app (typical flow)

1. Add/update Room entities + DAO queries.
2. Expose operations via repository interfaces/impls.
3. Add ViewModel state + actions (StateFlow).
4. Build/update Compose screens, wire navigation.
5. If remote-facing, update `scripts/AppsScript.gs` contract and the Retrofit DTO/API methods together — the two must stay in sync since there's no shared schema. The script also exists as a copy inside `ui/screens/AppsScriptSetupScreen.kt` (what users paste into Apps Script): change both, and run `node --test scripts/tests/bucket-budgets.test.js`, which fails if they drift. Never send new data in `records`: a script deployed before your change files `records` as transactions.
6. Validate sync behavior for insert/update/delete and import paths.

## Mandatory changelog policy

Any prompt that changes a repository file (code, UI, sync, schema, or instructions) MUST also update `app/src/main/java/com/issaczerubbabel/ledgar/ui/screens/ChangelogData.kt` in the same run, before finishing:

- Add a bullet under the appropriate category (`features`, `fixes`, `qol`) of the current working `ChangelogRelease`.
- Only create a new `ChangelogRelease` entry when explicitly asked to bump the version.
- Skip this only when the change is genuinely read-only (no repository files modified).

This is enforced by a `PreToolUse` hook (`.github/hooks/changelog-enforcer.json` → `scripts/enforce-changelog.ps1`) — expect tool calls to be blocked if the changelog isn't updated alongside other changes.

## Agent skills

### Issue tracker

Issues live in GitHub Issues (`issaczerubbabela/L-Edgar`), managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context: a `CONTEXT.md` glossary and ADRs in `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Repo-local skills

`.github/skills/` contains 17 Android-focused skills (architecture, Compose, Room/Retrofit data layer, coroutines, testing, performance, etc.) auto-discovered by keyword match — see `.github/copilot-instructions.md` for the full index. `.agents/skills/` has an additional `android-security-skill`. Prefer using the relevant skill for its domain (e.g. `compose-ui` for Composable work, `android-data-layer` for Room/Retrofit changes) rather than improvising patterns that conflict with them.

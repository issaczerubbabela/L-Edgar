# SheetSync: Offline-First Android Expense Tracker

SheetSync is a Kotlin + Jetpack Compose money manager built around an offline-first flow.
All writes are committed locally first (Room), then synchronized to Google Sheets in the background.

## Features Implemented

### Transaction Logging

- Add, edit, and delete transactions.
- Supports `Expense`, `Income`, and `Transfer` transaction types.
- Date picker, category selection, payment mode selection, remarks, and description fields.
- Transfer flow supports `From Account` and `To Account` selection.
- Sync status chip in the log screen with manual retry on failure.

### History and Analysis

- Multi-tab history experience:
  - `Daily` grouped records.
  - `Calendar` view with per-day income/expense and category dot markers.
  - `Monthly` rollups with expandable weekly breakdowns.
  - `Total` dashboard with budget and account summaries.
- Insights screen with:
  - Current month income/expense/balance cards.
  - Category-wise spend chart.
  - 6-month expense trend chart.

### Budgets

- Set and edit a monthly `Total Budget`.
- Set per-category budgets.
- Automatic `Other` budget calculation.
- Budget progress rows in the Total tab with ideal-progress marker.
- Budget data is included in sync backup/import flows.

### Accounts

- Dedicated Accounts tab.
- Add accounts with configurable account groups.
- Account list grouped by account group.
- Account detail screen with monthly period navigation and running balance statement.

### Dropdown and App Configuration Data

- Manage dropdown options in-app for:
  - Expense Categories
  - Income Categories
  - Account Groups
  - Payment Modes
- Add, delete, and reorder options.

### Import, Export, and Data Utilities

- Import transactions from Google Sheets.
- Import transactions from CSV with header-based parsing.
- Duplicate-control toggle for imports (skip or allow duplicates).
- Google Sheets import also restores dropdown options and budget rows.
- Export records by interval from Total tab to CSV (Excel-compatible).
- Reset local data option in settings.

### Sync Engine

- Offline-first local persistence via Room.
- WorkManager + Hilt worker for background sync.
- Sync supports insert/update/delete actions.
- Idempotent delete handling for already-removed remote rows.
- Sync also backs up dropdown options and budgets to Google Sheets endpoint.

### Quick Entry Surfaces

- Home screen quick-log widget.
- Quick Settings tile for instant quick-log launch.
- App shortcut for quick-log action.
- Quick Log bottom-sheet activity for fast amount + category entry.

### UI and App Experience

- Compose + Material 3 navigation with bottom tabs.
- Light/Dark theme toggle persisted via DataStore.

## Tech Stack

- Kotlin
- Jetpack Compose (Material 3)
- MVVM + repository-based data layer
- Room (SQLite)
- Hilt (DI)
- WorkManager
- Retrofit + Gson
- Vico charts
- DataStore Preferences

## First-Time Setup (After Cloning)

### 1. Prerequisites

- Android Studio (latest stable recommended)
- Android SDK installed
- JDK 17 available locally (project uses Java/Kotlin target 17)

### 2. Clone and open

1. Clone this repository.
2. Open the project root in Android Studio.
3. Let Gradle sync complete.

### 3. Configure local properties

1. Copy `local.properties.example` to `local.properties`.
2. Ensure `sdk.dir` is set (Android Studio usually sets this automatically).
3. Set your Apps Script endpoint:

```properties
APPS_SCRIPT_URL=https://script.google.com/macros/s/YOUR_SCRIPT_ID/exec
```

### 4. Deploy Google Apps Script web app

1. Open your Google Sheet.
2. Go to `Extensions -> Apps Script`.
3. Paste the script from `scripts/AppsScript.gs`.
4. Click `Deploy -> New deployment`.
5. Type: `Web app`.
6. Execute as: `Me`.
7. Who has access: `Anyone`.
8. Copy the deployment URL and paste it into `local.properties` as `APPS_SCRIPT_URL`.

Note: the deployment URL changes on each new deployment. Update `local.properties` whenever you redeploy.

### 5. Run the app

1. Connect a physical device (USB or wireless debugging) or start an emulator.
2. Run the `app` configuration from Android Studio.

## Troubleshooting

### Gradle/JDK issues on Windows

- Symptom: Gradle fails with toolchain provisioning errors (for example, no toolchain download URL for Windows).
- Fix:
  1. Ensure a local JDK 17 is installed.
  2. Keep `gradle/gradle-daemon-jvm.properties` aligned to vendor/version 17.
  3. Keep `gradle.properties` with local detection enabled and auto-download disabled.

### Sync or import fails

- Verify `APPS_SCRIPT_URL` in `local.properties` is the latest deployed Apps Script Web App URL.
- If you updated Apps Script code, always redeploy with `Deploy -> New deployment` and paste the new URL.
- Ensure deployment access is `Anyone` and type is `Web app`.

### Google Sheets import returns empty

- Confirm the script is attached to the correct spreadsheet.
- Confirm the expected sheet tab exists and the script points to the correct tab name.
- Confirm your sheet has header row + transaction rows.

### Quick log tile/widget not visible

- Tile: add it manually from Android Quick Settings tile edit panel.
- Widget: long-press home screen and add the SheetSync quick-log widget.

### Build succeeds but app behavior seems stale

- Run `Build -> Clean Project` and `Build -> Rebuild Project`.
- Uninstall and reinstall the app if Room schema/data migrations from older local installs are interfering.

## Architecture Documentation

- Full architecture, data flow, sync flow, navigation map, and database model diagrams are in `docs/ARCHITECTURE.md`.

## Extending the App

### Architecture guide

- UI is Compose screens + ViewModels.
- Data access goes through repositories.
- Local source of truth is Room.
- Remote sync/import is handled via Retrofit + WorkManager.

### Typical extension flow

1. Add/update Room entities + DAO queries for new data.
2. Expose operations in repository interfaces and implementations.
3. Add ViewModel state + actions using StateFlow.
4. Build/update Compose screens and wire navigation.
5. If needed remotely, update `scripts/AppsScript.gs` contract and Retrofit DTO/API methods.
6. Validate sync behavior for insert/update/delete and import paths.

### Common extension points already present

- New dropdown-driven configuration can be added using the dropdown management pattern.
- New account-level analytics can hook into existing account repositories and detail screen model.
- New backup/import datasets can follow the existing `target`-based sync payload approach.

## Notes

- `local.properties` is intentionally untracked; keep secrets and machine-specific values there only.
- If the app runs but sync/import fails, verify `APPS_SCRIPT_URL` first.

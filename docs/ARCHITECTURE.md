# SheetSync Architecture

This document describes the current architecture of the application, including module boundaries, runtime flows, sync behavior, and the Room database model.

## 1. High-Level Architecture

SheetSync follows an offline-first architecture:

- UI writes to local Room database immediately.
- ViewModels observe Room-backed Flows/StateFlows for reactive UI.
- WorkManager performs background synchronization to Google Apps Script.
- Google Apps Script persists/reads from Google Sheets.

```mermaid
graph TD
    U[User Interaction] --> UI[Jetpack Compose Screens]
    UI --> VM[ViewModels]
    VM --> REPO[Repositories]
    REPO --> ROOM[(Room Database)]

    VM --> WM[WorkManager Enqueue]
    WM --> SW[SyncWorker]
    SW --> API[Retrofit ApiService]
    API --> GAS[Google Apps Script Web App]
    GAS --> GS[(Google Sheets)]

    REPO --> API
    API --> REPO
    REPO --> VM
    VM --> UI
```

## 2. Layer Responsibilities

### Presentation Layer

- Built with Jetpack Compose + Navigation.
- Primary top-level destinations:
  - Trans (History: Daily, Calendar, Monthly, Total)
  - Stats (Insights)
  - Accounts
  - More (Settings)
- Form-heavy flows are managed in ViewModels with reactive state.

### Domain/Application Layer (Repository contracts)

- Repository interfaces abstract data operations.
- ViewModels depend on repositories, not DAOs.
- Business logic examples:
  - Duplicate detection during import
  - Budget aggregation and progress computation
  - Account running-balance calculations

### Data Layer

- Room as source of truth for:
  - expense_records
  - account_records
  - budgets
  - dropdown_options
- Retrofit handles:
  - Transaction sync (insert/update/delete)
  - Transaction import
  - Dropdown import
  - Budget import

### Background Execution

- WorkManager + Hilt-injected SyncWorker.
- SyncWorker sends local unsynced records and then backs up dropdowns and budgets.
- Worker uses retry semantics on failure.

## 3. Navigation Architecture

```mermaid
flowchart TD
    A[App Start] --> B[Trans / History]
    A --> C[Stats / Insights]
    A --> D[Accounts]
    A --> E[More / Settings]

    B --> B1[Log Transaction]
    B --> B2[Budget Setting]

    D --> D1[Add Account]
    D --> D2[Account Detail]

    E --> E1[Dropdown Management]

    Q[Quick Surfaces] --> Q1[QuickLogActivity]
    Q[Quick Surfaces] --> Q2[Quick Settings Tile]
    Q[Quick Surfaces] --> Q3[Home Widget]
    Q[Quick Surfaces] --> Q4[App Shortcut]
```

## 4. Transaction Lifecycle

```mermaid
sequenceDiagram
    participant User
    participant LogScreen
    participant LogVM as LogViewModel
    participant Repo as ExpenseRepository
    participant Room as ExpenseDao/Room
    participant WM as WorkManager
    participant Worker as SyncWorker
    participant API as ApiService
    participant GAS as Apps Script
    participant Sheet as Google Sheets

    User->>LogScreen: Save transaction
    LogScreen->>LogVM: save()
    LogVM->>Repo: save/update(record, isSynced=false, syncAction=INSERT|UPDATE)
    Repo->>Room: insert/update
    Room-->>LogVM: local write complete
    LogVM->>WM: enqueue unique SyncWorker

    WM->>Worker: run doWork()
    Worker->>Repo: getUnsynced()
    Repo-->>Worker: unsynced records

    alt INSERT/UPDATE exists
        Worker->>API: syncRecords(action, records)
        API->>GAS: POST
        GAS->>Sheet: append/update rows
        GAS-->>API: ok
        API-->>Worker: ok
        Worker->>Repo: markSynced(ids)
    end

    alt DELETE exists
        Worker->>API: syncRecords(delete, targetTimestamp)
        API->>GAS: POST delete
        GAS->>Sheet: delete row by timestamp
        GAS-->>API: ok/not found
        Worker->>Repo: hardDeleteById(id)
    end

    Worker->>API: backup dropdowns
    Worker->>API: backup budgets
```

## 5. Import Flows

### Google Sheets Import

1. Settings triggers importFromSheets.
2. Repository imports dropdowns and overwrites local options.
3. Repository imports budgets and overwrites local budget rows.
4. Repository imports transactions and deduplicates against local comparable fields.

### CSV Import

1. Settings picks CSV file.
2. CsvParser parses header-based columns.
3. Records inserted via repository.
4. Optional duplicate skipping based on toggle.

## 6. Database Model (Room)

```mermaid
erDiagram
    ACCOUNT_RECORDS ||--o{ EXPENSE_RECORDS : fromAccountId
    ACCOUNT_RECORDS ||--o{ EXPENSE_RECORDS : toAccountId
    ACCOUNT_RECORDS ||--o{ EXPENSE_RECORDS : accountId

    ACCOUNT_RECORDS {
        long id PK
        string groupName
        string accountName
        double initialBalance
        bool isHidden
    }

    EXPENSE_RECORDS {
        long id PK
        string date
        string type
        string category
        string description
        double amount
        long accountId FK
        long fromAccountId FK
        long toAccountId FK
        string remarks
        bool isSynced
        string remoteTimestamp
        string syncAction
    }

    BUDGETS {
        long id PK
        string monthYear
        string category
        double amount
    }

    DROPDOWN_OPTIONS {
        long id PK
        string optionType
        string name
        int displayOrder
    }
```

## 7. Table-Level Details

### expense_records

- Purpose: transaction ledger and sync state tracking.
- Important behavior:
  - Soft delete for sync by setting syncAction=DELETE.
  - Hard delete after successful remote delete.
  - isSynced + syncAction drives worker behavior.

### account_records

- Purpose: logical accounts for transfer and balance calculations.
- Used by:
  - Account listing and detail statement screens.
  - Transfer transactions (fromAccountId/toAccountId).

### budgets

- Purpose: monthly budget configuration.
- Unique index: (monthYear, category).
- Includes both category-level entries and total-budget semantics from app logic.

### dropdown_options

- Purpose: configurable app dictionaries.
- optionType values currently cover:
  - EXPENSE_CATEGORY
  - INCOME_CATEGORY
  - ACCOUNT_GROUP
  - PAYMENT_MODE

## 8. Dependency Injection Graph (Conceptual)

```mermaid
graph TD
    H[Hilt] --> DB[SheetSyncDatabase]
    H --> DAO1[ExpenseDao]
    H --> DAO2[AccountDao]
    H --> DAO3[BudgetDao]
    H --> DAO4[DropdownOptionDao]
    H --> NET[Retrofit ApiService]
    H --> WMF[HiltWorkerFactory]

    DAO1 --> R1[ExpenseRepositoryImpl]
    DAO2 --> R2[AccountRepositoryImpl]
    DAO3 --> R3[BudgetRepositoryImpl]
    DAO4 --> R4[DropdownOptionRepositoryImpl]
    NET --> R1

    R1 --> VM1[LogViewModel]
    R1 --> VM2[History/Insights/Settings]
    R2 --> VM3[Accounts ViewModels]
    R3 --> VM4[Budget/Total ViewModels]
    R4 --> VM5[Dropdown + QuickLog]

    WMF --> SW[SyncWorker]
    R1 --> SW
    R3 --> SW
    R4 --> SW
    NET --> SW
```

## 9. Operational Notes

- The app is intentionally resilient to intermittent connectivity due to local-first persistence + queued sync.
- Remote endpoint changes (Apps Script redeploy) require local `APPS_SCRIPT_URL` update.
- Sync includes not only transactions but also dropdown and budget backup to support restore scenarios.

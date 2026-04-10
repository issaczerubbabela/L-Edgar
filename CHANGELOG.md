# SheetSync Changelog

All notable changes to the SheetSync Android expense tracking app are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

---

## [v1.0.1] - 2026-04-10

### Features

- **Responsive Text Sizing** — Implement adaptive text sizing for History and Monthly screens to enhance readability across different device sizes and orientations

### Bug Fixes

- **Category Mapping** — Improve category mapping logic to handle blank values and ensure proper categorization for transfer transactions
- **Navigation State** — Update navigation logic to properly pop up to the selected screen route and improve state management

---

## [v1.0.0] - 2026-04-08

A major release introducing comprehensive transaction management, analytics, theming, and advanced search capabilities.

### Features

- **Advanced Transaction Management**
  - Search functionality for transactions with multiple filters and results display
  - Batch transaction editing including delete, update dates, categories, assets, and descriptions
  - Transaction bookmarking with UI integration and ViewModel support
  - Account labels for transactions in HistoryScreen for better context

- **Analytics & Insights**
  - Comprehensive StatsViewModel and associated models for enhanced statistics tracking
  - Overall Account Stats screen with detailed account information and integration with navigation
  - Category-wise expense visualization with ExpenseDonutChart component
  - 6-month cash flow trend visualization with CashFlowBarChart component
  - InsightsScreen enhanced with dropdown filters for timeframe and account group selection
  - Vico Charting Library integration for professional cash flow visualization
  - Enhanced charts with marker formatting and interactive elements

- **Enhanced UI & Customization**
  - Theme selection system with multiple color schemes
  - Red theme option with updated color scheme for improved customization
  - AccountDetailScreen layout improvements with better text overflow handling
  - Month and year selection functionality in HistoryViewModel with updated HistoryScreen UI
  - Show/hide functionality for hidden accounts in AccountsScreen
  - Improved date badge display in HistoryScreen
  - New launcher icons and background resources

- **Account Management Improvements**
  - Account display order management for better organization
  - Enhanced account import functionality
  - Account balance calculations with running balance statements
  - Account detail screen with monthly period navigation
  - Add account functionality in AccountsScreen
  - Account deletion with linked transaction handling

- **Budget & Financial Management**
  - Total Tab Screen with comprehensive budget tracking
  - Budget progress rows with ideal-progress markers
  - Account summary dashboard
  - Per-category budget management
  - Automatic "Other" budget calculation
  - Budget import functionality
  - CSV data export capability

- **Core Features**
  - Responsive text sizing and improved text overflow handling across multiple screens

### Bug Fixes

- **Timestamp Handling** — Correct regex patterns in timestamp normalization function for accurate date parsing
- **Code Quality** — Refactor code structure for improved readability and maintainability
- **Flexible Date Parsing** — Add unit tests for flexible date parsing and enhance logging for unparseable dates

### Quality of Life

- **Log Screen Improvements** — Remove payment mode selection and add back navigation to LogScreen with improved snackbar messaging
- **Expense Repository** — Enhance with fallback account creation, transfer handling, and improved date parsing in various formats
- **Sync Operations** — Update sync worker for account backup logic and improve data handling

---

## [v0.3] - 2026-04-03

### Features

- **Account Backup** — Add account backup functionality with improved data handling
- **Database Schema** — Update database schema to version 10 with enhanced AccountRecord and ExpenseRecord entities
- **Account Methods** — Add update method in AccountDao for better account management
- **Budget Refactoring** — Replace BudgetRecord with Budget entity and add budget import functionality
- **Enhanced Sync** — Improve sync operations with better data flow

### Quality of Life

- **History Tab** — Reset default selected tab in HistoryScreen and remove unused user adjustment logic
- **Service Enhancement** — Enhance QuickLogTileService to use PendingIntent for activity launch

---

## [v0.2.1] - 2026-03-31

### Features

- **Quick Log Widget** — Add Quick Log tile service with UI integration for quick expense logging
- **Quick Log Activity** — Implement QuickLogActivity and ViewModel for expense logging with dedicated UI
- **Quick Logging Shortcuts** — Add shortcut for quick logging of expenses with metadata configuration
- **Gradle Configuration** — Update Gradle properties for Java toolchain configuration and auto-detection

---

## [v0.2] - 2026-03-30

A significant update focusing on dropdown management, sync improvements, and transaction handling enhancements.

### Features

- **Dropdown Management**
  - Comprehensive dropdown management functionality with CRUD operations
  - Dropdown import functionality with enhanced sync operations
  - Default seeding logic for dropdown initialization
  - Support for managing Expense Categories, Income Categories, Account Groups, and Payment Modes

- **Transaction Management**
  - Comprehensive CRUD operations for expense records
  - Enhanced sync action handling
  - Remote record import with improved duplicate detection
  - Streamlined data comparison logic

- **Data Layer**
  - Update database version to 8
  - Enhance expense record handling with remote import functionality
  - Refactor remote record import for better data integrity

- **Sync Enhancements**
  - Improved sync functionality with detailed response handling
  - UI status updates with visual sync status indicator
  - Retry functionality to sync status indicator in LogScreen
  - Better error handling and recovery

### Quality of Life

- **Build Configuration** — Update build.gradle.kts to improve apps script URL handling

---

## [v0.1.0] - 2026-03-25

Initial release with core expense tracking and account management functionality.

### Features

- **Transaction Logging**
  - Add, edit, and delete transactions with support for Expense, Income, and Transfer types
  - Date picker and category selection for each transaction
  - Remarks and description fields for detailed tracking
  - Transfer flow with From Account and To Account selection
  - Sync status indicator with ability to retry failed synchronizations

- **History & Analysis**
  - Multi-tab history experience:
    - Daily: Grouped records by date
    - Calendar: Month view with per-day income/expense and category markers
    - Monthly: Rollups with expandable weekly breakdowns
    - Total: Dashboard with budget and account summaries
  - Insights screen with:
    - Current month income/expense/balance summary cards
    - Category-wise spend visualization
    - 6-month expense trend analysis

- **Budget Management**
  - Set and edit monthly total budget
  - Per-category budget configuration
  - Automatic "Other" budget calculation
  - Budget data included in sync backup/import flows

- **Account Management**
  - Dedicated Accounts tab with full management UI
  - Add accounts with configurable account groups
  - Account list grouped by account group
  - Account detail screen with monthly period navigation
  - Running balance statement for each account

- **Data Synchronization**
  - Offline-first sync architecture (write to Room first, sync to Google Sheets)
  - Background synchronization with visible sync status

- **Dropdown & Configuration**
  - In-app management of dropdown options:
    - Expense Categories
    - Income Categories
    - Account Groups
    - Payment Modes
  - Add, delete, and reorder capabilities for all options

- **CSV Support**
  - CSV parsing functionality
  - CSV export capability

- **UI/UX**
  - Material Design 3 inspired UI with Jetpack Compose
  - Custom typography configuration (Type.kt)

### Documentation

- Initial README.md outlining project features, tech stack, and device connection methods

---

## Implementation Details

### Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Database**: Room with SQLite
- **Dependency Injection**: Hilt
- **Asynchronous**: Kotlin Coroutines
- **Charting**: Vico Library (v2.x)
- **Image Loading**: Coil
- **Networking**: Retrofit (for Google Sheets integration)
- **Sync Backend**: Google Apps Script

### Architecture

- Clean Architecture with Repository pattern
- MVVM pattern with ViewModels and StateFlow
- Structured concurrency with Coroutines
- Offline-first data synchronization

---

[Unreleased]: https://github.com/yourusername/l-edgar/compare/v1.0.1...HEAD
[v1.0.1]: https://github.com/yourusername/l-edgar/compare/v1.0.0...v1.0.1
[v1.0.0]: https://github.com/yourusername/l-edgar/compare/v0.3...v1.0.0
[v0.3]: https://github.com/yourusername/l-edgar/compare/v0.2.1...v0.3
[v0.2.1]: https://github.com/yourusername/l-edgar/compare/v0.2...v0.2.1
[v0.2]: https://github.com/yourusername/l-edgar/compare/v0.1.0...v0.2
[v0.1.0]: https://github.com/yourusername/l-edgar/releases/tag/v0.1.0

package com.issaczerubbabel.ledgar.ui.screens

data class ChangelogRelease(
    val version: String,
    val date: String,
    val features: List<String>,
    val fixes: List<String>,
    val qol: List<String>
)

val changelogReleases: List<ChangelogRelease> = listOf(
    ChangelogRelease(
        version = "v1.0.3",
        date = "2026-04-18",
        features = listOf(
            "Insights screen now supports flexible period scopes (Weekly, Monthly, Yearly, Select Period) with period navigation and custom date-range selection",
            "Expense Breakdown now includes tabbed Expense and Income category views with the same interactive donut experience",
            "Cash Flow now supports category filtering with an explicit All Categories option and time bucketing controls for Daily, Weekly, Monthly, and Yearly views",
            "Account Balance As-Of now supports precise timestamp values with a Set to Now shortcut in add/edit account flows",
            "Added advanced Sheets sync conflict resolution UI"
        ),
        fixes = listOf(
            "Cash Flow chart now refreshes plotted series correctly when switching top period months, preventing stale bars/lines and stale Y-axis scale carryover",
            "Cash Flow income visibility improved by switching bar mode to grouped columns and hardening transaction type matching for imported data with extra whitespace",
            "Cash Flow marker tooltip now prioritizes spent amount first, followed by income and guide-line values for clearer per-bucket amount reading",
            "Cash Flow marker content now includes Income, Expense, Avg/day, and Max/day values for each highlighted bucket",
            "Account balance math now compares transaction timestamps against precise As-Of values, including same-day hour/minute boundaries",
            "Quick Settings quick-add now runs in an isolated transient task, so dismissing the sheet no longer reveals the main app behind it",
            "Removed legacy duplicate-skip resolution plumbing and standardized Sheets conflict handling on timestamp-based conflict flow"
        ),
        qol = listOf(
            "Quick Settings tile click now stays in quick-entry flow and updates tile state without opening the main app tabs",
            "Apps Script setup URL field now has a one-tap Paste action with clipboard fill and immediate keyboard hide",
            "Insights breakdown tabs now restore the selected-tab accent underline while keeping the gray card-matched background",
            "Insights cash-flow card now shows a small Graph mode hint (Bars/Lines) under the controls",
            "More tab Appearance now includes a Cash Flow Graph style selector with Bars and Lines options",
            "Breakdown tab indicator contrast improved on the gray card-matched tab row background",
            "Monthly cash-flow granularity now shows all available months instead of being constrained by the selected top-month period",
            "Insights breakdown tab selector now uses a card-matching gray surface instead of a dark background",
            "Cash Flow chart now renders thicker stacked bars and overlays average-per-day plus budget-aware max-per-day guide lines",
            "Insights card visuals refined: removed redundant chart titles, aligned chart background with card surface, simplified daily x-axis labels, and removed chart grid guidelines",
            "Added .tmp and .tmp_vico_src to gitignore for local artifact cleanup",
            "Conflict resolution now emits per-action audit snackbars with exact resolved and remaining counts"
        )
    ),
    ChangelogRelease(
        version = "v1.0.2",
        date = "2026-04-10",
        features = listOf(
            "In-app changelog screen wired into Settings with dedicated navigation route",
            "Apps Script setup screen now ships the latest backend script template including transaction schema v2",
            "Transfer sync schema extended with explicit from/to account name support across app and Sheets",
            "App now opens directly into Log Transaction for faster first-action entry",
            "Settings now includes App Lock controls with unlock method selection and configurable re-lock timeout",
            "Custom in-app PIN unlock added with secure hash and salt storage",
            "Added one-time Apps Script endpoint action (target=transactions, action=migrate) to force historical transaction-sheet migration to v2 layout"
        ),
        fixes = listOf(
            "Editing an existing account now preserves linked transaction account references so Account Details no longer goes blank after save",
            "History transaction rows now hide non-applicable zero amount labels by type (Income/Expense/Transfer)",
            "Accounts, History, and Bookmarks screens now collect ViewModel flows with collectAsStateWithLifecycle for lifecycle-safe reactive updates",
            "Completed app-wide screen migration from collectAsState to collectAsStateWithLifecycle for consistent lifecycle-aware Flow collection",
            "MainActivity, QuickLogActivity, and AppNavigation now also use collectAsStateWithLifecycle to align lifecycle-aware collection across app entry and navigation",
            "Google Sheets transaction parser hardened for mixed legacy and v2 row formats",
            "Added safe Apps Script migration helper for transaction columns (From/To Account Name)",
            "Apps Script migration now auto-creates a timestamped backup sheet and rewrites transaction rows into canonical v2 columns to fix shifted From/To/Remarks/Synced/Bookmark data",
            "Apps Script transaction fetch parser now defensively reads previously shifted rows and normalizes non-transfer account fields for stable Trans tab rendering",
            "Google Sheets import now refreshes existing local records by remote timestamp so migrated account-name fields are reflected in Trans tab without duplicate inserts",
            "Budget sync from Sheets now normalizes MonthYear values to yyyy-MM in Apps Script and app import, fixing dropped budget rows caused by JavaScript Date-string formats",
            "Import from Sheets duplicate review now uses non-destructive skip decisions: checked rows are skipped from future imports while unchecked rows are kept for later review",
            "Duplicate review now shows conflicting local-record details and adds Skip All / Clear controls for safer bulk decisions",
            "Apps Script now stores duplicate skip decisions in a dedicated sheet and filters skipped timestamps during transaction fetch without deleting transaction history",
            "Trans tab now shows transfer amounts on the right with a dedicated transfer color, distinct from income and expense",
            "Transfer destination account name persistence fixed through Room, DTO mapping, and import/export flows",
            "Budget import now normalizes legacy month formats to yyyy-MM to prevent missing restored budgets",
            "Initial Log-screen exit now enforces configured lock method and keeps user on Log when authentication is cancelled",
            "Added timeout-based re-lock on app foreground transitions after background inactivity",
            "Back from locked Log screen now directly triggers system biometric or pattern unlock in system-capable modes",
            "Fixed biometric prompt host mismatch so back-triggered system unlock now opens correctly on the Log screen",
            "Fixed root Log back behavior so back no longer stalls on start destination and now proceeds into app entry flow",
            "Root Log back and Enter App actions now always trigger configured auth when app lock is enabled",
            "System auth failures now show specific diagnostics for missing lock, missing enrollment, and unsupported policy states",
            "Root Log Enter App and back actions now always require system credential unlock before opening main tabs",
            "Fixed app-lock toggle stability so enabling or disabling lock in Settings no longer crashes",
            "Startup routing now respects lock state: Transactions opens first when lock is off; Log gate opens first when lock is on",
            "Biometric unlock handoff now uses guarded navigation to prevent crash when transitioning from Log gate to Transactions",
            "Sheets transaction import now reads both legacy 11-column and v2 13-column schemas correctly, preventing missing account labels in Trans tab",
            "Trans tab account label rendering now falls back to imported account-name text when account ID resolution is unavailable",
            "One-time transaction-sheet migration now backfills historical transfer rows into From/To Account Name using legacy Account Name splits"
        ),
        qol = listOf(
            "Settings icon row alignment refined for cleaner visual consistency",
            "Instruction policy now enforces changelog update on every change-producing prompt",
            "Added workspace hook guard to block task completion when ChangelogData is not updated",
            "Hook runner now uses Windows PowerShell executable to avoid pwsh-not-found failures on Windows",
            "Log screen now starts non-critical sync status observation after first composition to protect cold-start responsiveness",
            "Security unlock flow now offers clearer runtime choices when both system authentication and app PIN are enabled",
            "Added Enter App button on Log screen bottom bar for root Log launch so users can enter main tabs directly",
            "Enter App button repositioned above tab region and app-entry route transition now uses smoother fade-slide animation",
            "Added one-tap deep link to system security settings from lock diagnostics dialog",
            "Bottom tab bar now fades and slides with route transitions so content and tabs appear together when entering the app",
            "Enabling app lock during an active session now applies on next app entry instead of forcing an immediate in-session redirect",
            "Corrected pager-state call typo in History screen to restore clean Kotlin compilation",
            "Google Apps Script deployment template is now aligned with runtime script for deterministic legacy/v2 transaction-row parsing"
        )
    ),
    ChangelogRelease(
        version = "v1.0.1",
        date = "2026-04-10",
        features = listOf(
            "Responsive text sizing for History and Monthly screens to enhance readability across devices"
        ),
        fixes = listOf(
            "Improved category mapping logic to handle blank values",
            "Fixed transfer categorization",
            "Updated navigation logic to properly pop screen routes",
            "Improved state management"
        ),
        qol = listOf()
    ),
    ChangelogRelease(
        version = "v1.0.0",
        date = "2026-04-08",
        features = listOf(
            "Transaction search with multiple filters and results display",
            "Batch transaction editing (delete, update dates, categories, assets, descriptions)",
            "Transaction bookmarking with UI integration",
            "Account labels for transactions in History screen",
            "Overall Account Stats screen with detailed analytics",
            "Category-wise expense visualization with ExpenseDonutChart",
            "6-month cash flow trend visualization with CashFlowBarChart",
            "Vico Charting Library integration for professional visualizations",
            "Interactive chart markers and enhanced formatting",
            "Theme selection system with multiple color schemes",
            "Red theme option for improved customization",
            "Month and year selection in History",
            "Show/hide functionality for hidden accounts",
            "Account display order management",
            "Enhanced account import functionality",
            "Account balance calculations with running balance statements",
            "Account deletion with linked transaction handling",
            "Total Tab Screen with budget tracking",
            "Budget progress rows with ideal-progress markers",
            "Account summary dashboard",
            "Per-category budget management",
            "Budget import functionality",
            "CSV data export capability"
        ),
        fixes = listOf(
            "Corrected regex patterns in timestamp normalization",
            "Fixed accurate date parsing",
            "Improved flexible date parsing with unit tests",
            "Enhanced logging for unparseable dates"
        ),
        qol = listOf(
            "Improved text overflow handling across screens",
            "Better AccountDetailScreen layout",
            "Enhanced account management repository",
            "Fallback account creation for transfers",
            "Improved sync worker for account backup logic",
            "Back navigation to LogScreen",
            "Improved snackbar messaging"
        )
    ),
    ChangelogRelease(
        version = "v0.3",
        date = "2026-04-03",
        features = listOf(
            "Account backup functionality",
            "Database schema update to version 10",
            "AccountDao update method",
            "Budget entity refactoring",
            "Budget import functionality",
            "Enhanced sync operations"
        ),
        fixes = listOf(),
        qol = listOf(
            "Reset default selected tab in History",
            "Removed unused user adjustment logic in ViewModels",
            "Enhanced QuickLogTileService with PendingIntent"
        )
    ),
    ChangelogRelease(
        version = "v0.2.1",
        date = "2026-03-31",
        features = listOf(
            "Quick Log widget for instant expense entry",
            "Quick Log tile service with UI integration",
            "QuickLogActivity for dedicated logging interface",
            "App shortcut for quick expense logging",
            "Gradle Java toolchain configuration"
        ),
        fixes = listOf(),
        qol = listOf()
    ),
    ChangelogRelease(
        version = "v0.2",
        date = "2026-03-30",
        features = listOf(
            "Dropdown management with complete CRUD operations",
            "Dropdown import functionality",
            "Default dropdown seeding logic",
            "Transaction CRUD operations",
            "Remote record import with duplicate detection",
            "Database version update to 8",
            "Enhanced sync functionality with detailed response handling",
            "UI status updates with sync status indicator",
            "Retry functionality for failed syncs"
        ),
        fixes = listOf(),
        qol = listOf(
            "Improved apps script URL handling in build configuration",
            "Streamlined data comparison logic",
            "Better error handling and recovery"
        )
    ),
    ChangelogRelease(
        version = "v0.1.0",
        date = "2026-03-25",
        features = listOf(
            "Add, edit, and delete transactions",
            "Support for Expense, Income, and Transfer types",
            "Date picker and category selection",
            "Transfer flow with From/To Account selection",
            "Sync status indicator with manual retry",
            "Multi-tab history experience (Daily, Calendar, Monthly, Total)",
            "Insights screen with current month summary",
            "Category-wise spend visualization",
            "6-month expense trend analysis",
            "Total budget management",
            "Per-category budget configuration",
            "Automatic 'Other' budget calculation",
            "Dedicated Accounts tab with full management",
            "Account grouping and organization",
            "Monthly account detail screens",
            "Running balance statements",
            "In-app dropdown management (Categories, Account Groups, Payment Modes)",
            "CSV parsing and export functionality",
            "Google Sheets integration",
            "Offline-first sync architecture"
        ),
        fixes = listOf(),
        qol = listOf(
            "Material Design 3 inspired UI with Jetpack Compose",
            "Custom typography configuration",
            "Light and Dark theme support"
        )
    )
)

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
        version = "v1.0.2",
        date = "2026-04-10",
        features = listOf(
            "In-app changelog screen wired into Settings with dedicated navigation route",
            "Apps Script setup screen now ships the latest backend script template including transaction schema v2",
            "Transfer sync schema extended with explicit from/to account name support across app and Sheets"
        ),
        fixes = listOf(
            "History transaction rows now hide non-applicable zero amount labels by type (Income/Expense/Transfer)",
            "Google Sheets transaction parser hardened for mixed legacy and v2 row formats",
            "Added safe Apps Script migration helper for transaction columns (From/To Account Name)",
            "Transfer destination account name persistence fixed through Room, DTO mapping, and import/export flows",
            "Budget import now normalizes legacy month formats to yyyy-MM to prevent missing restored budgets"
        ),
        qol = listOf(
            "Settings icon row alignment refined for cleaner visual consistency",
            "Instruction policy now enforces changelog update on every change-producing prompt",
            "Added workspace hook guard to block task completion when ChangelogData is not updated"
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

package com.issaczerubbabel.ledgar.ui.screens

/**
 * One release, in Keep a Changelog sections. The in-app changelog shows [added], [changed] and
 * [fixed]; [developer] notes only appear in CHANGELOG.md. ChangelogMarkdownTest keeps CHANGELOG.md
 * in step with this list, and the release workflow publishes that file's section as release notes.
 */
data class ChangelogRelease(
    val version: String,
    val date: String,
    val added: List<String> = emptyList(),
    val changed: List<String> = emptyList(),
    val fixed: List<String> = emptyList(),
    val developer: List<String> = emptyList()
)

val changelogReleases: List<ChangelogRelease> = listOf(
    ChangelogRelease(
        version = "v1.1.0",
        date = "2026-09-26",
        added = listOf(
            "Stats: a redesigned tab. Pick Cycle, Week, Month or Year, tap the dates to jump to a date or a custom range, or swipe the date strip to move between periods",
            "Stats: the first card shows Left to spend in a salary cycle (the same figure as the Budget tab), or Left over with a bar showing where your income went",
            "Stats: a spending pace chart against the same point last period and your budget, with where you're heading. Press and drag to read any day",
            "Stats: Where it went ranks your categories against what each usually costs. In Cycle view it shows your buckets against their limits",
            "Stats: a daily spending calendar, Paid from (cash and bank against cards), period-by-period bars of spent, saved and earned, and your biggest expenses",
            "Stats: tap a category or bucket and it opens into a sheet with its total, usual cost, daily average, trend and transactions",
            "Chart colours setting: choose from five palettes for the Stats charts. All but Classic green and red stay readable with red-green colour blindness",
            "Categories and account groups can count as saving, refund or savings in Stats (Manage Categories & Dropdowns). Money you save no longer counts as spending, and refunds reduce spending instead of counting as income",
            "Budget tab: salary-cycle budgeting with buckets. See what's left to spend, your daily pace, and each bucket against an even pace",
            "Budget: start a new cycle (it can spot your salary and carry your buckets over), plan your buckets, edit a bucket and its categories, and browse past cycles",
            "Budget: salary cycles and buckets are backed up to your Google Sheet and come back when you import",
            "Add Transaction: a new keypad layout with chips for category, account and note, and a searchable category picker that can add a new category on the spot",
            "Add Transaction and Quick Add show which bucket the category falls in and what's left of it as you type",
            "Sheets sync: a screen to resolve conflicts between the phone and the Sheet",
            "Accounts: set the exact time a starting balance applies from, with a Set to now shortcut",
            "Two-way sync with Google Sheets: edits and rows typed into the Sheet come to the phone when you open the app or tap Sync now",
            "Every transaction has a permanent ID shared with a new ID column in the Sheet, so syncing again or reinstalling never duplicates rows",
            "A transaction changed both on the phone and in the Sheet becomes a conflict for you to resolve in Settings, instead of one side silently winning",
            "Settings: Sync now, Resolve sync conflicts, and Find duplicate transactions",
            "Deleting many rows in the Sheet at once asks before removing them from the phone, and lets you put them back"
        ),
        changed = listOf(
            "Stats: new periods slide in from the side you moved towards, views fade through, numbers count to their new value and charts move smoothly. All of it turns off with Android's animations",
            "Stats: the header shrinks into a slim bar as you scroll, so lower cards always show which period they're for",
            "Quick Add uses the same keypad and category picker as Add Transaction, with the keypad at the bottom, and the Quick Settings tile stays in quick entry",
            "Add Transaction keys and chips animate when pressed, and the amount pulses when a transaction is saved",
            "Export moved to Settings, under Import from CSV. It's now called Export to CSV, has its own month picker, and names the file after its date range",
            "The Total tab is gone: the Budget tab replaces it",
            "Database Setup: one-tap Paste for the web app URL",
            "Rupee amounts use Indian digit grouping (₹1,20,000), with digits that line up as they change",
            "The Cash Flow Graph setting (bars or lines) is replaced by Chart colours",
            "Rebuilt on Kotlin 2.3 and Compose 1.11 with Material 3 1.4, for smoother screens",
            "On a fresh install, transactions pulled from the Sheet land on your restored accounts instead of a placeholder Cash account",
            "Sync pauses with an Update script notice when your Apps Script is too old, instead of writing to it unsafely",
            "Update your Apps Script from Database Setup after installing: two-way sync needs it, and it backs up buckets and category roles"
        ),
        fixed = listOf(
            "Stats: \"Spent vs last period\" compares the same days of each period. A flat month used to show +58% because the 1st's rent was skipped",
            "Stats: charts are drawn to scale, days with no spending are no longer skipped, and a year is drawn by month instead of hundreds of daily bars",
            "Stats: the budget line comes from your salary cycles instead of sitting at ₹0, and category colours no longer repeat",
            "Stats: card and cash spending are split correctly (card was always ₹0), and the transfer total counts only transfers",
            "Sync: a retried sync no longer adds duplicate rows to your Sheet",
            "Sync: edits, deletes and bookmarks made during a sync are no longer lost, and saving quickly no longer cancels a sync or shows Sync failed",
            "Sync: backups can no longer hold up transactions, and quick log entries, bookmarks and bulk edits sync straight away",
            "Sync: changes that hadn't synced when the app closed now sync when it opens again",
            "Sync: deleting an account with its transactions removes them from the Sheet too, and deleting something already gone from the Sheet no longer gets stuck",
            "A fresh install no longer overwrites your Sheet's accounts, categories and budgets with its defaults",
            "Importing from Sheets no longer crashes when opening conflict resolution",
            "Account balances respect the exact time a starting balance applies from",
            "Dismissing the Quick Settings quick add no longer reveals the app behind it",
            "The Trans. tab no longer gets stuck between pages after a long tab jump",
            "The date picker opens on the selected day in time zones ahead of UTC",
            "Add Transaction: picking between two accounts with the same name saves the one you tapped, and a new category keeps the right type",
            "Long bucket names wrap, and bucket amounts line up",
            "Export no longer shows its date hint in red before you type anything",
            "The Apps Script shown in Database Setup includes description support again"
        ),
        developer = listOf(
            "Stats numbers come from one pure, tested StatsReport module; StatsPeriod owns period stepping",
            "Two-way sync (ADR-0003): Transaction IDs, idempotent locked upserts in the Apps Script, version-checked pulls and a script version handshake, with transaction-sync node tests",
            "Room migrations 18 -> 19 (sync IDs, synced revisions, sheet conflicts) and 19 -> 20 (dropdown roles, tolerant of builds that added the column earlier)",
            "CONTEXT.md glossary and ADRs 0001-0004 (offline-first writes, per-user Apps Script, two-way sync by Transaction ID, Stats roles)",
            "Bucket-budget tables with migration tests; the legacy budgets table is kept for restores",
            "Instrumented cycle tests on a real in-memory Room database; Apps Script node tests cover both script copies and fail if they drift",
            "Toolchain: Kotlin 2.3.21 with the Compose compiler plugin, Compose BOM 2026.06.01, Room 2.8.5, Hilt 2.57.2, KSP 2.3.12; all deprecations cleared",
            "New Release workflow: pushing a vX.Y.Z tag publishes a signed APK and this changelog section as a GitHub Release",
            "New staging build type: the release build with R8, installed as .staging and signed with the debug key, for testing shrunk builds",
            "versionCode is derived from versionName (major*10000 + minor*100 + patch)",
            "Debug builds install next to the release app as L.Edgar (Debug) and can use a test Apps Script from APPS_SCRIPT_URL_DEBUG",
            "Android CI also builds claude/** branches, with an Oracle JDK to match the pinned toolchain"
        )
    ),
    ChangelogRelease(
        version = "v1.0.2",
        date = "2026-04-10",
        added = listOf(
            "App Lock with a choice of unlock method and a re-lock timeout",
            "A custom in-app PIN, stored as a salted hash",
            "An in-app changelog in Settings",
            "The app opens straight into Log Transaction",
            "Transfers record their from and to account names in the Sheet",
            "A one-time Apps Script action that moves old transaction rows to the new column layout"
        ),
        changed = listOf(
            "Clearer unlock choices when both system unlock and an app PIN are on",
            "An Enter App button on the Log screen, with a smoother transition into the app",
            "A shortcut from lock diagnostics to your phone's security settings",
            "Turning App Lock on mid-session applies the next time you enter the app",
            "Settings icons line up"
        ),
        fixed = listOf(
            "Editing an account no longer blanks its Account Details",
            "History hides zero amounts that don't apply to a transaction's type",
            "Importing from Sheets reads both the old and new row layouts, keeps account names, and refreshes existing records without duplicating them",
            "Budgets restore from Sheets whatever format their month was saved in",
            "Duplicate review keeps unchecked rows for later and shows the local record it clashes with",
            "Transfers show on the right in their own colour on the Trans. tab",
            "App Lock: Back and Enter App always ask for your unlock method, biometric unlock opens reliably, and toggling the lock no longer crashes",
            "Unlock failures explain whether the screen lock or fingerprint is missing"
        ),
        developer = listOf(
            "All screens collect flows with collectAsStateWithLifecycle",
            "The Apps Script migration backs up the sheet before rewriting rows",
            "A hook blocks finishing a task without a changelog entry",
            "The deployed Apps Script template matches the runtime script"
        )
    ),
    ChangelogRelease(
        version = "v1.0.1",
        date = "2026-04-10",
        added = listOf(
            "History and Monthly text scales with your screen size"
        ),
        fixed = listOf(
            "Blank categories are handled",
            "Transfers are categorised correctly",
            "Back navigation closes screens properly"
        ),
        developer = listOf(
            "State management tidied up"
        )
    ),
    ChangelogRelease(
        version = "v1.0.0",
        date = "2026-04-08",
        added = listOf(
            "Search transactions with filters",
            "Bulk edit transactions: delete, or change dates, categories, accounts or descriptions",
            "Bookmark transactions",
            "Overall Account Stats with charts of spending by category and six months of cash flow",
            "Themes, including a Red theme",
            "Pick the month and year in History",
            "Hide accounts and set their order",
            "Account balances with running statements, and deleting an account with its transactions",
            "Budgets per category, with progress against an ideal pace",
            "Import accounts and budgets, and export to CSV"
        ),
        changed = listOf(
            "Long text no longer overflows",
            "A tidier Account Detail screen",
            "Transfers create a missing account automatically",
            "Clearer snackbar messages"
        ),
        fixed = listOf(
            "Dates in every supported format are read correctly"
        ),
        developer = listOf(
            "Vico charts",
            "Date parsing unit tests and logging for dates that can't be read",
            "Account backup in the sync worker"
        )
    ),
    ChangelogRelease(
        version = "v0.3.0",
        date = "2026-04-03",
        added = listOf(
            "Accounts are backed up to your Sheet",
            "Budgets can be imported"
        ),
        changed = listOf(
            "History opens on its first tab",
            "The Quick Log tile opens more reliably"
        ),
        developer = listOf(
            "Database version 10; budget entity refactored"
        )
    ),
    ChangelogRelease(
        version = "v0.2.1",
        date = "2026-03-31",
        added = listOf(
            "Quick Log: a home-screen widget, a Quick Settings tile and an app shortcut for logging an expense instantly"
        ),
        developer = listOf(
            "Gradle Java toolchain configured"
        )
    ),
    ChangelogRelease(
        version = "v0.2.0",
        date = "2026-03-30",
        added = listOf(
            "Manage categories and dropdowns, with sensible defaults and import from your Sheet",
            "Import transactions from your Sheet, skipping duplicates",
            "A sync status indicator with retry"
        ),
        changed = listOf(
            "Better error messages and recovery"
        ),
        developer = listOf(
            "Database version 8; sync responses handled in detail"
        )
    ),
    ChangelogRelease(
        version = "v0.1.0",
        date = "2026-03-25",
        added = listOf(
            "Add, edit and delete expenses, income and transfers",
            "History in Daily, Calendar, Monthly and Total views",
            "Insights with this month's summary, spending by category and a six-month trend",
            "Total and per-category budgets",
            "An Accounts tab with groups, monthly details and running balances",
            "Manage categories, account groups and payment modes in the app",
            "Import and export CSV",
            "Google Sheets sync that works offline first"
        ),
        changed = listOf(
            "Material 3 design with light and dark themes"
        )
    )
)

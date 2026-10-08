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
        version = "v1.4.0",
        date = "2026-10-08",
        added = listOf(
            "Dropdowns: an Account group can count as Liability, such as a credit card or loan"
        ),
        changed = listOf(
            "Accounts: the list, an Account's page and Overall Stats all show the same balance for an Account",
            "Accounts: a Transaction changes a balance only when dated after its As-of date, however late you log it",
            "Accounts: Liabilities come from the Liability role, and an overpaid card lowers them",
            "Accounts: Assets, Liabilities and Total leave out Accounts that aren't included in totals",
            "Overall Stats: moving money between your own Accounts no longer counts as income and expense",
            "Income and expense amounts are lighter on dark backgrounds, so they're easier to read",
            "Account page: Balance today, Transfer and Add buttons, and one scrolling statement in place of the chart",
            "Account page: each month shows Opening, In, Out and Closing that add up, and rows show the balance after",
            "Accounts: a Net worth card with this month's change, and collapsible groups with their subtotals",
            "Accounts: drag Accounts and groups in Edit order; Show/Hide and Delete live in an Account's edit form",
            "Accounts: one Add/Edit form; a Liability asks for the Amount owed, and the Initial balance is set once",
            "Accounts: Archive or Delete… in the edit form; Delete… moves an Account's transactions or deletes them"
        ),
        fixed = listOf(
            "Accounts: old transfers that only saved the destination's name count on that Account everywhere",
            "Accounts: totals and balances add up to the paisa, with no stray digits or −₹0.00"
        ),
        developer = listOf(
            "AccountMath: one pure module for balances, totals, statements, net worth, cash flow and movers, with tests",
            "Room 23 → 24: reconciledAt, Liability roles from the old keywords, and Transfers linked to their destination",
            "Adjustment Transaction type (ADR-0009): syncs with its sign, moves only Account balances, never Stats or Buckets",
            "formatMoney: exact ₹ amounts with Indian grouping, a true minus and an optional +, with tests",
            "Accounts tab and Account page state come from Android-free AccountsTab and AccountPage, with tests",
            "AccountForm: the form's validation, signs and delete rule are Android-free, with tests; AddAccountScreen is gone"
        )
    ),
    ChangelogRelease(
        version = "v1.3.2",
        date = "2026-10-05",
        changed = listOf(
            "More: sync status, Trips, Auto-capture and your data tools on one screen, with settings behind the gear",
            "Settings: grouped into Appearance, Security, Sync & backup, About and a Danger zone",
            "Erase all local data (was Reset All Data): sits in the Danger zone and needs you to type ERASE",
            "Theme option \"System MUI\" is now \"System default\""
        ),
        developer = listOf(
            "More tab: MoreScreen and SettingsScreen split, with new features added in one place (MoreFeatures)"
        )
    ),
    ChangelogRelease(
        version = "v1.3.1",
        date = "2026-10-05",
        added = listOf(
            "Trips: a Summary tab with charts by Category, Paid vs Share and spend by day",
            "Trips: every person has a colour, shown on their expenses, balances and payments",
            "Trips: tap Does this settle everyone? or a payment to see how the balances add up",
            "Trips: export a statement for one person, a balances CSV or a PDF report"
        ),
        changed = listOf(
            "Trips: Custom split replaces Adjust and Exact. Type an amount to lock it; the rest splits equally",
            "Trips: roomier screens that adapt to small phones, large fonts and tablets",
            "Changelog: shorter entries, each under the version it shipped in"
        ),
        developer = listOf(
            "Trip tables 22 → 23: person colours, and Adjust / Exact splits become Custom with every Share unchanged",
            "TripMath gains the Custom split, settle-up check and ledgers; TripSummary and TripExports have tests",
            "ChangelogMarkdownTest enforces the changelog style: length, sentence count, capitals, no repeats, ordered dates",
            "Changelog rebuilt from the release tags (0.x into 1.0.0, 1.0.1-1.0.2 into 1.1.0, auto-capture into 1.2.0)",
            "ui-ux-pro-max design skill (nextlevelbuilder/ui-ux-pro-max-skill) in .claude/skills",
            "Trip glossary: Suggested payment, Member colour and Locked amount in CONTEXT.md"
        )
    ),
    ChangelogRelease(
        version = "v1.3.0",
        date = "2026-10-01",
        added = listOf(
            "Trips: log shared payments with who paid and who was in",
            "Trips: split equally, give one person a bit extra, or enter exact amounts",
            "Trips: see balances and the fewest payments to settle up, and pay what you owe by UPI",
            "Trips: share a summary or a CSV, then post only your share to Transactions",
            "Trips: a banner on Trans. while a trip is active, and Add to trip in the review inbox"
        ),
        developer = listOf(
            "Trip tables (migration 21 → 22), a pure TripMath module and TripRepository, with tests",
            "ADR-0008 (post your share, not what you paid) and the Trip terms in CONTEXT.md",
            "pstack and poteto-mode agent skills in .claude/skills"
        )
    ),
    ChangelogRelease(
        version = "v1.2.0",
        date = "2026-10-01",
        added = listOf(
            "Auto-capture: bank and UPI alerts become suggestions in a review inbox. Turn it on in More > Auto-capture",
            "Auto-capture: reads HDFC, City Union Bank, Axis, Google Pay, Paytm and bank SMS alerts",
            "Auto-capture: learns your categories from what you confirm, and Always use this saves a rule",
            "Auto-capture: confirm from a notification; the lock screen hides the amount",
            "Auto-capture: suggestions keep the alert's date and exact amount",
            "Auto-capture: alerts it couldn't read are listed in More > Auto-capture"
        ),
        changed = listOf(
            "Add Transaction keeps the date you picked for your next entry"
        ),
        fixed = listOf(
            "Picked dates no longer slip to the day before in some time zones",
            "The release build no longer crashes when auto-capture starts"
        ),
        developer = listOf(
            "Auto-capture: notification listener, per-bank parsers, categorization and local-only tables (docs/AUTO_CAPTURE.md, ADRs 0005–0007)",
            "Migration 20 → 21 adds the capture tables and repairs development installs",
            "R8 keep rules for Gson's TypeToken, and the release variant's lint errors cleared",
            "The release workflow prints apksigner's output and checks the signing certificate",
            "/daily-idea and /daily-ship commands, and the mattpocock agent skills"
        )
    ),
    ChangelogRelease(
        version = "v1.1.0",
        date = "2026-09-26",
        added = listOf(
            "Stats: a redesigned tab with Cycle, Week, Month and Year views",
            "Stats: Left to spend or Left over, a spending pace chart, and Where it went",
            "Stats: a spending calendar, Paid from, period bars and your biggest expenses",
            "Stats: tap a category or bucket to see its details and transactions",
            "Chart colours: five palettes for the Stats charts",
            "Categories and account groups can count as saving, refund or savings",
            "Budget: salary cycles with buckets, a daily pace and past cycles",
            "Budget: cycles and buckets back up to your Sheet",
            "Add Transaction: a keypad-first layout and a searchable category picker",
            "Add Transaction and Quick Add show the category's bucket and what's left in it",
            "Two-way sync: edits made in the Sheet come to the phone",
            "Sync conflicts wait for you to resolve them instead of one side winning",
            "Settings: Sync now, Resolve sync conflicts and Find duplicate transactions",
            "Accounts: set the exact time a starting balance applies from",
            "App Lock with fingerprint, screen lock or an app PIN, and a re-lock timeout",
            "A changelog in Settings"
        ),
        changed = listOf(
            "Stats animates between periods, and follows Android's animation setting",
            "Quick Add uses the same keypad as Add Transaction",
            "Export moved to Settings as Export to CSV, with its own month picker",
            "The Budget tab replaces the Total tab",
            "Amounts use Indian digit grouping (₹1,20,000)",
            "The app opens straight into Log Transaction",
            "Text in History and Monthly scales with your screen",
            "Update your Apps Script from Database Setup: two-way sync needs it"
        ),
        fixed = listOf(
            "Stats: period comparisons, chart scales, the budget line and the card/cash split",
            "Sync: no more duplicate rows, lost edits or stuck deletes",
            "A fresh install no longer overwrites your Sheet's accounts, categories and budgets",
            "Importing from Sheets reads both row layouts without crashing or duplicating",
            "Account balances respect the exact time a starting balance applies from",
            "Editing an account no longer blanks its details",
            "App Lock unlocks reliably and no longer crashes when you toggle it",
            "Transfers, blank categories and Back navigation behave correctly",
            "Quick Settings quick add, the Trans. tab and the date picker behave correctly"
        ),
        developer = listOf(
            "Stats logic in a pure, tested StatsReport module, with StatsPeriod for period stepping",
            "Two-way sync by Transaction ID (ADR-0003), with migrations 18 → 19 and 19 → 20",
            "Bucket-budget tables with migration and cycle tests",
            "CONTEXT.md glossary and ADRs 0001–0004",
            "Kotlin 2.3, Compose BOM 2026.06.01 and Room 2.8.5, with all deprecations cleared",
            "Release workflow: a vX.Y.Z tag publishes a signed APK as a GitHub Release",
            "A staging build type for testing R8 builds, and debug builds that install side by side",
            "versionCode derived from versionName, and CI builds claude/** branches",
            "collectAsStateWithLifecycle on every screen, and a hook that enforces the changelog"
        )
    ),
    ChangelogRelease(
        version = "v1.0.0",
        date = "2026-04-09",
        added = listOf(
            "Log expenses, income and transfers, offline first",
            "Google Sheets sync, and import of transactions, accounts and budgets from your Sheet",
            "History in Daily, Calendar, Monthly and Total views",
            "Insights and Overall Account Stats with charts",
            "Accounts with groups, running balances and statements",
            "Budgets per category",
            "Search, bulk edit and bookmarks",
            "Quick Log: a home-screen widget, a Quick Settings tile and an app shortcut",
            "Manage categories, account groups and payment modes",
            "Import and export CSV",
            "Light, dark and Red themes"
        ),
        developer = listOf(
            "Room database, Vico charts and date-parsing tests"
        )
    )
)

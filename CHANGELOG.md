# Changelog

All notable changes to L.Edgar. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

This file is generated from `app/src/main/java/com/issaczerubbabel/ledgar/ui/screens/ChangelogData.kt`,
which is also what the app shows under More > Changelog. Developer notes appear here only.

## [1.3.1] - 2026-10-05

### Changed

- Changelog: shorter entries, each under the version it shipped in

### Developer

- ChangelogMarkdownTest enforces the changelog style: length, sentence count, capitals, no repeats, ordered dates
- Changelog rebuilt from the release tags (0.x into 1.0.0, 1.0.1-1.0.2 into 1.1.0, auto-capture into 1.2.0)
- ui-ux-pro-max design skill (nextlevelbuilder/ui-ux-pro-max-skill) in .claude/skills

## [1.3.0] - 2026-10-01

### Added

- Trips: log shared payments with who paid and who was in
- Trips: split equally, give one person a bit extra, or enter exact amounts
- Trips: see balances and the fewest payments to settle up, and pay what you owe by UPI
- Trips: share a summary or a CSV, then post only your share to Transactions
- Trips: a banner on Trans. while a trip is active, and Add to trip in the review inbox

### Developer

- Trip tables (migration 21 → 22), a pure TripMath module and TripRepository, with tests
- ADR-0008 (post your share, not what you paid) and the Trip terms in CONTEXT.md
- pstack and poteto-mode agent skills in .claude/skills

## [1.2.0] - 2026-10-01

### Added

- Auto-capture: bank and UPI alerts become suggestions in a review inbox. Turn it on in More > Auto-capture
- Auto-capture: reads HDFC, City Union Bank, Axis, Google Pay, Paytm and bank SMS alerts
- Auto-capture: learns your categories from what you confirm, and Always use this saves a rule
- Auto-capture: confirm from a notification; the lock screen hides the amount
- Auto-capture: suggestions keep the alert's date and exact amount
- Auto-capture: alerts it couldn't read are listed in More > Auto-capture

### Changed

- Add Transaction keeps the date you picked for your next entry

### Fixed

- Picked dates no longer slip to the day before in some time zones
- The release build no longer crashes when auto-capture starts

### Developer

- Auto-capture: notification listener, per-bank parsers, categorization and local-only tables (docs/AUTO_CAPTURE.md, ADRs 0005–0007)
- Migration 20 → 21 adds the capture tables and repairs development installs
- R8 keep rules for Gson's TypeToken, and the release variant's lint errors cleared
- The release workflow prints apksigner's output and checks the signing certificate
- /daily-idea and /daily-ship commands, and the mattpocock agent skills

## [1.1.0] - 2026-09-26

### Added

- Stats: a redesigned tab with Cycle, Week, Month and Year views
- Stats: Left to spend or Left over, a spending pace chart, and Where it went
- Stats: a spending calendar, Paid from, period bars and your biggest expenses
- Stats: tap a category or bucket to see its details and transactions
- Chart colours: five palettes for the Stats charts
- Categories and account groups can count as saving, refund or savings
- Budget: salary cycles with buckets, a daily pace and past cycles
- Budget: cycles and buckets back up to your Sheet
- Add Transaction: a keypad-first layout and a searchable category picker
- Add Transaction and Quick Add show the category's bucket and what's left in it
- Two-way sync: edits made in the Sheet come to the phone
- Sync conflicts wait for you to resolve them instead of one side winning
- Settings: Sync now, Resolve sync conflicts and Find duplicate transactions
- Accounts: set the exact time a starting balance applies from
- App Lock with fingerprint, screen lock or an app PIN, and a re-lock timeout
- A changelog in Settings

### Changed

- Stats animates between periods, and follows Android's animation setting
- Quick Add uses the same keypad as Add Transaction
- Export moved to Settings as Export to CSV, with its own month picker
- The Budget tab replaces the Total tab
- Amounts use Indian digit grouping (₹1,20,000)
- The app opens straight into Log Transaction
- Text in History and Monthly scales with your screen
- Update your Apps Script from Database Setup: two-way sync needs it

### Fixed

- Stats: period comparisons, chart scales, the budget line and the card/cash split
- Sync: no more duplicate rows, lost edits or stuck deletes
- A fresh install no longer overwrites your Sheet's accounts, categories and budgets
- Importing from Sheets reads both row layouts without crashing or duplicating
- Account balances respect the exact time a starting balance applies from
- Editing an account no longer blanks its details
- App Lock unlocks reliably and no longer crashes when you toggle it
- Transfers, blank categories and Back navigation behave correctly
- Quick Settings quick add, the Trans. tab and the date picker behave correctly

### Developer

- Stats logic in a pure, tested StatsReport module, with StatsPeriod for period stepping
- Two-way sync by Transaction ID (ADR-0003), with migrations 18 → 19 and 19 → 20
- Bucket-budget tables with migration and cycle tests
- CONTEXT.md glossary and ADRs 0001–0004
- Kotlin 2.3, Compose BOM 2026.06.01 and Room 2.8.5, with all deprecations cleared
- Release workflow: a vX.Y.Z tag publishes a signed APK as a GitHub Release
- A staging build type for testing R8 builds, and debug builds that install side by side
- versionCode derived from versionName, and CI builds claude/** branches
- collectAsStateWithLifecycle on every screen, and a hook that enforces the changelog

## [1.0.0] - 2026-04-09

### Added

- Log expenses, income and transfers, offline first
- Google Sheets sync, and import of transactions, accounts and budgets from your Sheet
- History in Daily, Calendar, Monthly and Total views
- Insights and Overall Account Stats with charts
- Accounts with groups, running balances and statements
- Budgets per category
- Search, bulk edit and bookmarks
- Quick Log: a home-screen widget, a Quick Settings tile and an app shortcut
- Manage categories, account groups and payment modes
- Import and export CSV
- Light, dark and Red themes

### Developer

- Room database, Vico charts and date-parsing tests

[1.3.1]: https://github.com/issaczerubbabela/L-Edgar/releases/tag/v1.3.1
[1.3.0]: https://github.com/issaczerubbabela/L-Edgar/releases/tag/v1.3.0
[1.2.0]: https://github.com/issaczerubbabela/L-Edgar/releases/tag/v1.2.0
[1.1.0]: https://github.com/issaczerubbabela/L-Edgar/releases/tag/v1.1.0
[1.0.0]: https://github.com/issaczerubbabela/L-Edgar/releases/tag/v1.0.0

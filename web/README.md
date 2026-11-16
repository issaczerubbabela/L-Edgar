# L.Edgar on the web

A browser version of L.Edgar for a PC or iPad. It talks to the same Apps Script web app the phone
syncs to (`scripts/AppsScript.gs`), so the Google Sheet stays the one place both sides agree on.
There is no server of its own: it builds to static files.

## What it does

| Screen | What you get |
|---|---|
| **Ledger** | A month of transactions day by day, on a calendar, or as a year of months, with Income, Expenses and Net. Search box, type chips, and category / account / amount filters. |
| **Stats** | Earned, Spent, Saved and Left over for a week, month, year, salary cycle or custom range. Spending pace against the previous period and the budget, day-by-day bars, categories against their usual, an 8-period trend, a spending calendar, Paid from, and the biggest expenses. Click a bar, category or day to see its transactions. |
| **Budget** | Each salary cycle: Left to spend, per-day pace, and every bucket with what it has spent and the categories behind it, plus Unbucketed. |
| **Accounts** | Assets, Liabilities and Net worth, a 12-month Net worth line, Cash flow, accounts by group with live balances, and a month-by-month statement per account. |
| **Search** | Every transaction, with text search, filters for type, category, account, dates and amount, Bookmarks, and sorting. |
| **Add / edit / delete** | Expenses, Income and Transfers, and bookmarking. Changes go straight to the Sheet; the phone picks them up when the app opens or on Sync now. |

It installs to an iPad home screen (Share → Add to Home Screen) or as a desktop app from Chrome or Edge.

## Run it

```bash
cd web
npm install
npm run mock     # a stand-in script with made-up data, on http://localhost:8787/exec
npm run dev      # the app, on http://localhost:5173
```

Connect to `http://localhost:8787/exec` to try it without touching a real Sheet, or paste your own
web app URL (in the phone app: More → Settings → Database Setup).

`npm test` runs the maths tests. `npm run build` type-checks and writes the site to `web/dist/`.

## Put it online

`web/dist/` is a plain static site with relative paths, so any static host works: GitHub Pages,
Cloudflare Pages, Netlify, or a folder on your own server. Nothing private is in the build. Your
script URL is typed in on each device and kept in that browser only.

## How it stays in step with the phone

- **Transactions** are read and written by Transaction ID with the script's `upsert` and
  `delete_ids` (ADR-0003). Every save carries the row's revision, so if the phone or a hand edit
  changed the row first, the script refuses the save and the web app reloads instead of overwriting.
- **Accounts, categories and buckets** are read-only here and are as of the phone's last backup.
- **Balances, bucket spending and Stats** are worked out in the browser from the transactions, with
  the same rules as the app (`src/math/` ports `AccountMath`, `CycleSummaryBuilder` and
  `StatsReport`), so they include edits made on the web straight away.
- The last read is cached in the browser, so the app opens at once and still shows data offline.

## What the web version can't do

**Not possible in a browser**

- **Auto-capture.** A web page can't read bank and UPI notifications, so there's no review inbox.
- **Repeat rules that add transactions by themselves.** Nothing runs while the tab is closed. The
  transactions the phone adds from them do show up here.
- **Daily reminders.** They need the phone's scheduler and what was logged on the phone.
- **App lock with PIN or fingerprint.** The page is only as locked as the device and browser are.
- **Working fully offline.** You can view the last copy offline, but adding or editing needs a
  connection: there's no background sync to send changes later the way the phone does.

**Not possible without changing the Apps Script and the phone's sync first**

- **Trips, Repeat rules, merchant rules and account aliases.** They live only on the phone and never
  reach the Sheet, so the web can't even show them.
- **Adding or editing accounts, categories, buckets and salary cycles.** The Sheet's copies of these
  are backups the phone overwrites; an edit made here would be lost at its next backup.
- **Reconcile.** It also stamps the account's "last reconciled" date, which only the phone can
  write. Balance adjustments made on the phone are shown and counted.
- **Sync conflicts and the duplicate review screen.** The web refuses a stale save rather than
  offering a merge.

**Other limits to know**

- **Speed.** Each read or save takes 1–3 seconds, because Apps Script is slow and handles one
  request at a time.
- **No sign-in.** The script URL is the only key to your Sheet. Don't connect on a shared computer,
  and use Disconnect in Settings when you're done on a borrowed one.
- **Accounts are matched by name.** The Sheet stores account names, not ids, so a transaction whose
  account was renamed on the phone but not yet synced can sit on no account until the phone syncs.
- **Excel export and CSV import** aren't built. Google Sheets can already download the data.
- **Chart palettes and the other app themes** aren't carried over: there is one light and one dark theme.

## Keeping it working

`src/api.ts` is the browser's side of the script contract. There is no shared schema, so when the
script's replies or the upsert payload change, change `src/api.ts` (and `dev/mock-server.mjs`) in
the same commit. It needs script version 2 or later and never sends data in `records`.

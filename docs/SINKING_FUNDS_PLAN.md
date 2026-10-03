# Sinking Funds Plan

Plan for sinking funds on the Budget tab: putting a little aside every Salary cycle for big,
known costs (an insurance renewal, Diwali, school fees, annual subscriptions), so that when the
bill lands it's paid from the fund and the cycle it lands in stays intact. Status: **proposal**,
nothing built yet. A visual version with phone mockups is published as an artifact:
https://claude.ai/artifact/8HEGj5EfZx9QTfTZPw75io

Related: issue #36 (a target on a savings Account, measured by the Account's balance). This is a
different thing: a Sinking fund is virtual, filled from the cycle plan, and meant to be spent.

## The problem

A Bucket belongs to one cycle and starts again from zero in the next, so a once-a-year
₹18,000 premium lands in a single cycle and wrecks it. Left to spend goes deep red, the bucket
shows 600% spent, and Stats' "Usual" is skewed for three cycles afterwards. The money was always
going to be needed, but L.Edgar has nowhere to put it ahead of time.

## How other apps do it

| App | Model | Take | Leave |
|---|---|---|---|
| YNAB | Targets on a category, "Needed for spending by date", refill-up-to vs set-aside-another, snooze | Suggested amount per period, the bar showing spent vs available, snooze | Status shown by colour alone |
| Actual Budget | Text templates `#template 50 up to 1000` | The "up to" cap: a funded fund stops asking | |
| Goodbudget | Annual/Irregular envelopes, fill = annual ÷ periods or from a due date | "Already saved" as a starting balance | |
| Monarch | Save Up goals: target, date, monthly contribution; On track/Ahead/At risk with how far; timeline chart | Status that comes with an amount, the timeline | Growth rates, splitting an account |
| Copilot | Goal pools spent *through categories*; Active → Ready to spend → Archived | Ready to spend, drawing by category | Account allocations |
| Simplifi | Goals deducted from the Spending Plan: Income − Bills − Planned − Goals = Available | Set-asides come off Left to spend; "earmarked" on Accounts | |
| Monzo Pots | Name, photo, target, date; works out the amount per day/week/month | Strong identity (emoji + colour), plain wording | Photos |
| Revolut Vaults | Goal, deadline, recurring transfers, round-ups | | Round-ups (L.Edgar doesn't move money) |
| Wallet | Manual goals, Active/Paused/Reached | Pause, Reached | Progress typed in by hand (goes stale) |
| Cashew | Transactions linked to goals | A per-transaction override, later | As the default |
| Jupiter Pots | Goal pots ("Goa trip") with auto-save | Indian templates | |

## Model

- **Set-aside**: each cycle plans an amount per fund. It comes off the cycle straight away.
- **Draw**: an Expense in a fund's Category draws from the fund first, up to its balance.
- **Spill**: what the fund can't cover counts as spend in that cycle, shown under the fund's name.
- Fund balance = Already saved + Σ set-asides − Σ draws. Never stored; derived from the ledger
  like Bucket spend, so editing or backdating an Expense updates it automatically.

```
Left to spend = Spendable − Set aside − (Expenses not paid from a fund)
Unallocated   = Spendable − Set aside − Bucket allocations
cycles left   = max(1, ceil(days from this cycle's start to the due date ÷ this cycle's length))
suggested     = round up to ₹10((target − balance before this cycle) ÷ cycles left)
```

Example: target ₹18,000, due 15 Mar 2027, ₹6,000 saved, cycle 1–31 Oct → 6 cycles → ₹2,000 a cycle.

Statuses (a word, never colour alone; the first match wins): Paused, Ready to spend (balance ≥
target, suggestion ₹0), Overdue · ₹X short, Due <date> (in this or the next cycle), On track
(this cycle's set-aside ≥ suggested), ₹X behind, Building (no due date).

Repeats: Once / Every year / Every N months. The current due date is derived as the first
`dueDate + k × repeatMonths` on or after the cycle's start. Whatever is left after the bill
carries into the next round.

Transactions and the Sheet's `_responses` tab don't change. Stats' Spent and Saved don't change.

## Screens

1. **Budget tab**: "Set aside" in the cycle header, and a funds strip above Buckets with the
   most urgent first, capped at 3, plus "See all".
2. **Fund detail**: one bar showing saved (solid) and this cycle's set-aside (striped), balance
   by cycle against a dashed target line, the Categories it pays for, repeat and last paid,
   "Skip this cycle" and "Log payment".
3. **New / edit fund sheet**: templates, name + emoji, target, due date, repeat, the Categories
   it pays for, already saved, and the live suggestion. Warns when a Category leaves a Bucket.
4. **Plan this cycle**: a "Set aside" group before Buckets, with inline amounts and "Use
   suggestions". Fund-owned Categories are shown locked in the picker.
5. **Start a cycle**: a "Set aside for funds" toggle next to "Carry over buckets", calling out
   any fund due in the new cycle.
6. **Add transaction** (Phase 4): picking a fund Category shows "Paid from X" and any spill.

## Data

Database 22 → 23 (23 → 24 if #36 lands first). Local only; backed up.

- `sinking_funds`: id, name, emoji, colorIndex, targetAmount, dueDate?, repeatMonths,
  openingBalance, openingDate, fixedPerCycle?, keptInAccountId?, archivedAt?, sortOrder.
- `fund_categories`: fundId, category (NOCASE, unique: a Category pays from at most one fund).
- `fund_set_asides`: fundId, cycleId, amount, skipped; unique (fundId, cycleId); cascades on both.

`fund/FundMath` (pure, takes `today`): `ledger`, `currentDue`, `cyclesLeft`, `suggested`,
`status`. `CycleSummaryBuilder.build` takes a `funds` snapshot (empty by default) and wires
through `BudgetViewModel`, `PlanBucketsViewModel`, `BucketDetailViewModel`, `StatsReport`,
`BucketPreviewSource` and `InsightsScreen`. Assigning a Category to a fund removes it from the
running cycle's `bucket_categories` in the same Room transaction. Closed cycles are untouched.

Backup: a new Apps Script target `sinking_funds` with three tabs (`_funds`, `_fund_categories`,
`_fund_set_asides`). Set-asides are keyed by cycle start date, so Import can renumber cycles.
`SCRIPT_VERSION` goes 2 → 3, and `REQUIRED_SCRIPT_VERSION` stays 2. `BackupWorker` skips funds
on an older script, and Database Setup asks the user to redeploy. Never send fund data in
`records`. Mirror the script in `AppsScriptSetupScreen.kt` and add a node test.

## Phases

- **P0 (S)**: `CONTEXT.md` terms (Sinking fund, Set-aside, Draw, Spill; update Left to spend),
  and ADR-0009 "Sinking funds are earmarks drawn by Category".
- **P1 (M)**: entities, migration and its test, repository, `FundMath` and the
  `CycleSummaryBuilder` integration, test-first.
- **P2 (L)**: screens 1–5, routes added to `BUDGET_SUB_ROUTES`, accessibility, changelog.
- **P3 (M)**: Backup / Import and the script.
- **P4 (M)**: screen 6 and a local per-Transaction override (`fund_draw_overrides` keyed by
  Transaction ID), a "Kept in" Account with an earmarked line, due-date reminders, a Goals list
  combining funds and #36, and a Stats "Smoothed" view.

## Edge cases

The full list is in the artifact. In short: a bill bigger than the fund spills under the fund's
name; a bill paid early leaves "Paid · ₹X short"; a fund Category used for everyday spend shows
up in the draw list (and the P4 override fixes it); Refunds don't top up funds; deleting a fund
releases its Categories and keeps past set-asides as "Set aside (deleted fund)"; set-asides
bigger than Spendable show the existing over-allocated warning.

## Decisions for the owner

1. Name: "Sinking funds" (recommended), "Funds", "Savings goals" or "Set-asides".
2. Should set-asides reduce Left to spend? Recommended yes, recorded in an ADR.
3. How does an Expense find its fund: by Category (recommended) or a per-Transaction field?
4. One table with #36 or two? Recommended two models now and one Goals list later.
5. Should drawn bills stay in Stats' Spent? Recommended yes, unchanged, with an optional
   Smoothed view later.

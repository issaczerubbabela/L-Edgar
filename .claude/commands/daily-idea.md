---
description: Research how leading expense trackers solve one problem, then file one well-scoped L.Edgar improvement as a GitHub issue. Built to run daily via `/loop 1d /daily-idea`.
argument-hint: "[optional focus area, e.g. 'budgets' or 'ui']"
---

# Daily idea: research one improvement and file it as an issue

You are the product researcher for **L.Edgar (SheetSync)**, an offline-first Android expense tracker
(Kotlin, Jetpack Compose, Room, WorkManager sync to a user-deployed Google Apps Script + Google Sheet).
Each run produces **exactly one** new GitHub issue in `issaczerubbabela/L-Edgar`, backed by real research.

Optional focus for this run: `$ARGUMENTS` (if empty, pick the area yourself; see step 2).

## 1. Learn the app as it is today

Read, don't skim:

- `CLAUDE.md`, `CONTEXT.md` (use its vocabulary in the issue), `docs/ARCHITECTURE.md`, every ADR in `docs/adr/`.
- `app/src/main/java/com/issaczerubbabel/ledgar/ui/screens/ChangelogData.kt`: what shipped recently.
- The screens, ViewModels and entities for the area you pick (`ui/screens`, `viewmodel`, `data/local`).

## 2. Pick an area, rotating across categories

List the open and recently closed issues (GitHub MCP `list_issues` / `search_issues`, or `gh issue list --state all --limit 50`).
Look at the labels of the last few `daily-idea` issues and pick a **different category** from the
last run, rotating through:

- `enhancement`: a new feature or a meaningful extension of one (recurring transactions, receipt capture, splits, search, reports, reminders, CSV/bank import, multi-currency, goals, widgets...).
- `ui`: a UI/UX upgrade of an existing screen (hierarchy, motion, empty states, density, one-handed use, dark theme, accessibility).
- `bug`: a real defect you found by reading the code (edge cases in date/time zones, sync races, rounding, pagination, state loss on rotation/process death). Only file a bug you can point to in code with file:line and a concrete reproduction.
- `performance` / `tech-debt`: only when it has a user-visible payoff.

Skip anything already covered by an open or closed issue, an open PR, or the changelog.

## 3. Deep research on how the best apps do it

Use web search and fetch (WebSearch / WebFetch). For the chosen problem, study at least **four** of:
YNAB, Monarch Money, Copilot Money, Money Manager (Realbyte), Spendee, Wallet by BudgetBakers,
Goodbudget, Actual Budget, Cashew, PocketGuard, Emma, Mint's legacy flows, Google's Material 3
guidance and Android "Now in Android" patterns. Prefer primary sources (their help centres, release
notes, design write-ups) plus user feedback (Reddit r/ynab, r/personalfinance, Play Store reviews)
for what users love or hate about each approach.

Then decide what the **right** design is for *this* app, not the average of the others. Weigh it
against L.Edgar's constraints:

- Offline-first: every write goes to Room first; no network on the critical path (ADR-0001).
- Each user deploys their own Apps Script; anything remote-facing needs `scripts/AppsScript.gs`, the copy
  in `AppsScriptSetupScreen.kt`, and the Retrofit DTOs to change together, and must stay compatible with
  scripts deployed before the change (ADR-0002, ADR-0003).
- Single user, personal finance, salary-cycle bucket budgeting, Material 3 Compose UI.

## 4. Use the repo's domain skills for the design

Open and apply the relevant `SKILL.md` files under `.github/skills/` (and `.agents/skills/` for security)
so the proposal fits the house patterns:

- UI work: `compose-ui`, `android-design-system`, `android-accessibility`, `compose-navigation`, `vico-ui` (charts), `compose-performance-audit`.
- Data/sync work: `android-data-layer`, `android-retrofit`, `android-coroutines`, `kotlin-concurrency-expert`, `android-viewmodel`.
- Testing notes: `android-testing`.

## 5. File the issue

Search once more for duplicates (`search_issues` with 2-3 keywords). Then create **one** issue.
Make sure these labels exist (create any that are missing) and apply them: `daily-idea`, one category
label (`enhancement`, `ui`, `bug`, `performance`, `tech-debt`), and a size label (`size:S`, `size:M`, `size:L`).
Prefer ideas that fit in one PR (S or M); if the idea is L, file the first shippable slice and list the
rest under "Later".

Title: short, user-facing, imperative (e.g. "Let a transaction repeat on a schedule").

Body, in this structure:

```markdown
## Problem
Who hits this, when, and what it costs them today in L.Edgar (cite screens / code with file:line).

## How others do it
| App | Approach | What users say |
|---|---|---|
| ... | ... | ... |
Sources: links.

## Proposal
The recommended design for L.Edgar and why it beats the alternatives here.
UI: the flow step by step (screens, components, states: empty, loading, error, offline).

## Implementation sketch
Room entities / migrations, DAO, repository, ViewModel state, Compose screens, navigation,
sync & backup impact (Apps Script contract, backward compatibility), skills to follow.

## Acceptance criteria
- [ ] testable, specific checks
- [ ] unit tests for ...
- [ ] ChangelogData.kt entry

## Out of scope / Later
```

## 6. Report

Reply with the issue link, its category, and one sentence on why it was chosen. Do not change any repository
files in this command (it is read-only apart from the GitHub issue), so no changelog entry is needed.

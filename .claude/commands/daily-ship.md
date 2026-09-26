---
description: Pick one open L.Edgar GitHub issue, implement it on a fresh branch, verify it, and open a PR. Built to run daily via `/loop 1d /daily-ship`.
argument-hint: "[optional issue number]"
---

# Daily ship: take one issue to a pull request

You are shipping **one** GitHub issue from `issaczerubbabela/L-Edgar` per run as a reviewable pull request.
Optional issue to work on: `$ARGUMENTS` (if empty, choose one as below).

Use the GitHub MCP tools (`mcp__github__*`) when available, otherwise the `gh` CLI.

## 1. Guard rails before starting

- List open PRs. If there are **3 or more** open PRs labelled `daily-ship`, don't start new work:
  instead, pick the oldest of them, merge `main` into it if it conflicts, fix red CI or answer
  review comments, push, report, and stop.
- If an open PR already references the chosen issue (`Closes #n` / `Fixes #n`), choose another issue.

## 2. Choose the issue

Without an argument, pick from open issues that are not assigned, not labelled `blocked`,
`needs-discussion`, `wontfix` or `question`, and have no linked open PR. Prioritise:

1. `bug` over everything else (the higher the user impact, the sooner).
2. Issues labelled `size:S` or `size:M`, or issues small enough to finish in one PR.
3. Older issues first; `daily-idea` issues are fair game.

If the issue is too big for one PR, implement the first shippable slice and say so in the PR.
If the issue is ambiguous in a way that changes the design, comment on the issue with the specific
question, add `needs-discussion`, and pick another one.

Comment on the chosen issue that work has started (and assign it to the repo owner if possible) so the
next run doesn't pick it.

## 3. Understand before changing

Read `CLAUDE.md`, `CONTEXT.md`, `docs/ARCHITECTURE.md` and the ADRs in `docs/adr/` that touch the area,
then the code involved. Open and follow the matching `SKILL.md` files in `.github/skills/`
(e.g. `compose-ui`, `android-design-system`, `android-accessibility` for UI; `android-data-layer`,
`android-retrofit`, `android-coroutines` for data/sync; `android-viewmodel`; `android-testing`).
Respect the non-negotiables in `CLAUDE.md`:

- Writes go to Room first; ViewModels never schedule sync (only `SyncTriggers` / `SyncScheduler` do).
- Never enqueue `SyncWorker` with `REPLACE`; settle rows with `markSyncedIfUnchanged`.
- Room schema changes need a migration (never destructive) and an exported schema update.
- Apps Script changes: update `scripts/AppsScript.gs` **and** the copy in `AppsScriptSetupScreen.kt` together,
  keep old deployed scripts working, never send new data in `records`, and run
  `node --test scripts/tests/bucket-budgets.test.js`.

## 4. Implement

```bash
git fetch origin main
git checkout -B claude/issue-<n>-<short-slug> origin/main
```

Keep the change minimal and focused on the issue. Add or update unit tests under `app/src/test/`
for the logic you touch. Update `app/src/main/java/com/issaczerubbabel/ledgar/ui/screens/ChangelogData.kt`
with a user-facing bullet under `features`, `fixes` or `qol` of the current (top) `ChangelogRelease`
(mandatory; do not create a new release).

## 5. Verify

Run what the environment allows and record the results honestly:

- `./gradlew test` (or the specific test class) and `./gradlew assembleDebug` when an Android SDK is available.
  If it isn't (for example in a cloud container), say so in the PR instead of claiming it passed.
- `node --test scripts/tests/bucket-budgets.test.js` if anything under `scripts/` or the Apps Script copy changed.
- Re-read your own diff adversarially: compile errors, missing imports, migrations, nullability,
  recomposition, time zones, sync races.

## 6. Commit, push, open the PR

Commit with a clear message, push with `git push -u origin <branch>`, and open a PR against `main`:

- Title: user-facing summary of the change.
- Label: `daily-ship` (create it if missing) plus the issue's category label.
- Body: `Closes #<n>`, **What changed**, **Why / design notes** (link the research in the issue),
  **How it was tested** (commands run and outcomes, plus anything not verified), **Screens touched**,
  **Sync / schema impact**, and any follow-ups.

## 7. Report

Reply with the issue picked, the PR link, and what was and wasn't verified. Then watch the PR:
fix red CI and answer review comments on later runs (step 1 handles this).

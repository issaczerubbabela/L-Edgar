# To review: group captures by day, compact rows, Confirm day and Ready filters

Ticket: [#142](https://github.com/issaczerubbabela/L-Edgar/issues/142) · Branch: `claude/issue-142-capture-inbox-redesign` · Part of the [Auto-capture improvements spec](README.md)

## Problem
To review is a flat list of tall cards. Each one carries Original text, Dismiss, Add to trip and Confirm. After a few days away, 30–40 captures mean a lot of scrolling and one tap per row. **Review all** only takes High rows, which only a saved rule can produce.

## Proposal
- **Grouped by day**, newest first. Each day's header shows the date, how many captures are waiting and their total.
- **Compact rows**: the title (the guessed Description, marked as a guess), the amount, the Category and Account as chips, and a band dot (High / Check / Low).
  - Tap a row to expand it in place: type switch, pickers, Description chips, Always use, Original text, Add to trip, Edit…, Dismiss and Confirm.
  - Only one row is expanded at a time.
- **Swipe** right to Confirm and left to Dismiss, with Undo, as today.
- **Filters**: All / Needs a pick / Ready (Ready means it has a Category, or a To-account, and an Account).
- **Confirm day** in each day's header opens the bulk sheet for that day.
- **Bulk sheet** (from Confirm day and Review all): High rows ticked, Check rows that are Ready listed **unticked** with their guesses shown, and Low rows left out. One Undo covers the batch.
- The banner and the Trans tab badge are unchanged.
- TalkBack:
  - a compact row reads as one sentence ("Lunch, ₹80, Food, HDFC, check, 12 November")
  - day headers are headings
  - swipe actions also appear as custom accessibility actions

## Notes
- Blocked by [#141](https://github.com/issaczerubbabela/L-Edgar/issues/141) (guessed Descriptions, Check rows in bulk).
- Branch: `claude/issue-142-capture-inbox-redesign`.
- Use the `compose-ui` and `android-accessibility` skills. Reuse the shared Transaction row from `ui/components` where it fits.

## Acceptance criteria
- [ ] Captures from 3 days show under 3 day headers with correct counts and totals.
- [ ] Tapping a row expands it and collapses any other. Every action available today is reachable from the expanded row.
- [ ] Each filter shows exactly the matching rows. A day with none matching is hidden.
- [ ] Confirm day lists that day's High rows ticked and Ready Check rows unticked. Confirming writes only the ticked rows, and Undo restores them all.
- [ ] TalkBack reads a compact row as one sentence, and the swipe actions are reachable without swiping.
- [ ] Changelog `changed` entry, e.g. "To review: captures grouped by day with Confirm day, compact rows and Ready / Needs a pick filters".

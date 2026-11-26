# Auto-capture: spot a capture I already logged by hand, and link it instead of adding it twice

Ticket: [#125](https://github.com/issaczerubbabela/L-Edgar/issues/125) · Branch: `claude/issue-125-capture-link-logged-transaction` · Part of the [Auto-capture improvements spec](README.md)

## Problem
Auto-capture and manual entry run side by side, but they never look at each other. If I log ₹450 at Swiggy on **Add Transaction** or **Quick Add** while paying (or a **Repeating transaction** adds my rent by itself), the bank's alert a minute later still turns up in **To review** as a brand-new capture. One Confirm, a swipe right, **Review all**, or the **Confirm** button on the High-confidence notification, and the same payment is in the Ledger twice. That doubles the spend in Stats, takes it out of the Bucket's Left to spend twice, and moves the Account balance twice. The only fix today is noticing it later in More > Find duplicate transactions.

In the code:
- `CaptureIngestor.ingest` (`capture/CaptureIngestor.kt:70-97`) only dedupes the alert against other alerts (`dedupeKey`, `:148`). It never checks `expense_records`, and it sets `shouldNotify` for every High-confidence capture with a known Account (`:97`).
- `CapturedTransaction.status` already lists `POSSIBLE_DUPLICATE` (`data/local/entity/CapturedTransaction.kt:54`), and the inbox query and `CaptureRepositoryImpl.confirm` accept it (`data/local/dao/CaptureDao.kt:25`, `data/repository/CaptureRepositoryImpl.kt:47`). Nothing ever sets it.
- `CaptureRepositoryImpl.confirm` (`:45-70`) always inserts a new `ExpenseRecord`. There is no way to say "this alert is that Transaction".
- `CaptureActionHandler.confirm` (`capture/notify/CaptureActionHandler.kt:22`) and the inbox's `isBulkCandidate` (`viewmodel/ReviewInboxViewModel.kt:59`) confirm without looking at the Ledger either.

People who log by hand while paying, which is what the Daily reminder and Quick Add encourage, are the ones who get hit.

## How others do it
| App | Approach | What users say |
|---|---|---|
| YNAB | Auto-matches an imported transaction to a hand-entered or scheduled one with the **same amount, dated within 10 days**. The pair becomes one transaction that keeps the user's date, payee, memo and category and the bank's amount. A match is approved automatically; **Unmatch** in Match details undoes it, and two rows can be matched by hand. | Users like logging on the go and seeing the bank "confirm" it. Complaints are about matches that fail when dates are more than 10 days apart. |
| Actual Budget | Uses the bank's ID first, then a fuzzy match (similar date, same amount, similar payee). On a match it updates the existing transaction's **date** to the bank's instead of adding a row. | GitHub issues [#2562](https://github.com/issaczerubbabela/L-Edgar/issues/2562) and [#2668](https://github.com/issaczerubbabela/L-Edgar/issues/2668): silent auto-merges sometimes join two real, separate same-amount transactions, and turning matching off isn't a fix. |
| Goodbudget | Added a manual **Match** in 2025: pick the manual entry and the synced one and merge them. Before that, the only fix was deleting one copy. | Forum threads about bank sync creating duplicates of hand-entered rows are what led to the feature. |
| Quicken Simplifi | Tells users to **merge** a manual entry with the downloaded one rather than delete one, so the app learns they're the same. | Community threads report merges failing when a reminder or recurring bill is attached. |
| SMS-based trackers (KlutterAI and others) | Mention "duplicate detection" for SMS imports in their release notes and privacy policies, but show nothing the user can see or check. | Reviewers say detection "isn't always perfect" because some UPI alerts carry no merchant name. |

Sources: [YNAB: Approving and matching](https://support.ynab.com/en_us/approving-and-matching-transactions-a-guide-ByYNZaQ1i), [YNAB: File-based import](https://support.ynab.com/en_us/file-based-import-a-guide-Bkj4Sszyo), [YNAB blog: matchmaker](https://www.ynab.com/matchmaker-matchmaker-make-me-a-match), [Actual: Importing](https://actualbudget.org/docs/transactions/importing), [Actual: How reconciliation works](https://medium.com/actualbudget/how-transaction-reconciliation-works-8dc5749bbd21), [actual#2562](https://github.com/actualbudget/actual/issues/2562), [actual#2668](https://github.com/actualbudget/actual/issues/2668), [Goodbudget: Match duplicate transactions](https://goodbudget.com/blog/2025/10/match-duplicate-transactions-on-web-and-iphone/), [Simplifi: resolve duplicates](https://support.simplifi.quicken.com/en/articles/4901071-how-to-resolve-duplicate-transactions), [KlutterAI listing](https://appshunter.io/ios/app/klutterai-budget-tracker/id6754978348).

## Proposal
**Suggest, don't merge.** YNAB-style silent auto-matching would break ADR-0005 (auto-capture never decides for the user), and Actual's false merges show why. Instead, a capture that looks like a Transaction already in the Ledger says so on its card, and the safe choice becomes the easy one: **Same one** links the alert to that Transaction and writes nothing new. The Transaction keeps everything the user typed (like YNAB), so linking changes nothing in Room's ledger, needs no Sync and doesn't touch the Sheet.

**Matching rule** (pure, local, cheap):
- Same amount to the paisa.
- Same direction: a DEBIT capture matches an Expense, a CREDIT capture matches an Income. Transfers and Balance adjustments never match.
- The Transaction's date is within **3 days** of the capture's date. UPI and card alerts arrive within minutes, so YNAB's 10 days would just add false matches. This also covers a spend logged before midnight with the alert dated after it.
- If the capture's Account is known and the Transaction has an Account, they must be the same.
- Leave out Transactions waiting to be deleted (`syncAction = DELETE`) and ones already linked to or confirmed from another capture (`captured_transactions.confirmedExpenseId`). Each Transaction can absorb only one alert.
- With more than one candidate, show the one on the same Account first, then the nearest date, then the most recently added.

**To review flow:**
1. A matched card shows a tonal strip under the amount: **"Already in your Ledger?"** with the matched Transaction drawn as the shared Transaction row (description, Category, Account, date).
2. Two buttons replace Confirm: **Same one** (filled) and **Add anyway** (text). Dismiss stays.
3. Swiping right on a matched card means **Same one**, and the swipe background reads "Link". Swiping left still Dismisses.
4. Every choice shows the usual 5-second **Undo** snackbar through `UndoableActions` ("Linked to Swiggy, 14 Nov"), and nothing is written until it's final.
5. Matched cards are left out of **Review all**, and no **Confirm**/**Not mine** notification is posted for them. They wait quietly in the inbox, so a double can't be added with one tap from the shade.
6. The match is worked out live. A capture that arrived *before* I logged the payment by hand picks up its match as soon as I save the Transaction.
7. TalkBack reads the strip as "Possibly already logged as Swiggy, Food, HDFC, 14 November".

States: everything is local, so there are no loading, offline or error states. A capture with no match looks exactly as it does today.

**Same one** also teaches the app. It runs the same `MerchantRuleLearner.onConfirmed` and Account alias learning as Confirm, using the Transaction's Category and Account, because the user chose them themselves.

## Implementation sketch
- **Pure matcher** `capture/CaptureMatcher.kt`: `fun bestMatch(capture: CapturedTransaction, candidates: List<ExpenseRecord>, linkedIds: Set<Long>, zone: ZoneId): ExpenseRecord?`. Compare amounts as paise (`Long`), not `Double`.
- **DAO**:
  - `ExpenseDao.observeCaptureMatchCandidates(since: String): Flow<List<ExpenseRecord>>`: Expense/Income rows, not DELETE, dated on or after `since` (35 days back covers any waiting capture).
  - `CaptureDao.observeLinkedExpenseIds(): Flow<List<Long>>`.
  - No new columns: a link stores the existing Transaction's id in `confirmedExpenseId` with a new status string `LINKED`, so **no Room migration** (stays at 26). Update the status comment in `CapturedTransaction.kt:54` and drop the unused `POSSIBLE_DUPLICATE`, or reuse it for the matched-but-undecided state; pick one and document it.
- **Repository** `CaptureRepository.link(captureId, expenseId)`: in one `withTransaction`, re-check that the capture is still PENDING and the Transaction is still unlinked and not deleted, mark it `LINKED`, and run rule and alias learning, sharing that code with `confirm`.
- **Ingest/notify**: before returning `shouldNotify = true`, `CaptureIngestor` asks the matcher and returns `false` on a match. `CaptureActionHandler.confirm` refuses a capture that now has a match, in case the Transaction was added after the notification was posted.
- **ViewModel**: `ReviewInboxViewModel.rows` adds the two new flows to its `combine` and computes `CaptureRowUi.match: ExpenseRecord?` on `Dispatchers.Default`. `isBulkCandidate` becomes `... && match == null`. Add `link(row)` beside `confirm(row)` through `UndoableActions`.
- **Compose**: `CaptureCard` / `SwipeableCaptureCard` in `ui/screens/CaptureInboxScreen.kt` get the match strip (reuse the shared Transaction row from `ui/components`), the button swap and the swipe label.
- **Sync & backup**: none. Captures are local-only (ADR-0005), and linking writes no `expense_records` row. No Apps Script, DTO or `SCRIPT_VERSION` change.
- **Docs**: add a "Linking to a logged Transaction" paragraph to `docs/AUTO_CAPTURE.md` §1 and §5, and a **Link** term in `CONTEXT.md` next to Confirm.
- Skills: `android-data-layer` (Room-first, the `withTransaction` re-check), `android-viewmodel` / `android-coroutines` (combine off the main thread), `compose-ui` + `android-accessibility` (the strip, the button swap, merged semantics), `android-testing`.

## Acceptance criteria
- [ ] A capture of ₹450 DEBIT on HDFC shows "Already in your Ledger?" when an unlinked ₹450 Expense on HDFC is dated within 3 days. It doesn't for ₹450.50, a 4-day gap, an Income, a Transfer, a different known Account, or a Transaction waiting to be deleted.
- [ ] **Same one** adds no `expense_records` row, leaves the Transaction unchanged and unsynced-state untouched, and marks the capture `LINKED` with that Transaction's id. Undo within 5 s leaves the capture pending.
- [ ] A Transaction already linked or confirmed from one capture is never offered for a second capture.
- [ ] **Add anyway** behaves exactly like today's Confirm.
- [ ] Matched captures are excluded from **Review all** and never get a Confirm notification. A notification Confirm on a capture that has gained a match does nothing and leaves it in the inbox.
- [ ] Saving a Transaction by hand while a matching capture is waiting shows the strip on that card without reopening the screen.
- [ ] **Same one** teaches the merchant rule and the Account alias the same way Confirm does.
- [ ] TalkBack reads the match strip as one sentence, and the buttons have clear labels.
- [ ] Unit tests for `CaptureMatcher` (amount in paise, the date window across midnight and time zones, direction, Account mismatch, ranking, already-linked exclusion) and for `CaptureRepositoryImpl.link` (no insert, the re-check race, learning), next to `CaptureRepositoryImplTest`.
- [ ] `ChangelogData.kt` `added` entry, e.g. "Auto-capture: a capture you already logged by hand says so, and Same one links it instead of adding it twice", plus a `developer` entry. `CHANGELOG.md` regenerated.

## Out of scope / Later
- Merging two captures of the same payment from different sources, such as the bank's SMS in Google Messages plus the Google Pay notification. `dedupeKey` includes source and sender, so these become two captures. Use the same matcher against pending captures, keyed on `refNumber` where both have one.
- **Unlink** from a Transaction's sheet, and showing "Matched bank alert" with the alert's original text on the Transaction.
- Letting the user pick between several candidates ("2 other matches").
- Offering to correct the Transaction's date or amount to the bank's on link, as Actual does.
- Running the matcher over CSV import and Import from Google Sheets, folding in the Settings duplicate finder.

# Auto-capture

Auto-capture turns bank and UPI alerts into suggested Transactions, so they don't have to be typed in by
hand. Everything runs on the phone: no network calls, no paid services and no cloud model. The only
learning is an on-phone Naive Bayes guesser, no network.

Nothing becomes a Transaction until the user confirms it ([ADR-0005](adr/0005-auto-capture-never-confirms-itself.md)).
Alerts are read from notifications, not from the SMS database ([ADR-0007](adr/0007-auto-capture-reads-notifications-not-sms.md)).

Terms used here (Captured transaction, Confirm, Merchant rule, Account alias, Confidence band, Unparsed
alert) are defined in [CONTEXT.md](../CONTEXT.md).

## 1. What the user sees

1. **Turn it on** in More > Auto-capture: switch capture on, grant notification access (an Android system
   screen), and allow notifications so confirmations can be asked from a notification.
2. **Map categories once.** The app recognises kinds of merchant (food delivery, groceries, fuel and so on)
   but only ever files into the user's own Categories. The same screen matches each kind to one of them. A
   kind left unmapped shows "Needs a category".
3. **Alerts arrive.** Each one from a watched app becomes a pending capture. A banner on the Trans screen
   ("3 captured transactions to review") and a badge on the Trans tab count them.
4. **Review.** To review (`CaptureInbox`, all pure) groups waiting captures **by day**, newest first, by the
   date Confirm will write (the alert's own time, in the phone's zone). Each day's header shows the date
   (Today, Yesterday, "Wed 11 Nov"), how many captures wait and the money out and in, and is a TalkBack
   heading. Each capture is a **compact row**: a band dot (High, Check, Low), the title (italic while it's
   the Suggested description), the Category (or a Transfer's other Account) and Account as pills, a guessed
   Account as "HDFC Savings?", and the amount. TalkBack reads the row as one sentence ("Lunch, ₹80, Food,
   HDFC, check, 12 November") and offers Confirm (or Same one and Add anyway), Dismiss, Edit description and
   Edit in full as custom actions. Tapping a row **expands it in place**, and only one row is expanded at a
   time. A row swiped right while something is missing springs back and expands with the gap highlighted.
   **Filters**: All, **Needs a pick** and **Ready**. Ready means a Category (or a Transfer's other Account),
   a real Account (not a guessed one) and no possible Ledger match; everything else Needs a pick. A day with
   nothing under the filter is hidden, and its header counts only what it shows.
   **Confirm day** in a day's header and **Review all** above the list open the **bulk sheet**: High rows
   start ticked, Check rows that are Ready are listed **unticked** with their guesses, and Low rows, guessed
   Accounts and Ledger matches are left out. Only ticked rows are confirmed, as one decision with one Undo,
   so every row it confirms is still the user's tick (ADR-0005).
   The expanded row shows, per capture: merchant, exact amount, account number, the date (if the alert
   states one), the guessed Category and Account, and why it was guessed. The user picks a Category and
   Account where missing, then **Confirm** or **Dismiss**, with the buttons or by swiping the row right or
   left. "Original text" shows the alert as received. "Always use X for Y" saves a rule after one
   confirmation. Tapping the title renames it before confirming, with Descriptions used before as
   suggestions; the merchant's rule remembers the new name for its next captures.
   An **Expense / Income / Transfer** switch sets the type. **Transfer** swaps the Category pick for the other
   side's Account: a debit's Account is the From-account and the user picks the To-account, a credit's Account
   is the To-account and the user picks the From-account. The bank's direction is never flipped (a wrong one
   is a parser bug). **Edit…** opens Add Transaction filled in from the card (amount, date, type, Accounts,
   Category, Description, Remarks), with "Always use this for <merchant>". Saving there is the capture's
   Confirm, through the same `CaptureConfirmer`, so learning, aliases, pairing and Undo match the card; Back
   leaves the capture waiting as it was.
   **Transfer pairing** ([ADR-0006](adr/0006-transfer-suggestions-require-an-own-account-signal.md)):
   confirming a Transfer also resolves the capture on its other side, in the same Room transaction, when
   exactly one waiting capture matches (`TransferPairMatcher`): opposite direction, the same amount to the
   paisa, within 2 days, and account digits that are an Account alias of the Transfer's other Account. It
   becomes `PAIRED` with the same `confirmedExpenseId`, so the money is counted once. The snackbar says "Transfer
   saved, and the matching CUB Savings credit too", and Undo brings both cards back. Two or more matches pair
   none: they stay waiting, and the snackbar says so.
   **Suggested Transfers** (#143, `TransferSuggestions`, all pure): a waiting debit and credit that could be each
   other's other half by the same rules (`TransferPairMatcher.couldBeOtherHalf`: the same amount to the paisa,
   within 2 days), each the other's only match, show as **one row** when at least one side's digits are an
   Account alias (the capture's Account, which only an alias sets). Amount and date alone never pair them, and
   both sides on the same Account are no Transfer. The row reads "Transfer between your accounts?", From → To,
   with a side whose digits are unknown to pick ("HDFC Savings → To account"), and TalkBack reads it as one
   sentence ("Suggested transfer, ₹5,000 from HDFC Savings to an account still to pick, 14 November").
   **Confirm Transfer** confirms the debit as a Transfer with the credit as its explicit other half
   (`CaptureEdits.pairCaptureId`): one Transaction, the debit `CONFIRMED`, the credit `PAIRED`, and the credit's
   digits saved as the To-account's alias. It goes through `CaptureConfirmer`, so one Undo brings both back.
   **Split** shows them as two captures again and marks both (`CaptureTrace.splitFromTransfer`, kept across
   re-guesses) so they are never suggested together again. A capture that may already be in the Ledger is never
   part of a suggestion, and a suggestion is left out of the bulk sheet and of its day's money out and in.
   **Linking to a logged Transaction** (#125): a capture that looks like a Transaction already in the
   Ledger (typed in on Add Transaction or Quick Add, or added by a Repeat rule) shows **"Already in your
   Ledger?"** with that Transaction drawn as the shared Transaction row. **Same one** (and a right swipe,
   which reads "Link") **links** the capture to it: the capture becomes `LINKED` with that Transaction's id
   in `confirmedExpenseId`, and nothing is written to `expense_records`, so there is nothing to Sync. It
   teaches the Merchant rule and Account alias like a Confirm, from the Transaction's type, Category and
   Account, but never takes its Description as the merchant's rename. **Add anyway** is an ordinary Confirm.
   The match (`LedgerMatcher`) is worked out live, so a capture picks it up the moment the Transaction is
   saved: the same amount to the paisa, a debit against an Expense or a credit against an Income (Transfers
   and Balance adjustments never), dated within 3 days, the same Account when both have one, not waiting to
   be deleted, and not already resolved by another capture. With several, the one on the capture's Account
   wins, then the nearest date, then the newest. Matched cards are left out of **Review all**.
   Every Confirm, Dismiss, Same one and **Review all** shows a snackbar with **Undo** for 5 seconds. Nothing is written
   until then (`UndoableActions`, the same holder Delete uses), so Undo leaves no Transaction, nothing to
   Sync and no rule or alias change. Leaving the screen or the app makes a waiting decision final.
5. **Confirm from a notification.** A capture that is High confidence and has a known Account also posts a
   notification with **Confirm** and **Not mine**, unless it already matches a Transaction in the Ledger. A
   saved Transfer rule counts too, once it names an other Account that isn't the capture's own.
   Everything else waits quietly in the inbox.
6. **Unreadable alerts.** If a bank changes its wording and an alert can't be read, it appears under
   More > Auto-capture > Alerts we couldn't read, with Copy and Delete.

## 2. How it fits together

```mermaid
graph TD
    SRC["Watched app notifications<br/>banks, Google Pay, Paytm, Google Messages"] --> L[BankNotificationListener]
    L --> ING[CaptureIngestor]
    ING --> REG["ParserRegistry<br/>Cub, Hdfc, Axis, UpiApp, Generic"]
    REG -->|"ParsedTxn"| CAT[CategorizationPipeline]
    REG -->|"nothing readable, but looks like a bank alert"| UNP[("unparsed_alerts")]
    CAT --> CAP[("captured_transactions<br/>status = PENDING")]
    CAP --> UI["Inbox, banner and badge"]
    CAP -->|"High confidence and Account known"| NOTI[CaptureNotifier]
    NOTI --> ACT[CaptureActionReceiver]
    UI --> REPO["CaptureRepository.confirm"]
    ACT --> REPO
    REPO -->|"one Room transaction"| EXP[("expense_records<br/>isSynced = false")]
    REPO --> LEARN["merchant_rules, account_aliases"]
    EXP --> SYNC["SyncTriggers, SyncWorker, Sheet (existing)"]
    LEARN --> CAT
```

Confirm is the only way a capture becomes a Transaction. It writes exactly what a manual entry writes, an
unsynced `ExpenseRecord`, so the existing Sync picks it up unchanged (ADR-0001, ADR-0003). Captures, rules,
aliases and unparsed alerts are local only and are not sent to the Sheet.

### Package map (`com.issaczerubbabel.ledgar`)

| Area | Files |
| --- | --- |
| `capture/source` | `BankNotificationListener` (the service), `CaptureApps` (the allowlist and sender rules) |
| `capture` | `CaptureIngestor` (parse, dedupe, account lookup, categorize, store), `CaptureSettingsSource`, `CaptureConfirmer` (Confirm with Undo, shared by To review and Edit) |
| `capture/pair` | `TransferPairMatcher` (a confirmed Transfer's other half, ADR-0006), `LedgerMatcher` + `LedgerMatchFinder` (the logged Transaction a capture repeats) |
| `capture/parse` | `ParserRegistry`, `HdfcParser`, `CubParser`, `AxisParser`, `UpiAppParser`, `GenericBankParser`, `ParsingUtils`, `ParsedTxn`, `TxnTime` |
| `capture/categorize` | `MerchantNormalizer`, `KeywordDictionary`, `CategoryResolver`, `CategorizationPipeline`, `MerchantRuleLearner` |
| `capture/notify` | `CaptureNotifier`, `CaptureActionReceiver`, `CaptureActionHandler`, `CaptureNotificationText` |
| `data/local` | entities `CapturedTransaction`, `MerchantRule`, `AccountAlias`, `UnparsedAlert`; matching DAOs; `CaptureMigration` (v18 to v19, v19 to v20, v26 to v27) |
| `data` | `CaptureRepository(Impl)`, `CapturePreferencesRepository` (on/off flag and category mapping, in DataStore) |
| `viewmodel`, `ui/screens` | `ReviewInboxViewModel` + `CaptureInboxScreen` (cards built by `CaptureRows`, days, filters and the bulk sheet by `CaptureInbox`), `LogViewModel` + `LogScreen` for Edit (`CaptureForm`), `CaptureSettingsViewModel` + `CaptureSettingsScreen`, `UnparsedAlertsViewModel` + `UnparsedAlertsScreen`, `CaptureBadgeViewModel` |
| `di` | `CaptureModule` (keyword list from `assets/merchant_keywords.json`, the pipeline, bindings) |

## 3. Reading the alerts

**Which apps.** `CaptureApps` holds the allowlist: HDFC (`com.hdfcbank.android.now`), Axis
(`com.axis.mobile`), City Union Bank (`com.cub.plus.gui`), Google Pay
(`com.google.android.apps.nbu.paisa.user`), Paytm (`net.one97.paytm`) and Google Messages
(`com.google.android.apps.messaging`). Bank SMS arrive through the Messages notification, so the SMS
permission is never needed. Notifications from any other app are never read. Debug builds also accept the
adb shell (see section 11); release builds never do.

**What is read.** The notification's title and big text. For a bank or UPI app the app names itself as the
sender; for Messages the notification title is the sender (an ID like `AD-HDFCBK-T`).

**When.** Live, through `onNotificationPosted`, and once on `onListenerConnected` for notifications already in
the shade. The second matters because Android can stop the service or access can be off for a while, and
those alerts would otherwise be lost. Repeats are harmless (section 5).

**Arrival time** is the notification's own post time, not "now". Re-reading the shade must give an alert the
same time, or it would be captured a second time.

## 4. Parsing

`ParserRegistry.read` first drops a Not-a-payment alert (section 9), then tries parsers in order and uses
the first that claims the text: City Union Bank, HDFC, Axis, UPI apps, then a generic fallback. Each returns
a `ParsedTxn` (amount, direction, merchant, account digits, reference, channel, and the alert's date if
stated).

Formats covered by tests built from real (redacted) alerts:

| Parser | Formats |
| --- | --- |
| HDFC | UPI send (`Sent Rs.70.00 From HDFC Bank A/C *1234 To Name On 26/09/26 Ref ...`); UPI ATM withdrawal; deposit narration (`INR ... deposited in HDFC Bank A/c XX1234 on 25-SEP-26 for PAYER CREDIT`); `Credit Alert! Rs.X credited to HDFC Bank A/c XX1234 ...`; mandate debit (`UPI Mandate: Sent Rs.X from HDFC Bank A/c 1234 To PAYEE ...`); NEFT (`Amt Deducted! Rs.X from your HDFC Bank A/c XX1234 for NEFT txn ...`); NetBanking (`Payment Successful! Rs. X from A/c **…1234 to PAYEE via HDFC Bank NetBanking`, the payee is the merchant); RD (`RD Booked! Your HDFC Bank RD XX5678 ... for INR X` and `INR X debited from HDFC Bank XX1234 ... Info: RD through MOBILE-…5678`), both debits with the RD's digits as `counterpartyAccountHint`, so an Account alias on the RD can suggest a Transfer |
| City Union Bank | `Your a/c no. XXXXXXXX1234 is credited/debited for Rs.X on 14-09-2026 and debited from/credited to a/c no. ... (UPI Ref no ...)`. Direction is always about "Your a/c"; the other account is only a counterparty hint |
| Axis | UPI merchant debit, recognised by its `UPI/P2M/<ref>/<merchant>` narration because it never says "Axis"; IMPS credit |
| Google Pay | `Paid Rs.500 to NAME.` and `Received ... from NAME.` Built from a description of the notifications, **not** a capture from a real phone |
| Paytm | Same patterns as Google Pay on trust. No sample exists yet |
| VPA debit (Generic, HDFC, Axis) | `Rs.250.00 debited from A/c XX1234 to VPA swiggy.stores@axb on 12-11-26 ...`, also `to UPI ID handle@psp` and a bare `to handle@psp`. `ParsingUtils.vpaOf` takes the handle as the merchant, and `MerchantNormalizer` strips the `@psp` (a phone-number handle becomes `P2P:<number>`). The handle must contain `@`, so the bank's `SMS BLOCK ... to <number>` footer is never the payee, and an email address (`care@bank.net`) is skipped. Built from a synthetic sample, **not** a real alert. CUB is left alone |
| Generic | Any text with an amount and a debit or credit word, e.g. ICICI's UPI debit alert (`ICICI Bank Acct XX123 debited for Rs 240.00 on 28-Sep-26; ZOMATO credited. UPI:... Call ... SMS BLOCK 123 to ...`), where the debit word comes before "credited" for the payee |

Direction for `GenericBankParser`, `HdfcParser` and `AxisParser` is decided by `ParsingUtils.directionOf`: whichever debit or credit word occurs first in the text wins, so an alert naming both (debited for your account, credited for the payee, or the reverse order) resolves correctly either way. `CubParser` keeps its own "Your a/c" rule instead, since CUB's narration always says which account the alert is about regardless of word order.

Rules common to all: OTP messages are rejected; the first amount in the text is the transaction (balances
come later); the dates `26/09/26`, `25-SEP-26`, `14-09-2026` and `19-12-24` are all read, day first.

**Adding a bank.** Add a `TransactionParser` with `canParse` (cheap) and `parse`, list it in
`ParserRegistry.default()` before the generic parser, and add a test file using the real alert text. Keep
the alerts in tests, since they are the only record of what a bank actually sends. Expect gaps: the
VPA-addressed debit above is tested only on a synthetic sample, so swap in a real (redacted) alert when one
turns up under Alerts we couldn't read.

## 5. Keeping one capture per alert

Each capture has a `rawHash` with a unique index; a repeat is dropped by the database.

- Alert **with a reference number**: the hash is of source, sender and the alert text. The reference makes
  each payment's text unique.
- Alert **without one** (Google Pay): the hash also includes the ten-minute window of the post time. The
  same notification read again gets the same key, while two real identical payments more than ten minutes
  apart both land. Two identical payments within ten minutes count as one; nothing in the alert can tell
  them apart.

**Against the Ledger.** The hash only compares alerts with alerts. Before storing a capture,
`CaptureIngestor` also asks `LedgerMatchFinder` whether it repeats a Transaction already logged (the rule
is in section 1). If so it is stored as `POSSIBLE_DUPLICATE` instead of `PENDING` and no notification is
posted. It still waits in To review and counts in the badge like any other. The inbox doesn't rely on that
status: it matches every waiting capture live, so a capture that arrived before its Transaction was typed
in gets the strip too, and the notification's Confirm re-checks and does nothing once a match exists.

**Dating.** A capture is filed under the date its alert states (`TxnTime`), so a late SMS isn't logged as
today. A missing date, an impossible one, or one more than a day in the future falls back to arrival time. A
past date keeps the arrival's time of day. The inbox shows the alert's date when it differs from the
arrival day, and lists what arrived last first.

## 6. Categorizing

The Merchant name is normalised first (`MerchantNormalizer`): a UPI handle loses its domain, digits,
punctuation and company words are dropped, and a phone-number handle becomes `P2P:<number>`. Then:

| Rule | When | Confidence |
| --- | --- | --- |
| R1 | A saved Merchant rule exists for the merchant and the bank's direction: its type and Category, while the Category is still one of the user's, or Transfer and its other Account | 0.98 |
| R2 | History match: the user's past picks for this merchant agree at least 60% of the time | that share, at most 0.89 |
| NB | Naive Bayes: the on-phone guesser's top Category clears 0.6 | its probability, at most 0.89 |
| R7 | A keyword matches and its kind is mapped to a Category the user still has | 0.75 |
| R11 | Anything else: "Needs a category" | none |

Everything runs on the phone with no network (`CategorizationPipeline`, with R2 and Naive Bayes in
`HistoryPredictor`). The learned steps read a fresh snapshot of `expense_records` on every ingest and re-guess
(`CaptureHistorySource`); nothing they learn is stored. They only guess an Expense or Income Category: a
Transfer comes from a rule alone, and past Transfers only feed the Suggested description.

- **R2, history match** (`HistoryMatch`). A past Transaction counts when it was confirmed from a capture of
  the same merchant, or when its Description is the merchant's saved rename or reads as the merchant itself
  (a hand-typed "Swiggy" for `SWIGGY`). The most-picked Category the user still has wins, with "You picked
  Food for this 4 of 5 times".
- **Naive Bayes** (`NaiveBayesCategorizer`). A multinomial model over merchant and Description words, a
  log-scaled amount bucket (doubling: ₹30 and ₹80 differ, ₹80 and ₹90 don't), a three-hour time-of-day slot
  (known only for Transactions confirmed from a capture) and weekday/weekend. It is trained on every Expense
  and Income, hand-typed ones included, one model per type. Merchant words count three times over the
  context features. It answers only with at least 10 rows and two Categories to tell apart, when the capture
  shares a word with them, and when its top probability is 0.6 or more: "Guessed from similar spends". The
  threshold was tuned leave-one-out on a fixture in `NaiveBayesCategorizerTest`, which prints the sweep.
- **Keywords** come from `assets/merchant_keywords.json` (kind to words). Whole words only, the longest
  match wins, so "instamart" beats "swiggy" for `SWIGGY INSTAMART`.
- **The app never invents a Category.** Every step only suggests one of the user's own Categories. A kind
  with no mapping, or mapped to a Category since deleted, leaves the capture uncategorized and records which
  kind to map. A rule naming a Category the user no longer has of its type (deleted in the app, or renamed in
  the Sheet) is passed over: the steps after R1 run as if there were no rule, still using its rename, so the
  capture is at most Check and the why line starts "Saved rule's Category Food & Snacks no longer exists". The
  rule stays and is re-taught by Confirms as usual (two overrides replace a learned rule's outcome); a `USER`
  rule changes only when "Always use" is ticked again.
- **Credits** are Income and get a Category from a rule, R2 or Naive Bayes, never from a keyword.
- **Bands.** High is 0.90 or more, Check is 0.60 to 0.89, Low is less or no Category. Only a rule reaches
  High ([ADR-0010](adr/0010-only-a-saved-rule-reaches-high.md)), so a history, Naive Bayes or keyword guess is
  always Check: it never notifies and never starts ticked in the bulk sheet (a Ready one is listed unticked).

**Suggested description** (`DescriptionGuesser`). Each capture also gets a guessed Description from every
Transaction of its type, by merchant, amount, time of day and weekday. A merchant seen before outweighs
everything: only its own Descriptions are ranked, and amount and time decide between them (₹80 at 1pm is
"Lunch", ₹30 at 4:30pm is "Tea"). With no history for the merchant, a Description is guessed only when
several close Transactions agree. The top guess fills the card's title in italics, marked "Suggested title",
and up to two alternatives show as chips; tapping one replaces the title. The bank's own text and "Payment"
are never guessed. A saved rename wins over the guess until the merchant has had different Descriptions;
from then on the guess is the title and the rename one of its chips. Confirming with a guessed Description
(or a chip) doesn't rename the merchant. It never changes the Confidence band.

**Re-guessing.** After each Confirm, once its Undo is over (`CaptureRepository.confirm`), and after a kind is
mapped in Settings, every waiting capture is guessed again (`CaptureIngestor.reguessPending`). Only the guesses
stored on the capture change: its suggested type and Category (or Transfer and other Account), the Suggested
description and the Account guess. What the user picked or typed on a card, including switching it to a
Transfer, lives in the inbox until Confirm, so it always wins, and a pass with no suggestion keeps the one
already there, unless its Category is no longer one of the user's. The guesses, why text and alternatives are kept in the capture's `traceJson`
(`CaptureTrace`), so no table changed.

**Merchant rules** (`MerchantRuleLearner`), keyed by merchant and **direction** (`DEBIT` or `CREDIT`, as the
bank wrote it), so a debit and a credit from one merchant have separate rules. A rule's outcome is
Expense + Category, Income + Category, or **Transfer + the other Account** (`toAccountId`: the To-account of
a debit, the From-account of a credit). Two outcomes are identical when their type matches and so does the
Category, or for a Transfer the other Account; a different type counts as an override. A Transfer rule
suggests the card already on Transfer with its Account, at High. Room 26 → 27 kept every rule: Expense
rules became `DEBIT` and Income rules `CREDIT`.

| Origin | How it arises | What happens to it |
| --- | --- | --- |
| `CANDIDATE` | First confirmation of a merchant | Tracks consecutive identical outcomes. Never matched |
| `LEARNED` | Three identical outcomes in a row | Deleted after two overrides in a row |
| `USER` | "Always use X for Y" ticked on Confirm | Never changed automatically |

Confirming from a notification or in bulk never saves a `USER` rule.

A rule also keeps a **description**: what the user renamed the merchant to on Confirm. Confirming with the
bank's own text clears it. A capture's title is that description, else the bank's merchant text, else
"Payment" or "Money received"; it is looked up when shown or confirmed (`CaptureRepository.titleFor`), never
copied onto the capture, so a rename retitles captures already waiting. A `LEARNED` rule with a description
that would be deleted after two overrides goes back to `CANDIDATE` instead, so the rename outlives the
Category. Renaming never changes which rule or Category a capture matches.

## 7. Accounts

An **Account alias** maps the account digits in an alert (`1234`) to one of the user's Accounts. There is no
prompt: a capture with an unknown number shows "Map account". Picking an Account for it and confirming saves
the alias, and other waiting captures with that number pick the Account up too. Aliases are by last digits
only; UPI-address aliases are not built.

Aliases are also the own-account signal for **Transfer pairing**: a waiting capture is only taken as a
Transfer's other half when its digits are an alias of the Transfer's other Account. A Transfer between two
Accounts whose digits aren't aliased yet is saved without pairing, and its other half stays waiting.

**Account guess** (`AccountGuesser`). When the digits have no alias but the alert came from a bank's own app
(HDFC, Axis or City Union Bank, not a UPI app or an SMS) and every alias seen on that bank's alerts points to
the same one Account, the card suggests it, shown as "HDFC Savings?". It is only a suggestion: the capture's
Account stays unknown, so it never notifies and isn't ticked in bulk, and the alias is saved only when the
user confirms.

## 8. The notification

`CaptureNotifier` posts for a capture that is High confidence **and** has a known Account, and that doesn't
match a Transaction already in the Ledger, nothing else. A Transfer rule (R1 with Transfer and an other Account)
also needs that other Account to exist and differ from the capture's own.

- The lock screen shows "A captured transaction is waiting"; the amount and merchant appear only once unlocked.
  Unlocked, a Transfer reads "Transfer ₹5,000 to HDFC Credit Card" ("from" for a credit), then the capture's
  title and its own Account.
- **Confirm** is marked as needing authentication (`setAuthenticationRequired`), so Android asks for an unlock
  first. It saves only what the capture
  already suggests, with the capture's title (its rename or Suggested description) as the Description, and
  never with a guessed Account. A Transfer is saved from its Account to the rule's (for a debit) and pairs its
  other half exactly as the inbox's Confirm does. With no Category (or other Account) or Account, or once the
  capture matches a Transaction in the Ledger, it does nothing and the capture stays in the inbox.
- **Not mine** dismisses the capture.
- Tapping the notification opens the app normally, so the app lock still applies before anything is shown.
- It is cancelled when the capture is resolved in the inbox.

## 9. Unparsed and Not-a-payment alerts

**Not-a-payment alerts** show an amount but report no money moving. `NotAPayment` holds the phrases, one
pure tested list, and `ParserRegistry.read` checks them before any parser runs. A match is dropped
completely: no capture and no Unparsed alert. The list covers:

- payment requests (`has requested money`, `requested Rs. ... from you`);
- an amount still to come (`INR 500.00 will be debited`, `will be deducted`, `will be refunded`);
- mandates set up, announced or ended (`Mandate Set`, `Mandate Alert`, `mandate ... has been created` / `revoked`);
- instalments due (`RD Installment Due`);
- declined, failed or expired payments (`TXN DECLINED`, `has failed`, `has expired`);
- refunds only initiated (`refund ... initiated`);
- offers, including Google Pay's reward and loan notifications (`FREE 2GB`, `welcome bonus`,
  `Up to ₹2,500 in welcome rewards`, `EMI starts at`, `Earn flat ₹11 cashback`, `cashback on ... recharge`),
  and gaming offers (`Rs.50 Bonus to be credited`, `Credited Bonus with Rs.500`, `Prize Pool`).

An alert that says money was credited (`has been credited`, `credited to your A/c`, `credited back to`) is
never a Not-a-payment alert (money still to come, `can be credited to`, and `has been Credited Bonus` don't count), so a refund or cashback actually credited still parses, and so does a mandate's
real debit (`UPI Mandate: Sent Rs....`), which no phrase names. A capture already waiting when a phrase is
added stays in To review; only new alerts are dropped. Keep each phrase narrow, since a wrong match loses a payment without a trace. Some phrases are only tested on
samples worded after real alerts; swap in the real one when it turns up.

**Unparsed alerts.** An alert no parser could read is kept only if it comes from a watched app or a bank-style SMS sender ID
(`AB-BANKID` or `AB-BANKID-S`), shows an amount, and is not an OTP. A friend's message, an alert with no amount
and an OTP are never stored, and neither is a Not-a-payment alert. Entries are deleted after 30 days and when
capture is turned off. A promotional SMS from a bank-style sender that shows an amount and matches none of
the Not-a-payment phrases is still kept, since it can't be told apart from a wording change; "Clear all" deals
with those, and a recurring one is worth a phrase in `NotAPayment`.

## 10. Data and privacy

| Table | Holds | Sent to the Sheet |
| --- | --- | --- |
| `captured_transactions` | Waiting and resolved captures, including the alert's text | No |
| `merchant_rules` | Saved and learned rules | No |
| `account_aliases` | Account digits to Account | No |
| `unparsed_alerts` | Unreadable alerts, 30 days | No |

- Only allowlisted apps are read, and a notification's text is never logged.
- Turning capture off clears waiting captures and unparsed alerts. Rules, aliases and the category mapping
  are kept.
- All processing is on the device.

## 11. Testing

- **Unit tests** (`./gradlew test`) cover the parsers against real alert text, the normaliser, keywords,
  categorization, rule learning, dates and the notification text. At the time of writing: 204.
- **Device tests** (`./gradlew connectedDebugAndroidTest`) run the real flow against SQLite and the real
  notification: ingest, confirm, learn, dedupe, dating, unparsed alerts, and the notification's actions and
  privacy. At the time of writing: 54. Gradle's runner uninstalls the debug app afterwards, which also
  deletes its data. To keep the data, install both APKs and run
  `adb shell am instrument -w <package>.debug.test/androidx.test.runner.AndroidJUnitRunner`.
- **Trying it without a payment** (debug builds only): post a notification as the adb shell, for example
  `adb shell "cmd notification post -S bigtext -t 'HDFC' tag 'Sent Rs.70.00 From HDFC Bank A/C *1234 To Shop On 26/09/26 Ref 11111111111'"`.
  For a shell post the title is the sender, so use a bank-style ID such as `AD-HDFCBK-T` to try the
  unparsed-alerts list, or `Google Pay` for that parser. Grant access with
  `adb shell cmd notification allow_listener <package>/com.issaczerubbabel.ledgar.capture.source.BankNotificationListener`.
- **Release check.** Release builds use R8, which once broke capture. Gson's `TypeToken` needs the generic
  signature of its anonymous subclass, R8 strips it, and the notification listener then crashed on start
  with "TypeToken must be created with a type argument". It happens only in release, so debug tests cannot
  see it. `app/proguard-rules.pro` now keeps `TypeToken` and its subclasses. Before releasing, run
  `./gradlew installStaging`, which installs the same shrunk build as a separate app (`.staging`, signed with
  the debug key) beside the real one. Open Auto-capture, switch capture on and confirm it doesn't crash. Never install a capture build over the
  real app without meaning to: it upgrades the real database.

## 12. Known limits and not built

Deliberate omissions and gaps, so nobody assumes otherwise:

- **Google Pay and Paytm are unverified.** Google Pay's layout comes from a description, and Paytm has no
  sample.
- **Sources.** Only notifications. No SMS permission path, no email capture.
- **Categorization.** Rules, history, an on-phone Naive Bayes guesser and keywords, with no network and no
  cloud model. The Naive Bayes counts are rebuilt in memory on each ingest and re-guess rather than stored,
  which is fine for a few thousand Transactions (the snapshot is capped at the latest 5,000); a much larger
  ledger may want a cached model.
- **Transfers.** A Suggested Transfer needs an Account alias on at least one side
  ([ADR-0006](adr/0006-transfer-suggestions-require-an-own-account-signal.md)), so a Transfer between two
  Accounts whose digits aren't aliased yet shows as two captures. Split has no Undo, and only To review
  suggests: the notification offers a Transfer only when a saved rule says so. A Transfer confirmed from the
  notification pairs only a capture whose digits are already the other Account's alias.
- **Ledger edits teach** (`LedgerEditLearner`, wrapped around `ExpenseRepository.update` and the Ledger's batch
  Change category). Changing the type or Category of a Transaction that came from a capture (a Confirm or a
  Link, by `confirmedExpenseId`; a Transfer's PAIRED half doesn't count) records an override, or a confirmation,
  on that merchant's rule, exactly as a Confirm with that outcome would; for a Transfer a new other Account is a
  new outcome. Amount, date, Account and Description edits teach nothing, and a Ledger edit never renames the
  merchant. A Sheet pull that changes a Category doesn't teach. Learning is best effort, each rule updated in its own Room transaction after the edit: it never fails the
  edit, and changing a Transaction back counts as one more decision, not an undo.
- **Linking.** Only Transactions dated in the last 35 days are offered, and only the best match: there is
  no picker for "2 other matches", no Unlink, and a link never corrects the Transaction's date or amount to
  the bank's. Two captures of one payment from different apps (the bank's SMS and Google Pay) are still two
  captures; once one is linked or confirmed, the other no longer matches.
- **No 30-day purge of a capture's raw text** (only unparsed alerts are purged). The database is not
  excluded from Android's automatic backup, which is on for the app.
- **Learned data is local.** Rules, aliases and the mapping aren't backed up to the Sheet, so a reinstall
  starts them again.
- **Late or dismissed alerts.** An alert dismissed from the shade before access was on is gone. Only Google
  Messages is watched for SMS; another messaging app would have to be added to `CaptureApps`.
- **Locked-phone behaviour wasn't exercised on hardware.** Every on-phone check of the notification's buttons
  was done with the phone unlocked. The unlock requirement and the generic lock-screen text come from how the
  notification is built, which a device test inspects, but nobody has watched Android enforce them.
- **Phone makers** that aggressively stop background services can stop the listener. Reconnecting re-reads the shade,
  which recovers what is still there.

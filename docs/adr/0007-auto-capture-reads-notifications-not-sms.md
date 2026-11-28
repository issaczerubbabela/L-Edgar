---
status: accepted
---

# Auto-capture reads notifications, not the SMS database

Bank alerts reach the phone as SMS and as notifications from the bank's own app. Reading SMS directly needs `READ_SMS`/`RECEIVE_SMS`, a sensitive permission that Google Play restricts to default messaging apps, and it would give the app every message on the phone. We chose a notification listener with a fixed allowlist of apps instead (the bank apps, Google Pay, Paytm, and Google Messages, through which bank SMS arrive). The app never sees a notification from any other app, and needs only the one-time "notification access" switch. The cost is that capture depends on that access and on Android keeping the listener alive, only Google Messages is covered for SMS, and an alert dismissed before access was granted is lost. When the listener connects it re-reads what is still in the shade to recover what it can.

## Considered options

- **`READ_SMS` with an SMS receiver.** More complete and independent of any notification, but a restricted permission, broader than needed, and a blocker if the app is ever published.
- **Reading bank emails through the user's Apps Script.** Free and structured, but batch rather than real time, needs the script re-authorised for Gmail, and would pass email text through the script and the Sheet. Rejected in favour of reading Gmail's notification (below).
- **Bank data APIs (Account Aggregator, Plaid and similar).** Structured and accurate, but paid or heavyweight for a personal app.

## Update: email through Gmail's notification

Email is read the same way as SMS, behind its own switch, **Read bank emails** (More > Auto-capture, off by default). Gmail (`com.google.android.gm`) joins the allowlist, but the listener doesn't open its notifications until capture and the switch are on. A notification is then read only when it is from a known sender, by its title (the sender's display name, `HDFC Bank InstaAlerts` now and `Amazon Pay India` once its parser lands, or Gmail's title for a thread of that sender's emails, `HDFC, alerts`), and is for the user's Gmail label, which Gmail puts in its sub text. Gmail's notification never shows the address, so the label is what proves the email came through the user's filter on the sender addresses, and a lookalike display name from anyone else is not read. Any other email is dropped in the listener, never parsed, stored or logged, and the ingestor checks the sender again. The notification carries the subject and the email's text, which holds the amount, Account, payee and reference for HDFC's alerts. Gmail only notifies for the labels the user chooses, and these alerts land in the Updates category, so the setup under the switch asks for a Gmail filter that labels them and for notifications on that label, and shows when a bank email was last read. The layout was read off a phone on 2026-11-28. Turning the switch off ignores Gmail again; what was already captured stays.

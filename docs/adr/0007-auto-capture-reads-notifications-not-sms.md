---
status: accepted
---

# Auto-capture reads notifications, not the SMS database

Bank alerts reach the phone as SMS and as notifications from the bank's own app. Reading SMS directly needs `READ_SMS`/`RECEIVE_SMS`, a sensitive permission that Google Play restricts to default messaging apps, and it would give the app every message on the phone. We chose a notification listener with a fixed allowlist of apps instead (the bank apps, Google Pay, Paytm, and Google Messages, through which bank SMS arrive). The app never sees a notification from any other app, and needs only the one-time "notification access" switch. The cost is that capture depends on that access and on Android keeping the listener alive, only Google Messages is covered for SMS, and an alert dismissed before access was granted is lost. When the listener connects it re-reads what is still in the shade to recover what it can.

## Considered options

- **`READ_SMS` with an SMS receiver.** More complete and independent of any notification, but a restricted permission, broader than needed, and a blocker if the app is ever published.
- **Reading bank emails through the user's Apps Script.** Free and structured, but batch rather than real time, needs the script re-authorised for Gmail, and would pass email text through the script and the Sheet. Rejected in favour of reading Gmail's notification (below).

## Update: email through Gmail's notification

Email is read the same way as SMS: Gmail (`com.google.android.gm`) joins the allowlist, and only Alerts from known bank and Amazon Pay sender addresses are read. The notification carries the sender, subject and a preview of about 200 characters, which holds the sentence with the amount, Account and payee for HDFC's and Amazon Pay's alerts. Gmail only notifies for the labels the user chooses, and these alerts land in the Updates category, so the user sets up a Gmail filter that labels them and turns notifications on for that label.
- **Bank data APIs (Account Aggregator, Plaid and similar).** Structured and accurate, but paid or heavyweight for a personal app.

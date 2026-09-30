---
status: accepted
---

# Auto-capture reads notifications, not the SMS database

Bank alerts reach the phone as SMS and as notifications from the bank's own app. Reading SMS directly needs `READ_SMS`/`RECEIVE_SMS`, a sensitive permission that Google Play restricts to default messaging apps, and it would give the app every message on the phone. We chose a notification listener with a fixed allowlist of apps instead (the bank apps, Google Pay, Paytm, and Google Messages, through which bank SMS arrive). The app never sees a notification from any other app, and needs only the one-time "notification access" switch. The cost is that capture depends on that access and on Android keeping the listener alive, only Google Messages is covered for SMS, and an alert dismissed before access was granted is lost. When the listener connects it re-reads what is still in the shade to recover what it can.

## Considered options

- **`READ_SMS` with an SMS receiver.** More complete and independent of any notification, but a restricted permission, broader than needed, and a blocker if the app is ever published.
- **Reading bank emails through the user's Apps Script.** Free and structured, but batch rather than real time and needs the script re-authorised for Gmail. Kept as a possible later source.
- **Bank data APIs (Account Aggregator, Plaid and similar).** Structured and accurate, but paid or heavyweight for a personal app.

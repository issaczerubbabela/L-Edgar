---
status: accepted
---

# One Captured transaction per payment, across Alert sources

One payment often produces several Alerts. HDFC emails every alert it also sends by SMS (with the same reference number), and from 2027 sends only the email for UPI payments up to ₹500. Money received through Google Pay produces the bank's SMS, which shows at most a masked VPA ("from VPA *06-1@okaxis"), and Google Pay's notification, which names the payer ("<NAME> paid you ₹1.00") but not the Account. An Autopay is reported by both too. Google Pay posts nothing for an ordinary payment the user sends, so City Union Bank's sends, which never name the payee, stay without one. Kept apart, each Alert is a Captured transaction with half the facts, and the user resolves the same payment two or three times. We decided that an Alert from a different Alert source that describes the same payment is merged into the waiting Captured transaction instead of making a new one. Alerts match when they have the same reference number, or, when at most one of them shows one, the same amount to the paisa and the same direction within 10 minutes (the window the Google Pay dedupe already uses). Two different reference numbers never match. Each fact is taken from the source that states it best: the Account only from a bank's Alert, the payee from the UPI app, then the email, then the SMS, and the date from the earliest Alert. A Merchant rule is looked up under the best payee first and then the bank's, so rules saved before a merge keep matching. Two Alerts from the **same** source are never merged, because nothing but a reference number tells two identical payments apart, and the source's own dedupe (the `rawHash`) already handles a repeat of one Alert.

## Considered options

- **Keep one Captured transaction per Alert and spot the repeat once the first is confirmed** (how Linking to the Ledger works). No risk of a wrong merge, but CUB captures would never get a payee, and email would make the inbox two or three times as long.
- **Keep them apart and suggest "Same payment?"**, like a Suggested Transfer. Safe, but it is a tap per payment for the commonest case, and the user still has to resolve the half-empty bank capture.

## Consequences

- Two real payments of the same amount, in the same direction, within 10 minutes, from different sources and without matching reference numbers, merge wrongly. "Not the same payment" splits them back and marks them so they never merge again.
- A Captured transaction can have several Alerts, so its "Original text" shows all of them.
- An Alert that arrives after its Captured transaction was confirmed or linked is attached to it quietly. It never reappears as a new capture and never edits the Transaction (ADR-0005).

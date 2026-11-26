# Auto-capture: show who I paid on a UPI ID debit, not just Payment

Ticket: [#139](https://github.com/issaczerubbabela/L-Edgar/issues/139) · Branch: `claude/issue-139-capture-vpa-merchant` · Part of the [Auto-capture improvements spec](README.md)

## Problem
A debit alert worded "debited … to VPA merchant@bank" comes back with an empty merchant (`docs/AUTO_CAPTURE.md` §4: "a VPA-addressed debit has no sample yet, so its merchant comes back empty"). The capture's title is then "Payment", no Merchant rule can match it, and the predictor has nothing to work with.

## Proposal
- When an alert names a VPA after `to VPA`, `to UPI ID` or `to <handle>@<psp>`, use the handle as `merchantRaw`. `MerchantNormalizer` already strips the domain and turns a phone-number handle into `P2P:<number>`.
- Do this in `ParsingUtils` so HDFC, Axis and the generic parser share it. CUB reads its counterparty differently and is left alone.
- Never take the bank's own "SMS BLOCK … to <number>" footer as the payee ([#96](https://github.com/issaczerubbabela/L-Edgar/pull/96) already guards the generic parser against this).

## Notes
- Builds on PR [#96](https://github.com/issaczerubbabela/L-Edgar/pull/96) (fixes [#91](https://github.com/issaczerubbabela/L-Edgar/issues/91)). Branch from `main` after it merges, as `claude/issue-139-capture-vpa-merchant`.
- There's no real alert sample yet. Write tests from a redacted real alert if one turns up under More > Auto-capture > Alerts we couldn't read; otherwise mark the sample synthetic in the test, as the Google Pay tests do.

## Acceptance criteria
- [ ] `Rs.250.00 debited from A/c XX1234 to VPA swiggy.stores@axb on 12-11-26 Ref 1234` parses as a ₹250 DEBIT with merchant `swiggy.stores@axb`, normalised to `swiggy stores`.
- [ ] A phone-number VPA (`9876543210@ybl`) normalises to `P2P:9876543210`.
- [ ] Existing parser tests are unchanged.
- [ ] `docs/AUTO_CAPTURE.md` §4 lists the format, and the "has no sample yet" gap is removed or reworded.
- [ ] Changelog `fixed` entry, e.g. "Auto-capture: payments to a UPI ID show who you paid instead of Payment".

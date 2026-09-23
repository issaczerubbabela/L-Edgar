# Each user deploys their own Apps Script over their own Sheet

There's no shared server. Everyone who uses the app deploys `scripts/AppsScript.gs` as a web app over a Google Sheet they own, and pastes its URL into the app. That keeps each person's data in their own Sheet, where they can read and hand-edit it.

## Consequences

Users redeploy the script on their own schedule, so at any moment the app may be talking to a script older than itself. Any change to the app–script contract must keep working with already-deployed scripts. If it can't, the app has to notice the older script and tell the user to redeploy. The script also exists as a copy in `AppsScriptSetupScreen.kt`, which is what users paste, so the two copies must change together.

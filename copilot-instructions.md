# AI Agent Instructions

Whenever you successfully complete a feature, fix a bug, or make a Quality of Life (QoL) UI improvement, you MUST:

1. Open `ChangelogData.kt`.
2. Add a bullet point to the appropriate category (features, fixes, or qol) for the current working version (e.g., v0.9.X).
3. If instructed to bump the version, create a new `ChangelogRelease` entry.

Enforcement for this repository:

4. Apply this rule for every prompt run that modifies any repository file (code, UI, sync, schema, or instructions).
5. Update the changelog in the same run before sending the final completion response.
6. Skip only when the prompt is read-only and no repository files were changed.

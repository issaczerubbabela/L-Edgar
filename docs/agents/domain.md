# Domain Docs

This repo uses a **single-context** layout:

- **Main context doc**: `CONTEXT.md` (at the repo root)
  - Architecture overview, key concepts, how the system works
  - Keep it up-to-date when you add major features or change structure
- **Architecture Decision Records**: `docs/adr/`
  - One `.md` file per decision
  - Format: problem, decision, consequences
  - Useful for agent skills to understand tradeoffs and avoid regressions

**Consumer rules:**
- Agent skills read `CONTEXT.md` as ground truth for system design.
- Skills also check `docs/adr/` for decisions that constrain or guide implementation.
- When making breaking changes, update both; new features get an ADR.

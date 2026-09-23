# ADR 003: Salary-Cycle Bucket Budgeting Model

**Date**: 2025-09-24  
**Status**: Accepted

## Problem

Traditional per-month, per-category budgets don't align with users' financial rhythms. A user paid bi-weekly may have a $1000 grocery budget per cycle, not per calendar month. Categories also shift — some cycles might emphasize "Car Repairs," others "Groceries." A rigid monthly model forces awkward period boundaries and doesn't match how users naturally think about spending.

## Decision

Implement **salary-cycle bucket budgeting:**

1. **Cycles** define a time window aligned with paycheck frequency (e.g., bi-weekly). Each cycle has a start date and duration.
2. **Buckets** are named spending groups within a cycle (e.g., "Essential," "Discretionary," "Savings"). Buckets have amounts and optional notes.
3. **Category routing** maps expense categories to buckets. A category lives in exactly one bucket per cycle (`unique(cycleId, category)`).
4. **Budget tracking** aggregates expenses by bucket, shows progress, and supports rollover/carryover logic.

**Schema:**
- `budget_cycles(id, startDate, duration, isActive)`
- `budget_buckets(id, cycleId, bucketName, amount, notes)`
- `bucket_categories(id, cycleId, category, bucketId)` — unique constraint ensures each category belongs to exactly one bucket

## Consequences

**Positive:**
- Aligns budgets with paycheck cycles, not calendar months.
- Flexible bucket structure — users can redefine buckets per cycle without affecting history.
- Single-category-per-bucket rule simplifies aggregation and prevents ambiguity.
- Supports complex rollover scenarios (unused "Groceries" budget carries to next cycle).

**Negative:**
- More complex schema than flat monthly budgets; requires cycle setup UX.
- Existing backup/import from Sheets must evolve; the legacy per-month budget model is now secondary.
- Cycle overlap or gaps must be handled in queries to avoid under/over-budgeting.

**Migration & Legacy:**
- Old `budgets` table (per-month, per-category) is kept for backup/rollback but not primary.
- Import flows convert Sheets-based budgets into the new cycle model if schema is detected.
- Changelog documents the shift in budgeting philosophy.

## References

- `CONTEXT.md` — Core concept
- `docs/ARCHITECTURE.md` — Room model diagram
- `app/src/main/java/com/issaczerubbabel/ledgar/ui/screens/BudgetScreens.kt` — UI implementation

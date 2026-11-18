// What a Salary cycle looks like right now: a port of data/bucket/CycleSummaryBuilder.kt. Spend is
// never stored; it is always worked out from the Transactions.

import { type Day, daysBetween } from "../lib/dates";
import { key } from "../lib/money";
import type { Bucket, Cycle, Txn } from "../types";

export interface CategorySpend {
  category: string;
  amount: number;
}

/** Money moved into one tracked Account this cycle, net of what was moved back out. */
export interface AccountTransfers {
  account: string;
  amount: number;
}

export interface BucketSummary {
  bucket: Bucket;
  /** Categories routed here that have spend this cycle, largest first. */
  categories: CategorySpend[];
  /** Accounts this Bucket tracks, with their net Transfers this cycle; included in spent. */
  accounts: AccountTransfers[];
  spent: number;
  remaining: number;
}

export interface CycleSummary {
  cycle: Cycle;
  isRunning: boolean;
  totalDays: number;
  /** 1-based day of the cycle today falls on; 0 before it starts. */
  dayNumber: number;
  daysLeft: number;
  daysOverdue: number;
  /** 0..1: how far through the cycle today is. */
  elapsed: number;
  totalSpent: number;
  leftToSpend: number;
  allocated: number;
  unallocated: number;
  /** What can be spent per remaining day to make the money last. */
  dailyPace: number;
  buckets: BucketSummary[];
  /** Spend in Categories no Bucket claims. */
  unbucketed: CategorySpend[];
  unbucketedSpent: number;
}

/** The running cycle keeps absorbing spend past its end date until the next one starts. */
export const cycleRange = (cycle: Cycle, todayDay: Day): { start: Day; end: Day } => ({
  start: cycle.start,
  end: cycle.closedAt === null && todayDay > cycle.end ? todayDay : cycle.end,
});

export function cycleSummary(cycle: Cycle, transactions: Txn[], todayDay: Day): CycleSummary {
  const isClosed = cycle.closedAt !== null;
  const spend = new Map<string, CategorySpend>();
  for (const txn of transactions) {
    if (txn.type !== "Expense" || txn.date < cycle.start || (isClosed && txn.date > cycle.end)) continue;
    const name = key(txn.category);
    const existing = spend.get(name);
    if (existing) existing.amount += txn.amount;
    else spend.set(name, { category: txn.category.trim() || "Uncategorised", amount: txn.amount });
  }

  const bucketOf = new Map<string, number>();
  for (const bucket of cycle.buckets) for (const category of bucket.categories) bucketOf.set(key(category), bucket.id);
  const bySize = (a: { amount: number }, b: { amount: number }) => b.amount - a.amount;

  // A Transfer that crosses into a Bucket's tracked Accounts counts as that Bucket's; one back out takes it away.
  const bucketOfAccount = new Map<string, number>();
  for (const bucket of cycle.buckets) for (const account of bucket.accounts ?? []) if (!bucketOfAccount.has(key(account))) bucketOfAccount.set(key(account), bucket.id);
  const moved = new Map<string, number>();
  for (const txn of transactions) {
    if (txn.type !== "Transfer" || txn.date < cycle.start || (isClosed && txn.date > cycle.end)) continue;
    const fromBucket = bucketOfAccount.get(key(txn.from));
    const toBucket = bucketOfAccount.get(key(txn.to));
    if (fromBucket === toBucket) continue;
    if (toBucket !== undefined) moved.set(key(txn.to), (moved.get(key(txn.to)) ?? 0) + txn.amount);
    if (fromBucket !== undefined) moved.set(key(txn.from), (moved.get(key(txn.from)) ?? 0) - txn.amount);
  }

  const buckets = cycle.buckets.map((bucket) => {
    const categories = [...spend].filter(([name]) => bucketOf.get(name) === bucket.id).map(([, s]) => s).sort(bySize);
    const accounts = (bucket.accounts ?? [])
      .filter((account) => bucketOfAccount.get(key(account)) === bucket.id)
      .map((account) => ({ account, amount: moved.get(key(account)) ?? 0 }))
      .sort(bySize);
    const spent = categories.reduce((sum, c) => sum + c.amount, 0) + accounts.reduce((sum, a) => sum + a.amount, 0);
    return { bucket, categories, accounts, spent, remaining: bucket.allocated - spent };
  });
  const unbucketed = [...spend].filter(([name]) => !bucketOf.has(name)).map(([, s]) => s).sort(bySize);

  const totalSpent = [...spend.values()].reduce((sum, c) => sum + c.amount, 0) + [...moved.values()].reduce((sum, a) => sum + a, 0);
  const allocated = cycle.buckets.reduce((sum, b) => sum + b.allocated, 0);
  const leftToSpend = cycle.spendable - totalSpent;
  const totalDays = Math.max(daysBetween(cycle.start, cycle.end) + 1, 1);
  const dayNumber = todayDay < cycle.start ? 0 : Math.min(daysBetween(cycle.start, todayDay) + 1, totalDays);
  const daysLeft = todayDay > cycle.end ? 0 : Math.min(daysBetween(todayDay, cycle.end) + 1, totalDays);
  const daysOverdue = !isClosed && todayDay > cycle.end ? daysBetween(cycle.end, todayDay) : 0;

  return {
    cycle,
    isRunning: !isClosed,
    totalDays,
    dayNumber,
    daysLeft,
    daysOverdue,
    elapsed: Math.min(Math.max(dayNumber / totalDays, 0), 1),
    totalSpent,
    leftToSpend,
    allocated,
    unallocated: cycle.spendable - allocated,
    dailyPace: daysOverdue > 0 || leftToSpend <= 0 ? 0 : leftToSpend / Math.max(daysLeft, 1),
    buckets,
    unbucketed,
    unbucketedSpent: unbucketed.reduce((sum, c) => sum + c.amount, 0),
  };
}

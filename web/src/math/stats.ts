// Everything the Stats screen shows for one period: a port of viewmodel/StatsReport.kt and
// StatsPeriod.kt. Week, Month, Year and custom ranges follow the money rules of ADR-0004 (Earned,
// Spent, Saved, Left over). Cycle view matches the Budget screen instead: every Expense counts.

import { type Day, addDays, addMonths, dayMonth, daysBetween, eachDay, maxDay, minDay, monthEnd, monthLabel, monthOf, monthShort, monthStart, weekStart } from "../lib/dates";
import { key } from "../lib/money";
import type { Account, Cycle, DropdownOption, Txn } from "../types";
import { cycleRange } from "./buckets";

export type PeriodKind = "week" | "month" | "year" | "cycle" | "custom";

export interface Period {
  kind: PeriodKind;
  start: Day;
  end: Day;
  label: string;
  /** Set for a cycle period. */
  cycleId?: number;
}

export const days = (p: Period) => daysBetween(p.start, p.end) + 1;

const sortedCycles = (cycles: Cycle[]) => [...cycles].sort((a, b) => a.start.localeCompare(b.start));

export function cyclePeriod(cycle: Cycle, todayDay: Day): Period {
  const range = cycleRange(cycle, todayDay);
  return { kind: "cycle", ...range, label: `${dayMonth(range.start)} – ${dayMonth(cycle.end)}`, cycleId: cycle.id };
}

export function periodFor(kind: PeriodKind, day: Day, cycles: Cycle[], todayDay: Day): Period {
  switch (kind) {
    case "week": {
      const start = weekStart(day);
      return { kind, start, end: addDays(start, 6), label: `${dayMonth(start)} – ${dayMonth(addDays(start, 6))}` };
    }
    case "year": {
      const year = day.slice(0, 4);
      return { kind, start: `${year}-01-01`, end: `${year}-12-31`, label: year };
    }
    case "cycle": {
      const ordered = sortedCycles(cycles);
      const cycle = [...ordered].reverse().find((c) => c.start <= day) ?? ordered[0];
      if (cycle) return cyclePeriod(cycle, todayDay);
      return periodFor("month", day, cycles, todayDay);
    }
    case "custom":
      return { kind, start: monthStart(monthOf(day)), end: day, label: "Custom" };
    default: {
      const month = monthOf(day);
      return { kind: "month", start: monthStart(month), end: monthEnd(month), label: monthLabel(month) };
    }
  }
}

/** The period [steps] before (negative) or after this one; null where there is none (first or last cycle). */
export function shift(p: Period, steps: number, cycles: Cycle[], todayDay: Day): Period | null {
  switch (p.kind) {
    case "week":
      return periodFor("week", addDays(p.start, 7 * steps), cycles, todayDay);
    case "month":
      return periodFor("month", addMonths(p.start, steps), cycles, todayDay);
    case "year":
      return periodFor("year", addMonths(p.start, 12 * steps), cycles, todayDay);
    case "cycle": {
      const ordered = sortedCycles(cycles);
      const next = ordered[ordered.findIndex((c) => c.id === p.cycleId) + steps];
      return next && p.cycleId !== undefined ? cyclePeriod(next, todayDay) : null;
    }
    case "custom": {
      const length = days(p) * steps;
      return { ...p, start: addDays(p.start, length), end: addDays(p.end, length) };
    }
  }
}

export interface Roles {
  saving: Set<string>;
  refund: Set<string>;
  savingsGroups: Set<string>;
}

export function rolesOf(dropdowns: DropdownOption[]): Roles {
  const named = (type: string, role: string) => new Set(dropdowns.filter((d) => d.type === type && d.role === role).map((d) => key(d.name)));
  return { saving: named("EXPENSE_CATEGORY", "SAVING"), refund: named("INCOME_CATEGORY", "REFUND"), savingsGroups: named("ACCOUNT_GROUP", "SAVINGS") };
}

export interface MoneyTotals {
  earned: number;
  /** Net of Refunds (except on the budget basis). */
  spent: number;
  saved: number;
  refunds: number;
  leftOver: number;
}

/** The money rules. [budgetBasis] is Cycle view: Saving Categories count as spent and Refunds aren't taken off. */
export class Money {
  private groupOf: Map<string, string>;
  constructor(private roles: Roles, accounts: Account[], private budgetBasis: boolean) {
    this.groupOf = new Map(accounts.map((a) => [key(a.name), key(a.group)]));
  }

  isSaving = (t: Txn) => t.type === "Expense" && this.roles.saving.has(key(t.category));
  isSpending = (t: Txn) => t.type === "Expense" && (this.budgetBasis || !this.roles.saving.has(key(t.category)));
  private inSavings = (account: string) => this.roles.savingsGroups.has(this.groupOf.get(key(account)) ?? "\u0000");

  totals(transactions: Txn[]): MoneyTotals {
    let earned = 0;
    let gross = 0;
    let saved = 0;
    let refunds = 0;
    for (const t of transactions) {
      if (t.type === "Expense") {
        if (!this.roles.saving.has(key(t.category))) gross += t.amount;
        else if (this.budgetBasis) {
          gross += t.amount;
          saved += t.amount;
        } else saved += t.amount;
      } else if (t.type === "Income") {
        if (this.roles.refund.has(key(t.category))) refunds += t.amount;
        else earned += t.amount;
      } else if (t.type === "Transfer") {
        const into = this.inSavings(t.to);
        const outOf = this.inSavings(t.from);
        if (into && !outOf) saved += t.amount;
        if (outOf && !into) saved -= t.amount;
      }
    }
    const spent = this.budgetBasis ? gross : gross - refunds;
    return { earned, spent, saved, refunds, leftOver: earned - spent - saved };
  }

  paidFrom(transactions: Txn[]) {
    let card = 0;
    let other = 0;
    let transfers = 0;
    for (const t of transactions) {
      if (t.type === "Transfer") transfers += t.amount;
      else if (this.isSpending(t)) {
        if ((this.groupOf.get(key(t.account)) ?? "").includes("card")) card += t.amount;
        else other += t.amount;
      }
    }
    return { card, cashAndAccounts: other, transfers };
  }
}

export interface TimelinePoint {
  label: string;
  start: Day;
  end: Day;
  earned: number;
  spent: number;
  /** The Salary cycles' daily allowance over this point; null when no cycle covers it. */
  budget: number | null;
}

export interface CategoryRow {
  category: string;
  amount: number;
  /** Average of the three periods before this one; null when it had no spend in them. */
  usual: number | null;
}

export interface TrendBar {
  label: string;
  period: Period;
  earned: number;
  spent: number;
  saved: number;
  isCurrent: boolean;
}

export interface Report {
  period: Period;
  totals: MoneyTotals;
  /** Spent against the previous period at the same point in it. */
  spentChangePercent: number | null;
  timeline: TimelinePoint[];
  /** Running spend up to today, point by point. */
  pace: number[];
  previousPace: number[];
  /** Running budget; empty when no cycle covers the period. */
  budgetPace: (number | null)[];
  /** Where spending is heading by the end of a running period. */
  projection: number | null;
  categories: CategoryRow[];
  trend: TrendBar[];
  paidFrom: { card: number; cashAndAccounts: number; transfers: number };
  biggest: Txn[];
  hasTransactions: boolean;
}

const runningSum = (values: number[]) => {
  let sum = 0;
  return values.map((v) => (sum += v));
};

function timelineSlots(p: Period): { label: string; start: Day; end: Day }[] {
  if (p.kind === "year" || days(p) > 92) {
    const slots = [];
    for (let m = monthOf(p.start); m <= monthOf(p.end); m = monthOf(addMonths(monthStart(m), 1))) {
      slots.push({ label: monthShort(m), start: maxDay(monthStart(m), p.start), end: minDay(monthEnd(m), p.end) });
    }
    return slots;
  }
  return eachDay(p.start, p.end).map((d) => ({ label: String(Number(d.slice(8))), start: d, end: d }));
}

/** Each day's share of the Salary cycle covering it: spendable ÷ the cycle's planned days. */
function dailyAllowance(cycles: Cycle[], todayDay: Day) {
  const spans = cycles
    .map((c) => ({ ...cycleRange(c, todayDay), perDay: c.spendable / Math.max(daysBetween(c.start, c.end) + 1, 1) }))
    .sort((a, b) => b.start.localeCompare(a.start));
  return (start: Day, end: Day): number | null => {
    let total = 0;
    let covered = false;
    for (const day of eachDay(start, end)) {
      const span = spans.find((s) => day >= s.start && day <= s.end);
      if (span) {
        total += span.perDay;
        covered = true;
      }
    }
    return covered ? total : null;
  };
}

export function buildReport(input: { transactions: Txn[]; accounts: Account[]; dropdowns: DropdownOption[]; cycles: Cycle[]; period: Period; today: Day }): Report {
  const { transactions, cycles, period, today: todayDay } = input;
  const money = new Money(rolesOf(input.dropdowns), input.accounts, period.kind === "cycle");
  const within = (start: Day, end: Day) => transactions.filter((t) => t.date >= start && t.date <= end);

  const inPeriod = within(period.start, period.end);
  const totals = money.totals(inPeriod);
  const previous = shift(period, -1, cycles, todayDay);

  // A period still running is compared with the same number of days from the start of the previous one.
  let spentChangePercent: number | null = null;
  const elapsedDays = todayDay >= period.end ? days(period) : daysBetween(period.start, todayDay) + 1;
  if (previous && elapsedDays >= 3) {
    const running = todayDay >= period.start && todayDay < period.end;
    const compareEnd = running ? minDay(addDays(previous.start, elapsedDays - 1), previous.end) : previous.end;
    const before = money.totals(within(previous.start, compareEnd)).spent;
    if (before > 0) spentChangePercent = Math.round(((totals.spent - before) / before) * 100);
  }

  const allowance = dailyAllowance(cycles, todayDay);
  const timelineOf = (p: Period): TimelinePoint[] =>
    timelineSlots(p).map((slot) => {
      const t = money.totals(within(slot.start, slot.end));
      return { ...slot, earned: t.earned, spent: t.spent, budget: allowance(slot.start, slot.end) };
    });
  const timeline = timelineOf(period);
  const previousTimeline = previous ? timelineOf(previous) : [];

  const elapsed = timeline.filter((point) => point.start <= todayDay).length;
  const pace = runningSum(timeline.slice(0, elapsed).map((point) => point.spent));
  const firstCovered = timeline.findIndex((point) => point.budget !== null);
  const budgetPace: (number | null)[] =
    firstCovered < 0 ? [] : [...Array<null>(firstCovered).fill(null), ...runningSum(timeline.slice(firstCovered).map((point) => point.budget ?? 0))];
  let projection: number | null = null;
  const isRunning = todayDay >= period.start && todayDay < period.end;
  if (isRunning && timeline.length === days(period) && elapsed >= 3) {
    // Leave out the biggest day (usually rent) so one bill doesn't set the pace for the rest.
    const perDay = timeline.slice(0, elapsed).map((point) => point.spent);
    const typical = (perDay.reduce((a, b) => a + b, 0) - Math.max(...perDay)) / (perDay.length - 1);
    projection = pace[pace.length - 1] + typical * (timeline.length - elapsed);
  }

  const byCategory = (p: Period) => {
    const sums = new Map<string, number>();
    for (const t of within(p.start, p.end)) {
      if (!money.isSpending(t)) continue;
      const name = t.category.trim() || "Uncategorized";
      sums.set(name, (sums.get(name) ?? 0) + t.amount);
    }
    return sums;
  };
  const earlier: Map<string, number>[] = [];
  for (let p = previous, i = 0; p && i < 3; p = shift(p, -1, cycles, todayDay), i++) earlier.push(byCategory(p));
  const categories = [...byCategory(period)]
    .map(([category, amount]) => {
      const history = earlier.map((sums) => sums.get(category) ?? 0);
      return { category, amount, usual: history.some((v) => v > 0) ? history.reduce((a, b) => a + b, 0) / history.length : null };
    })
    .sort((a, b) => b.amount - a.amount);

  return {
    period,
    totals,
    spentChangePercent,
    timeline,
    pace,
    previousPace: previousTimeline.some((point) => point.spent !== 0) ? runningSum(previousTimeline.map((point) => point.spent)) : [],
    budgetPace,
    projection,
    categories,
    trend: trend(period, cycles, todayDay, (p) => money.totals(within(p.start, p.end))),
    paidFrom: money.paidFrom(inPeriod),
    biggest: inPeriod.filter(money.isSpending).sort((a, b) => b.amount - a.amount).slice(0, 5),
    hasTransactions: inPeriod.length > 0,
  };
}

/** The period on screen and up to seven before it, oldest first; a year shows its own months. */
function trend(period: Period, cycles: Cycle[], todayDay: Day, totalsOf: (p: Period) => MoneyTotals): TrendBar[] {
  let periods: Period[] = [];
  if (period.kind === "custom") return [];
  if (period.kind === "year") {
    for (let m = 0; m < 12; m++) periods.push(periodFor("month", addMonths(period.start, m), cycles, todayDay));
  } else {
    for (let p: Period | null = period, i = 0; p && i < 8; p = shift(p, -1, cycles, todayDay), i++) periods.unshift(p);
  }
  return periods.map((p) => {
    const t = totalsOf(p);
    const label = period.kind === "year" ? monthShort(p.start) : period.kind === "month" ? monthShort(p.start) : dayMonth(p.start);
    return { label, period: p, earned: t.earned, spent: t.spent, saved: t.saved, isCurrent: p.start === period.start && period.kind !== "year" };
  });
}

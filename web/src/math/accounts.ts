// Account balances, totals and statements: a port of account/AccountMath.kt in the Android app,
// with Accounts matched by name (as the Sheet stores them) instead of by Room id. Sums are whole
// paise so they never drift.

import { type Day, monthEnd, monthOf } from "../lib/dates";
import { fromPaise, key, toPaise } from "../lib/money";
import type { Account, DropdownOption, Txn } from "../types";

export type RowKind = "INCOME" | "EXPENSE" | "TRANSFER_IN" | "TRANSFER_OUT" | "ADJUSTMENT";

export interface StatementRow {
  txn: Txn;
  kind: RowKind;
  /** The signed change to this Account. */
  amount: number;
  balanceAfter: number;
  /** False when dated on or before the As-of date, so it is already in the Initial balance. */
  counts: boolean;
}

export interface StatementMonth {
  month: string;
  opening: number;
  moneyIn: number;
  moneyOut: number;
  closing: number;
  /** Newest first. */
  rows: StatementRow[];
}

export interface Totals {
  assets: number;
  liabilities: number;
  netWorth: number;
}

export const liabilityGroups = (dropdowns: DropdownOption[]) =>
  new Set(dropdowns.filter((d) => d.type === "ACCOUNT_GROUP" && d.role === "LIABILITY").map((d) => key(d.name)));

/** How much [txn] changes each Account it touches (by name key), in paise, before the As-of rule. */
export function effects(txn: Txn): Map<string, number> {
  const amount = toPaise(txn.amount);
  const result = new Map<string, number>();
  const add = (name: string, delta: number) => {
    if (key(name)) result.set(key(name), (result.get(key(name)) ?? 0) + delta);
  };
  switch (txn.type) {
    case "Income":
      add(txn.account, amount);
      break;
    case "Expense":
      add(txn.account, -amount);
      break;
    case "Transfer":
      add(txn.from, -amount);
      add(txn.to, amount);
      break;
    case "Adjustment":
      add(txn.account, amount);
      break;
  }
  return result;
}

const counts = (txn: Txn, account: Account) => txn.date > account.asOf;

function balancesPaise(accounts: Account[], transactions: Txn[]): Map<string, number> {
  const byKey = new Map(accounts.map((a) => [key(a.name), a]));
  const totals = new Map(accounts.map((a) => [key(a.name), toPaise(a.initialBalance)]));
  for (const txn of transactions) {
    for (const [name, delta] of effects(txn)) {
      const account = byKey.get(name);
      if (account && counts(txn, account)) totals.set(name, totals.get(name)! + delta);
    }
  }
  return totals;
}

/** Each Account's balance today, by name key: Initial balance plus every Transaction dated after its As-of date. */
export function balances(accounts: Account[], transactions: Txn[]): Map<string, number> {
  return new Map([...balancesPaise(accounts, transactions)].map(([name, paise]) => [name, fromPaise(paise)]));
}

function totalsPaise(accounts: Account[], liabilities: Set<string>, balance: Map<string, number>) {
  let assets = 0;
  let owed = 0;
  for (const account of accounts) {
    if (!account.includedInTotals) continue;
    const value = balance.get(key(account.name)) ?? 0;
    if (liabilities.has(key(account.group))) owed -= value;
    else assets += value;
  }
  return { assets, liabilities: owed, netWorth: assets - owed };
}

/** Assets, Liabilities and Net worth over the Accounts Included in totals. */
export function totals(accounts: Account[], liabilities: Set<string>, transactions: Txn[]): Totals {
  const t = totalsPaise(accounts, liabilities, balancesPaise(accounts, transactions));
  return { assets: fromPaise(t.assets), liabilities: fromPaise(t.liabilities), netWorth: fromPaise(t.netWorth) };
}

/** Net worth at the end of each of [months]. */
export function netWorthHistory(accounts: Account[], liabilities: Set<string>, transactions: Txn[], months: string[]) {
  return months.map((month) => {
    const end: Day = monthEnd(month);
    const upTo = transactions.filter((t) => t.date <= end);
    return { month, netWorth: fromPaise(totalsPaise(accounts, liabilities, balancesPaise(accounts, upTo)).netWorth) };
  });
}

/**
 * Cash flow per month: money into and out of the Accounts Included in totals. A Transfer between
 * two of them nets to nothing; Balance adjustments are kept apart.
 */
export function cashFlow(accounts: Account[], transactions: Txn[], months: string[]) {
  const byKey = new Map(accounts.map((a) => [key(a.name), a]));
  const flows = new Map(months.map((m) => [m, { month: m, moneyIn: 0, moneyOut: 0, adjustments: 0 }]));
  for (const txn of transactions) {
    const flow = flows.get(monthOf(txn.date));
    if (!flow) continue;
    let net = 0;
    for (const [name, delta] of effects(txn)) {
      const account = byKey.get(name);
      if (account && account.includedInTotals && counts(txn, account)) net += delta;
    }
    if (txn.type === "Adjustment") flow.adjustments += net;
    else if (net > 0) flow.moneyIn += net;
    else flow.moneyOut -= net;
  }
  return months.map((m) => {
    const f = flows.get(m)!;
    return { month: m, moneyIn: fromPaise(f.moneyIn), moneyOut: fromPaise(f.moneyOut), adjustments: fromPaise(f.adjustments) };
  });
}

/** [account]'s statement, one section per month with a Transaction on it, newest month and row first. */
export function statement(account: Account, transactions: Txn[]): StatementMonth[] {
  const name = key(account.name);
  let balance = toPaise(account.initialBalance);
  const rows = transactions
    .map((txn) => ({ txn, delta: effects(txn).get(name) }))
    .filter((r): r is { txn: Txn; delta: number } => r.delta !== undefined)
    .sort((a, b) => a.txn.date.localeCompare(b.txn.date) || a.txn.timestamp.localeCompare(b.txn.timestamp))
    .map(({ txn, delta }) => {
      const counted = counts(txn, account);
      if (counted) balance += delta;
      const kind: RowKind =
        txn.type === "Income" ? "INCOME" : txn.type === "Expense" ? "EXPENSE" : txn.type === "Adjustment" ? "ADJUSTMENT" : delta > 0 || (delta === 0 && key(txn.to) === name) ? "TRANSFER_IN" : "TRANSFER_OUT";
      return { txn, kind, delta, balanceAfter: balance, counts: counted };
    });

  const months: StatementMonth[] = [];
  let opening = toPaise(account.initialBalance);
  for (const row of rows) {
    const month = monthOf(row.txn.date);
    let section = months[months.length - 1];
    if (!section || section.month !== month) {
      section = { month, opening: fromPaise(opening), moneyIn: 0, moneyOut: 0, closing: fromPaise(opening), rows: [] };
      months.push(section);
    }
    if (row.counts) {
      if (row.delta > 0) section.moneyIn += row.delta;
      else section.moneyOut -= row.delta;
    }
    section.closing = fromPaise(row.balanceAfter);
    opening = row.balanceAfter;
    section.rows.unshift({ txn: row.txn, kind: row.kind, amount: fromPaise(row.delta), balanceAfter: fromPaise(row.balanceAfter), counts: row.counts });
  }
  for (const section of months) {
    section.moneyIn = fromPaise(section.moneyIn);
    section.moneyOut = fromPaise(section.moneyOut);
  }
  return months.reverse();
}

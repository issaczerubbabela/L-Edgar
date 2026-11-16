// Search and filter over Transactions, shared by the Ledger and Search screens.

import { key } from "../lib/money";
import type { Txn } from "../types";

export interface Filter {
  query: string;
  types: string[];
  categories: string[];
  accounts: string[];
  from: string;
  to: string;
  min: string;
  max: string;
  bookmarkedOnly: boolean;
}

export const emptyFilter: Filter = { query: "", types: [], categories: [], accounts: [], from: "", to: "", min: "", max: "", bookmarkedOnly: false };

export const activeCount = (f: Filter) =>
  [f.query, f.from || f.to, f.min || f.max].filter(Boolean).length + f.types.length + f.categories.length + f.accounts.length + (f.bookmarkedOnly ? 1 : 0);

export function matches(t: Txn, f: Filter): boolean {
  if (f.bookmarkedOnly && !t.bookmarked) return false;
  if (f.types.length && !f.types.includes(t.type)) return false;
  if (f.categories.length && !f.categories.some((c) => key(c) === key(t.category))) return false;
  if (f.accounts.length) {
    const mine = [t.account, t.from, t.to].map(key).filter(Boolean);
    if (!f.accounts.some((a) => mine.includes(key(a)))) return false;
  }
  if (f.from && t.date < f.from) return false;
  if (f.to && t.date > f.to) return false;
  const amount = Math.abs(t.amount);
  if (f.min !== "" && amount < Number(f.min)) return false;
  if (f.max !== "" && amount > Number(f.max)) return false;
  // Every word must appear somewhere: "swiggy 450" finds a ₹450 Swiggy order.
  const words = f.query.toLowerCase().split(/\s+/).filter(Boolean);
  if (words.length) {
    const haystack = [t.description, t.remarks, t.category, t.account, t.from, t.to, t.type, String(t.amount), t.date].join(" ").toLowerCase();
    if (!words.every((word) => haystack.includes(word))) return false;
  }
  return true;
}

/** Newest first; within a day, the row added last comes first. */
export const newestFirst = (a: Txn, b: Txn) => b.date.localeCompare(a.date) || stamp(b) - stamp(a);
const stamp = (t: Txn) => {
  const time = Date.parse(t.timestamp);
  return isNaN(time) ? 0 : time;
};

/** Net as the Ledger shows it: Income minus Expenses for the rows given; Transfers and Balance adjustments left out. */
export function net(transactions: Txn[]) {
  let income = 0;
  let expenses = 0;
  for (const t of transactions) {
    if (t.type === "Income") income += t.amount;
    else if (t.type === "Expense") expenses += t.amount;
  }
  return { income, expenses, net: income - expenses };
}

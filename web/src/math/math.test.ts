import { describe, expect, it } from "vitest";
import { toSheetDto, toTxn } from "../api";
import { parseDay } from "../lib/dates";
import { money, rupees } from "../lib/money";
import type { Account, Cycle, DropdownOption, Txn } from "../types";
import { balances, cashFlow, liabilityGroups, statement, totals } from "./accounts";
import { cycleSummary } from "./buckets";
import { emptyFilter, matches } from "./filter";
import { buildReport, periodFor, shift } from "./stats";

let seq = 0;
const txn = (over: Partial<Txn>): Txn => ({
  id: `t${++seq}`, revision: "r", timestamp: "", date: "2026-03-10", type: "Expense", category: "Food", description: "", amount: 100,
  account: "Bank", from: "", to: "", remarks: "", bookmarked: false, ...over,
});
const account = (over: Partial<Account>): Account => ({
  id: 1, group: "Bank Accounts", name: "Bank", description: "", initialBalance: 1000, asOf: "2026-03-01", hidden: false, includedInTotals: true, order: 0, reconciledAt: "", ...over,
});
const dropdowns: DropdownOption[] = [
  { type: "EXPENSE_CATEGORY", name: "Investments", order: 0, role: "SAVING" },
  { type: "INCOME_CATEGORY", name: "Return", order: 0, role: "REFUND" },
  { type: "ACCOUNT_GROUP", name: "Savings", order: 0, role: "SAVINGS" },
  { type: "ACCOUNT_GROUP", name: "Credit Card", order: 0, role: "LIABILITY" },
];

describe("dates and money", () => {
  it("reads every date form a Sheet cell arrives in", () => {
    expect(parseDay("2026-03-05")).toBe("2026-03-05");
    expect(parseDay("3/5/2026 14:02:11")).toBe("2026-03-05");
    expect(parseDay("Thu Mar 05 2026 00:00:00 GMT+0530 (India Standard Time)")).toBe("2026-03-05");
    expect(parseDay("")).toBe("");
  });
  it("groups rupees the Indian way", () => {
    expect(rupees(120000)).toBe("₹1,20,000");
    expect(rupees(-940)).toBe("-₹940");
    expect(money(247980.5)).toBe("₹2,47,980.50");
    expect(money(340, true)).toBe("+₹340.00");
  });
});

describe("sheet mapping", () => {
  it("reads a legacy transfer and writes it back the way the app does", () => {
    const t = toTxn({ id: "a", revision: "x", date: "2026-03-05", type: "transfer", amount: 500, accountName: "Bank -> Wallet" });
    expect([t.type, t.from, t.to, t.account]).toEqual(["Transfer", "Bank", "Wallet", ""]);
    const dto = toSheetDto(t);
    expect(dto).toMatchObject({ accountName: "Bank -> Wallet", fromAccountName: "Bank", toAccountName: "Wallet", expCategory: "", base: "x" });
  });
  it("puts the category in the column for its type", () => {
    expect(toSheetDto(txn({ type: "Income", category: "Salary" }))).toMatchObject({ incCategory: "Salary", expCategory: "", fromAccountName: null });
  });
});

describe("accounts", () => {
  const accounts = [account({}), account({ id: 2, name: "Card", group: "Credit Card", initialBalance: 0 })];
  const liabilities = liabilityGroups(dropdowns);
  const transactions = [
    txn({ date: "2026-03-01", amount: 999 }), // on the As-of date: already in the Initial balance
    txn({ date: "2026-03-02", amount: 200 }),
    txn({ date: "2026-03-03", type: "Income", amount: 50 }),
    txn({ date: "2026-03-04", account: "card", amount: 300 }),
    txn({ date: "2026-03-05", type: "Transfer", from: "Bank", to: "Card", account: "", amount: 100 }),
    txn({ date: "2026-03-06", type: "Adjustment", amount: -10 }),
  ];
  it("adds Transactions after the As-of date, matching accounts by name ignoring case", () => {
    const b = balances(accounts, transactions);
    expect(b.get("bank")).toBe(740);
    expect(b.get("card")).toBe(-200);
  });
  it("keeps Assets − Liabilities = Net worth", () => {
    expect(totals(accounts, liabilities, transactions)).toEqual({ assets: 740, liabilities: 200, netWorth: 540 });
  });
  it("builds a statement whose months add up", () => {
    const [march] = statement(accounts[0], transactions);
    expect(march.opening + march.moneyIn - march.moneyOut).toBe(march.closing);
    expect(march.closing).toBe(740);
    expect(march.rows[march.rows.length - 1].counts).toBe(false);
  });
  it("nets a Transfer between included Accounts out of cash flow", () => {
    expect(cashFlow(accounts, transactions, ["2026-03"])).toEqual([{ month: "2026-03", moneyIn: 50, moneyOut: 500, adjustments: -10 }]);
  });
});

describe("buckets", () => {
  const cycle: Cycle = {
    id: 1, start: "2026-03-01", end: "2026-03-30", spendable: 3000, closedAt: null,
    buckets: [{ id: 1, name: "Needs", note: "", colorIndex: 0, emoji: "", allocated: 1000, order: 0, categories: ["food"], accounts: [] }],
  };
  it("routes spend into buckets ignoring case and keeps the rest Unbucketed", () => {
    const s = cycleSummary(cycle, [txn({ amount: 400 }), txn({ category: "Fun", amount: 150 }), txn({ type: "Income", amount: 9 }), txn({ date: "2026-02-27" })], "2026-03-10");
    expect(s.buckets[0].spent).toBe(400);
    expect(s.buckets[0].remaining).toBe(600);
    expect(s.unbucketedSpent).toBe(150);
    expect(s.leftToSpend).toBe(2450);
    expect([s.dayNumber, s.daysLeft, s.totalDays]).toEqual([10, 21, 30]);
  });
  it("counts Transfers into a Bucket's tracked Accounts, like the app", () => {
    const saving: Cycle = {
      ...cycle,
      buckets: [{ id: 2, name: "Savings", note: "", colorIndex: 0, emoji: "", allocated: 5000, order: 0, categories: [], accounts: ["Recurring Deposit", "SIP"] }],
    };
    const s = cycleSummary(saving, [
      txn({ type: "Transfer", account: "", from: "Bank", to: "recurring deposit", amount: 3000 }),
      txn({ type: "Transfer", account: "", from: "Recurring Deposit", to: "SIP", amount: 500 }), // within the Bucket
      txn({ type: "Transfer", account: "", from: "SIP", to: "Bank", amount: 200 }),
      txn({ amount: 400 }),
    ], "2026-03-10");
    expect(s.buckets[0].spent).toBe(2800);
    expect(s.buckets[0].accounts).toEqual([{ account: "Recurring Deposit", amount: 3000 }, { account: "SIP", amount: -200 }]);
    expect(s.totalSpent).toBe(3200);
  });
  it("lets a running cycle keep absorbing spend past its end date", () => {
    expect(cycleSummary(cycle, [txn({ date: "2026-04-02" })], "2026-04-03").totalSpent).toBe(100);
    expect(cycleSummary({ ...cycle, closedAt: "2026-03-31" }, [txn({ date: "2026-04-02" })], "2026-04-03").totalSpent).toBe(0);
  });
});

describe("stats", () => {
  const accounts = [account({}), account({ id: 2, name: "FD", group: "Savings" })];
  const transactions = [
    txn({ type: "Income", category: "Salary", amount: 5000 }),
    txn({ type: "Income", category: "Return", amount: 100 }),
    txn({ amount: 1000 }),
    txn({ category: "Investments", amount: 700 }),
    txn({ type: "Transfer", from: "Bank", to: "FD", account: "", amount: 300 }),
    txn({ date: "2026-02-10", amount: 500 }),
  ];
  const report = buildReport({ transactions, accounts, dropdowns, cycles: [], period: periodFor("month", "2026-03-15", [], "2026-03-31"), today: "2026-03-31" });
  it("follows the Earned / Spent / Saved rules", () => {
    expect(report.totals).toEqual({ earned: 5000, spent: 900, saved: 1000, refunds: 100, leftOver: 3100 });
  });
  it("compares with the previous period and lists spending categories with their usual", () => {
    expect(report.spentChangePercent).toBe(80);
    expect(report.categories).toEqual([{ category: "Food", amount: 1000, usual: 500 / 3 }]);
    expect(report.trend).toHaveLength(8);
    expect(report.pace[report.pace.length - 1]).toBe(900);
  });
  it("steps weeks from Monday and months by calendar", () => {
    expect(periodFor("week", "2026-03-11", [], "2026-03-11").start).toBe("2026-03-09");
    expect(shift(periodFor("month", "2026-03-31", [], "2026-03-31"), -1, [], "2026-03-31")).toMatchObject({ start: "2026-02-01", end: "2026-02-28" });
  });
});

describe("filter", () => {
  it("matches every word, an account on either side of a transfer, and amount ranges", () => {
    const t = txn({ description: "Swiggy dinner", amount: 450 });
    expect(matches(t, { ...emptyFilter, query: "swiggy 450" })).toBe(true);
    expect(matches(t, { ...emptyFilter, query: "swiggy lunch" })).toBe(false);
    expect(matches(t, { ...emptyFilter, min: "500" })).toBe(false);
    expect(matches(txn({ type: "Transfer", from: "Bank", to: "FD", account: "" }), { ...emptyFilter, accounts: ["fd"] })).toBe(true);
  });
});

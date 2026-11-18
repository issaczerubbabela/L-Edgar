// A stand-in for the deployed Apps Script, for developing without touching a real Sheet.
// It speaks the same contract as scripts/AppsScript.gs (version 2) over made-up data, held in memory.
//
//   npm run mock        then connect the web app to http://localhost:8787/exec

import { createServer } from "node:http";
import { randomUUID } from "node:crypto";

const PORT = Number(process.env.PORT) || 8787;
const SCRIPT_VERSION = 3;
const pad = (n) => String(n).padStart(2, "0");
const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const daysAgo = (n) => {
  const d = new Date();
  d.setHours(12, 0, 0, 0);
  d.setDate(d.getDate() - n);
  return d;
};

// A small repeatable random source, so the sample data is the same on every start.
let seed = 42;
const random = () => ((seed = (seed * 1664525 + 1013904223) % 4294967296) / 4294967296);
const pick = (list) => list[Math.floor(random() * list.length)];

const accounts = [
  { id: 1, groupName: "Bank Accounts", accountName: "HDFC Salary", description: "Salary account", initialBalance: 84000, initialBalanceDate: iso(daysAgo(400)), isHidden: false, includeInTotals: true, displayOrder: 0, reconciledAt: iso(daysAgo(12)) },
  { id: 2, groupName: "Cash", accountName: "Wallet", description: "", initialBalance: 3500, initialBalanceDate: iso(daysAgo(400)), isHidden: false, includeInTotals: true, displayOrder: 1, reconciledAt: "" },
  { id: 3, groupName: "Credit Card", accountName: "Axis Card", description: "Statement on the 5th", initialBalance: -12500, initialBalanceDate: iso(daysAgo(400)), isHidden: false, includeInTotals: true, displayOrder: 2, reconciledAt: "" },
  { id: 4, groupName: "Savings", accountName: "Fixed Deposit", description: "", initialBalance: 250000, initialBalanceDate: iso(daysAgo(400)), isHidden: false, includeInTotals: true, displayOrder: 3, reconciledAt: "" },
  { id: 5, groupName: "Bank Accounts", accountName: "Old SBI", description: "Closed", initialBalance: 0, initialBalanceDate: iso(daysAgo(400)), isHidden: true, includeInTotals: false, displayOrder: 4, reconciledAt: "" },
];

const option = (id, optionType, name, displayOrder, role = "") => ({ id, optionType, name, displayOrder, role });
const expenseCategories = ["Food", "Groceries", "Transport", "Rent", "Utilities", "Shopping", "Health", "Entertainment", "Investments/Savings"];
const dropdowns = [
  ...expenseCategories.map((name, i) => option(i + 1, "EXPENSE_CATEGORY", name, i, name === "Investments/Savings" ? "SAVING" : "")),
  option(20, "INCOME_CATEGORY", "Salary", 0), option(21, "INCOME_CATEGORY", "Freelance", 1), option(22, "INCOME_CATEGORY", "Return", 2, "REFUND"),
  option(30, "ACCOUNT_GROUP", "Bank Accounts", 0), option(31, "ACCOUNT_GROUP", "Cash", 1),
  option(32, "ACCOUNT_GROUP", "Credit Card", 2, "LIABILITY"), option(33, "ACCOUNT_GROUP", "Savings", 3, "SAVINGS"),
];

const merchants = {
  Food: ["Swiggy", "Zomato", "Cafe Coffee Day", "Lunch at office"],
  Groceries: ["BigBasket", "DMart", "Zepto"],
  Transport: ["Uber", "Metro card", "Petrol"],
  Utilities: ["Electricity bill", "Jio recharge", "Broadband"],
  Shopping: ["Amazon", "Myntra", "Decathlon"],
  Health: ["Apollo Pharmacy", "Gym"],
  Entertainment: ["Netflix", "Movie tickets", "Spotify"],
};
const typical = { Food: 380, Groceries: 1400, Transport: 260, Utilities: 900, Shopping: 2200, Health: 700, Entertainment: 450 };

let transactions = [];
let stamp = 0;
function add(day, type, fields) {
  const at = new Date(day.getTime() + stamp++ * 1000);
  transactions.push({
    id: randomUUID(),
    timestamp: `${at.getMonth() + 1}/${at.getDate()}/${at.getFullYear()} ${pad(at.getHours())}:${pad(at.getMinutes())}:${pad(at.getSeconds())}`,
    date: iso(day), type, expCategory: "", incCategory: "", description: "", amount: 0, accountName: "", fromAccountName: "", toAccountName: "",
    remarks: "", syncedAt: "", isBookmarked: false, ...fields,
  });
}
for (let n = 395; n >= 0; n--) {
  const day = daysAgo(n);
  if (day.getDate() === 1) {
    add(day, "Income", { incCategory: "Salary", description: "Salary", amount: 92000, accountName: "HDFC Salary" });
    add(day, "Expense", { expCategory: "Rent", description: "Rent", amount: 24000, accountName: "HDFC Salary", isBookmarked: true });
    add(day, "Expense", { expCategory: "Investments/Savings", description: "Index fund SIP", amount: 10000, accountName: "HDFC Salary" });
  }
  if (day.getDate() === 3) add(day, "Transfer", { description: "To FD", amount: 5000, accountName: "HDFC Salary -> Fixed Deposit", fromAccountName: "HDFC Salary", toAccountName: "Fixed Deposit" });
  if (day.getDate() === 6) add(day, "Transfer", { description: "Card bill", amount: 14000 + Math.round(random() * 4000), accountName: "HDFC Salary -> Axis Card", fromAccountName: "HDFC Salary", toAccountName: "Axis Card" });
  if (day.getDate() === 10) add(day, "Transfer", { description: "ATM withdrawal", amount: 2000, accountName: "HDFC Salary -> Wallet", fromAccountName: "HDFC Salary", toAccountName: "Wallet" });
  if (day.getDate() === 14 && random() < 0.4) add(day, "Income", { incCategory: "Freelance", description: "Design gig", amount: 12000, accountName: "HDFC Salary" });
  if (day.getDate() === 18 && random() < 0.3) add(day, "Income", { incCategory: "Return", description: "Amazon refund", amount: 1299, accountName: "Axis Card" });
  const count = Math.floor(random() * 4);
  for (let i = 0; i < count; i++) {
    const category = pick(Object.keys(merchants));
    const amount = Math.max(40, Math.round(typical[category] * (0.4 + random() * 1.4)));
    // Small things are paid in cash; the wallet is topped up from the bank below.
    const accountName = amount < 300 && random() < 0.5 ? "Wallet" : pick(["HDFC Salary", "Axis Card", "Axis Card"]);
    add(day, "Expense", { expCategory: category, description: pick(merchants[category]), amount, accountName });
  }
}
add(daysAgo(12), "Adjustment", { description: "Balance adjustment", amount: -240, accountName: "HDFC Salary" });

const firstOfMonth = (back) => {
  const d = daysAgo(0);
  return new Date(d.getFullYear(), d.getMonth() - back, 1, 12);
};
const lastOfMonth = (back) => {
  const d = daysAgo(0);
  return new Date(d.getFullYear(), d.getMonth() - back + 1, 0, 12);
};
const buckets = () => [
  { id: 1, name: "Needs", note: "Rent, bills, food at home", colorIndex: 0, emoji: "🏠", allocatedAmount: 42000, sortOrder: 0, categories: ["Rent", "Utilities", "Groceries", "Transport", "Health"], accounts: [] },
  { id: 2, name: "Wants", note: "", colorIndex: 1, emoji: "🎉", allocatedAmount: 18000, sortOrder: 1, categories: ["Food", "Entertainment"], accounts: [] },
  { id: 3, name: "Future", note: "Pay yourself first", colorIndex: 2, emoji: "🌱", allocatedAmount: 10000, sortOrder: 2, categories: ["Investments/Savings"], accounts: [] },
];
const cycles = [2, 1, 0].map((back, i) => ({
  id: i + 1, startDate: iso(firstOfMonth(back)), endDate: iso(lastOfMonth(back)), spendableAmount: 80000,
  closedAt: back === 0 ? null : iso(firstOfMonth(back - 1)), buckets: buckets(),
}));

// The script's revision is a hash of the row's content cells; any stable hash will do here.
function revision(row) {
  const text = [row.date, row.type, row.expCategory, row.incCategory, row.description, row.amount, row.accountName, row.fromAccountName, row.toAccountName, row.remarks, row.isBookmarked].join("\u001f");
  let h = 0x811c9dc5;
  for (let i = 0; i < text.length; i++) h = Math.imul(h ^ text.charCodeAt(i), 0x01000193) >>> 0;
  return h.toString(16).padStart(8, "0");
}

function handleGet(target) {
  if (target === "accounts") return { status: "ok", data: accounts };
  if (target === "dropdowns") return { status: "ok", data: dropdowns };
  if (target === "bucket_budgets") return { status: "ok", type: "bucket_budgets", data: cycles };
  if (target === "budgets") return { status: "ok", data: [] };
  return { status: "ok", count: transactions.length, idsAssigned: 0, data: transactions.map((row) => ({ ...row, revision: revision(row) })) };
}

function handlePost(payload) {
  if (payload.target !== "transactions") return { status: "error", message: "The mock only takes transaction writes" };
  if (payload.action === "upsert") {
    const stale = [];
    const revisions = {};
    let updated = 0;
    let inserted = 0;
    for (const r of payload.transactions ?? []) {
      if (!r.id) return { status: "error", message: "Every transaction needs an id" };
      const index = transactions.findIndex((row) => row.id === r.id);
      const row = {
        id: r.id, timestamp: r.timestamp || "", date: r.date, type: r.type, expCategory: r.expCategory || "", incCategory: r.incCategory || "",
        description: r.description || "", amount: Number(r.amount) || 0, accountName: r.accountName || "",
        fromAccountName: r.type === "Transfer" ? r.fromAccountName || "" : "", toAccountName: r.type === "Transfer" ? r.toAccountName || "" : "",
        remarks: r.remarks || "", syncedAt: new Date().toISOString(), isBookmarked: r.isBookmarked === true,
      };
      if (index >= 0) {
        if (r.base && revision(transactions[index]) !== r.base) {
          stale.push(r.id);
          continue;
        }
        transactions[index] = { ...row, timestamp: transactions[index].timestamp || row.timestamp };
        updated++;
      } else {
        transactions.push(row);
        inserted++;
      }
      revisions[r.id] = revision(row);
    }
    return { status: "ok", action: "upserted", updated, inserted, stale, revisions };
  }
  if (payload.action === "delete_ids") {
    const ids = new Set(payload.ids ?? []);
    const before = transactions.length;
    transactions = transactions.filter((row) => !ids.has(row.id));
    return { status: "ok", action: "deleted_ids", deleted: before - transactions.length, missing: [] };
  }
  return { status: "error", message: `Unknown action ${payload.action}` };
}

createServer((request, response) => {
  const send = (body) => {
    response.writeHead(200, { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" });
    response.end(JSON.stringify({ ...body, scriptVersion: SCRIPT_VERSION }));
  };
  const url = new URL(request.url, `http://localhost:${PORT}`);
  if (request.method === "GET") return send(handleGet(url.searchParams.get("target") ?? "transactions"));
  if (request.method === "POST") {
    let raw = "";
    request.on("data", (chunk) => (raw += chunk));
    request.on("end", () => {
      try {
        send(handlePost(JSON.parse(raw || "{}")));
      } catch (error) {
        send({ status: "error", message: String(error.message) });
      }
    });
    return;
  }
  response.writeHead(405).end();
}).listen(PORT, () => console.log(`Mock Apps Script on http://localhost:${PORT}/exec`));

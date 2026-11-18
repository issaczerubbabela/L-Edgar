// The browser side of the Apps Script contract (scripts/AppsScript.gs at the repo root). There is
// no shared schema: when the script's replies or the upsert payload change, change this file too.

import { parseDay } from "./lib/dates";
import type { Account, Cycle, DropdownOption, Snapshot, Txn } from "./types";

/** The script version whose upsert / delete_ids / revision behaviour this client depends on. */
export const REQUIRED_SCRIPT_VERSION = 2;

export class ApiError extends Error {}
/** The Sheet row changed after this browser last read it, so the script refused the write. */
export class StaleError extends ApiError {}

export function looksLikeScriptUrl(url: string) {
  return /^https:\/\/script\.google\.com\/macros\/s\/[\w-]+\/exec$/.test(url.trim()) || /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?\//.test(url.trim());
}

async function readJson(response: Response): Promise<any> {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    // A wrong URL, or a deployment that isn't "Anyone", answers with a Google sign-in or error page.
    throw new ApiError("The script didn't answer with data. Check the web app URL, and that it is deployed with access set to Anyone.");
  }
}

async function get(url: string, target: string): Promise<any> {
  let response: Response;
  try {
    response = await fetch(`${url}?target=${target}`, { redirect: "follow" });
  } catch {
    throw new ApiError("Couldn't reach the script. Check your connection and the web app URL.");
  }
  const body = await readJson(response);
  if (body.status !== "ok") throw new ApiError(body.message || `Reading ${target} failed`);
  return body;
}

async function post(url: string, payload: object): Promise<any> {
  let response: Response;
  try {
    // text/plain keeps this a "simple" request: Apps Script can't answer a CORS preflight.
    response = await fetch(url, {
      method: "POST",
      redirect: "follow",
      headers: { "Content-Type": "text/plain;charset=utf-8" },
      body: JSON.stringify(payload),
    });
  } catch {
    throw new ApiError("Couldn't reach the script, so nothing was saved.");
  }
  const body = await readJson(response);
  if (body.status !== "ok") throw new ApiError(body.message || "The script refused the change");
  if (!(Number(body.scriptVersion) >= REQUIRED_SCRIPT_VERSION)) {
    throw new ApiError("The deployed Apps Script is too old to save from the web. Redeploy it from the app's Database Setup screen.");
  }
  return body;
}

const text = (value: unknown) => String(value ?? "").trim();

function canonicalType(raw: unknown): string {
  const t = text(raw);
  const known = ["Expense", "Income", "Transfer", "Adjustment"].find((k) => k.toLowerCase() === t.toLowerCase());
  return known ?? t;
}

/** A Sheet row as the script reports it, in the web app's shape. Exported for tests. */
export function toTxn(row: any): Txn {
  const type = canonicalType(row.type);
  const accountName = text(row.accountName);
  // Rows from before the From/To columns keep a Transfer's accounts as "From -> To".
  const legacy = accountName.split("->").map((part) => part.trim());
  const isTransfer = type === "Transfer";
  const category = type === "Expense" ? text(row.expCategory) : type === "Income" ? text(row.incCategory) : type === "Adjustment" ? "" : text(row.expCategory) || text(row.incCategory);
  return {
    id: text(row.id),
    revision: text(row.revision),
    timestamp: text(row.timestamp),
    date: parseDay(row.date) || parseDay(row.timestamp) || text(row.date),
    type,
    category,
    description: text(row.description),
    amount: Number(row.amount) || 0,
    account: isTransfer ? "" : accountName,
    from: isTransfer ? text(row.fromAccountName) || legacy[0] || "" : "",
    to: isTransfer ? text(row.toAccountName) || legacy[1] || "" : "",
    remarks: text(row.remarks),
    bookmarked: row.isBookmarked === true || text(row.isBookmarked).toLowerCase() === "true",
  };
}

/** What an upsert sends for [txn]: the same fields the Android app's `toSheetDto` sends. */
export function toSheetDto(txn: Txn) {
  const isTransfer = txn.type === "Transfer";
  return {
    id: txn.id,
    timestamp: txn.timestamp || null,
    date: txn.date,
    type: txn.type,
    expCategory: txn.type === "Expense" ? txn.category : "",
    incCategory: txn.type === "Income" ? txn.category : "",
    description: txn.description,
    amount: txn.amount,
    accountName: isTransfer ? [txn.from, txn.to].filter(Boolean).join(" -> ") : txn.account,
    fromAccountName: isTransfer ? txn.from : null,
    toAccountName: isTransfer ? txn.to : null,
    remarks: txn.remarks,
    isBookmarked: txn.bookmarked,
    base: txn.revision || null,
  };
}

const toAccount = (row: any): Account => ({
  id: Number(row.id) || 0,
  group: text(row.groupName),
  name: text(row.accountName),
  description: text(row.description),
  initialBalance: Number(row.initialBalance) || 0,
  asOf: parseDay(row.initialBalanceDate) || "1970-01-01",
  hidden: row.isHidden === true,
  includedInTotals: row.includeInTotals !== false,
  order: Number(row.displayOrder) || 0,
  reconciledAt: parseDay(row.reconciledAt),
});

const toDropdown = (row: any): DropdownOption => ({
  type: text(row.optionType),
  name: text(row.name),
  order: Number(row.displayOrder) || 0,
  role: text(row.role).toUpperCase(),
});

const toCycle = (row: any): Cycle => ({
  id: Number(row.id) || 0,
  start: parseDay(row.startDate),
  end: parseDay(row.endDate),
  spendable: Number(row.spendableAmount) || 0,
  closedAt: parseDay(row.closedAt) || null,
  buckets: (row.buckets ?? [])
    .map((b: any) => ({
      id: Number(b.id) || 0,
      name: text(b.name),
      note: text(b.note),
      colorIndex: Number(b.colorIndex) || 0,
      emoji: text(b.emoji),
      allocated: Number(b.allocatedAmount) || 0,
      order: Number(b.sortOrder) || 0,
      categories: (b.categories ?? []).map(text).filter(Boolean),
      accounts: (b.accounts ?? []).map(text).filter(Boolean),
    }))
    .sort((a: any, b: any) => a.order - b.order),
});

/** Reads the whole Sheet. The script takes a lock per request, so these run one after another on its side. */
export async function fetchSnapshot(url: string): Promise<Snapshot> {
  const [transactions, accounts, dropdowns, buckets] = await Promise.all([
    get(url, "transactions"),
    get(url, "accounts"),
    get(url, "dropdowns"),
    get(url, "bucket_budgets"),
  ]);
  return {
    transactions: (transactions.data ?? []).map(toTxn).filter((t: Txn) => t.id),
    accounts: (accounts.data ?? []).map(toAccount).filter((a: Account) => a.name),
    dropdowns: (dropdowns.data ?? []).map(toDropdown).filter((d: DropdownOption) => d.name),
    // A script from before bucket budgets answers this target with the transaction list instead.
    cycles: buckets.type === "bucket_budgets" ? (buckets.data ?? []).map(toCycle).filter((c: Cycle) => c.start && c.end) : [],
    scriptVersion: Number(transactions.scriptVersion) || 0,
    fetchedAt: Date.now(),
  };
}

/** Adds or changes one Transaction by ID and returns its new revision. Safe to repeat. */
export async function upsertTransaction(url: string, txn: Txn): Promise<string> {
  const body = await post(url, { target: "transactions", action: "upsert", transactions: [toSheetDto(txn)] });
  if ((body.stale ?? []).includes(txn.id)) throw new StaleError("This transaction was changed somewhere else first.");
  return text(body.revisions?.[txn.id]);
}

/** Deletes Transactions by ID. An ID that is already gone counts as deleted. */
export async function deleteTransactions(url: string, ids: string[]): Promise<void> {
  await post(url, { target: "transactions", action: "delete_ids", ids });
}

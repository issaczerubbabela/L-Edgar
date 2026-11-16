/** The four values a Transaction's type takes (see CONTEXT.md at the repo root). */
export type TxnType = "Expense" | "Income" | "Transfer" | "Adjustment";

/** One Transaction, as the web app holds it. Accounts are referred to by name, as in the Sheet. */
export interface Txn {
  id: string;
  /** The Sheet row's revision when last read; sent back as `base` so a stale edit is refused. */
  revision: string;
  timestamp: string;
  /** yyyy-MM-dd, or the raw cell text when it can't be read as a date. */
  date: string;
  type: TxnType | string;
  category: string;
  description: string;
  /** Unsigned, except for an Adjustment. */
  amount: number;
  /** The Account of an Expense, Income or Adjustment. */
  account: string;
  /** Transfer only. */
  from: string;
  to: string;
  remarks: string;
  bookmarked: boolean;
}

export interface Account {
  id: number;
  group: string;
  name: string;
  description: string;
  initialBalance: number;
  /** The As-of date, yyyy-MM-dd: Transactions dated on or before it are already in the Initial balance. */
  asOf: string;
  hidden: boolean;
  includedInTotals: boolean;
  order: number;
  reconciledAt: string;
}

export interface DropdownOption {
  type: "EXPENSE_CATEGORY" | "INCOME_CATEGORY" | "ACCOUNT_GROUP" | string;
  name: string;
  order: number;
  /** Stats role: SAVING, REFUND, SAVINGS or LIABILITY; blank for most. */
  role: string;
}

export interface Bucket {
  id: number;
  name: string;
  note: string;
  colorIndex: number;
  emoji: string;
  allocated: number;
  order: number;
  categories: string[];
}

export interface Cycle {
  id: number;
  start: string;
  end: string;
  spendable: number;
  closedAt: string | null;
  buckets: Bucket[];
}

/** Everything read from the Sheet in one refresh. */
export interface Snapshot {
  transactions: Txn[];
  accounts: Account[];
  dropdowns: DropdownOption[];
  cycles: Cycle[];
  scriptVersion: number;
  /** Epoch milliseconds of the read. */
  fetchedAt: number;
}

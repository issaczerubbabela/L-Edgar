import { key } from "./money";
import type { Snapshot } from "../types";

/** The names offered in pickers: the Sheet's lists first, then anything Transactions already use. */
export function options(data: Snapshot) {
  const listed = (type: string) => data.dropdowns.filter((d) => d.type === type).sort((a, b) => a.order - b.order).map((d) => d.name);
  const withUsed = (names: string[], used: string[]) => {
    const seen = new Set(names.map(key));
    const extra = [...new Set(used.map((u) => u.trim()).filter((u) => u && !seen.has(key(u))))].sort();
    return [...names, ...extra];
  };
  const usedCategories = (type: string) => data.transactions.filter((t) => t.type === type).map((t) => t.category);
  const accounts = [...data.accounts].sort((a, b) => a.order - b.order);
  return {
    expenseCategories: withUsed(listed("EXPENSE_CATEGORY"), usedCategories("Expense")),
    incomeCategories: withUsed(listed("INCOME_CATEGORY"), usedCategories("Income")),
    /** Accounts for pickers: Hidden ones left out, as in the app. */
    accounts: accounts.filter((a) => !a.hidden).map((a) => a.name),
    allAccounts: withUsed(accounts.map((a) => a.name), data.transactions.flatMap((t) => [t.account, t.from, t.to])),
  };
}

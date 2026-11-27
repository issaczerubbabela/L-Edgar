import { useState } from "react";
import { options } from "../lib/options";
import { activeCount, emptyFilter, type Filter } from "../math/filter";
import { useStore } from "../store";

const TYPES = ["Expense", "Income", "Transfer", "Adjustment"];
const TYPE_LABEL: Record<string, string> = { Adjustment: "Balance adjustment" };

function Picker({ label, all, chosen, onChange }: { label: string; all: string[]; chosen: string[]; onChange: (next: string[]) => void }) {
  return (
    <label>
      {label}
      <select value="" onChange={(e) => e.target.value && onChange([...chosen, e.target.value])}>
        <option value="">{chosen.length ? `${chosen.length} chosen: add another…` : "Any"}</option>
        {all.filter((name) => !chosen.includes(name)).map((name) => <option key={name}>{name}</option>)}
      </select>
    </label>
  );
}

/** The search box and filter panel. [dates] is off on the Ledger, where the month on screen is the range. */
export function FilterBar({ filter, onChange, dates = true }: { filter: Filter; onChange: (filter: Filter) => void; dates?: boolean }) {
  const { data } = useStore();
  const [open, setOpen] = useState(false);
  if (!data) return null;
  const lists = options(data);
  const set = (patch: Partial<Filter>) => onChange({ ...filter, ...patch });
  const toggleType = (type: string) => set({ types: filter.types.includes(type) ? filter.types.filter((t) => t !== type) : [...filter.types, type] });
  const chips: { label: string; clear: () => void }[] = [
    ...filter.categories.map((c) => ({ label: c, clear: () => set({ categories: filter.categories.filter((x) => x !== c) }) })),
    ...filter.accounts.map((a) => ({ label: a, clear: () => set({ accounts: filter.accounts.filter((x) => x !== a) }) })),
    ...(dates && (filter.from || filter.to) ? [{ label: `${filter.from || "…"} to ${filter.to || "…"}`, clear: () => set({ from: "", to: "" }) }] : []),
    ...(filter.min || filter.max ? [{ label: `₹${filter.min || "0"} to ₹${filter.max || "…"}`, clear: () => set({ min: "", max: "" }) }] : []),
  ];
  const count = activeCount(dates ? filter : { ...filter, from: "", to: "" });

  return (
    <div className="filterbar">
      <div className="filter-row">
        <input type="search" placeholder="Search description, category, account, amount…" value={filter.query} onChange={(e) => set({ query: e.target.value })} aria-label="Search" />
        <button className={open ? "on" : ""} onClick={() => setOpen(!open)} aria-expanded={open}>Filters{count ? ` (${count})` : ""}</button>
        {count > 0 && <button className="ghost" onClick={() => onChange(dates ? emptyFilter : { ...emptyFilter, from: filter.from, to: filter.to })}>Clear</button>}
      </div>
      <div className="chips">
        {TYPES.map((type) => (
          <button key={type} className={filter.types.includes(type) ? "chip on" : "chip"} aria-pressed={filter.types.includes(type)} onClick={() => toggleType(type)}>{TYPE_LABEL[type] ?? type}</button>
        ))}
        <button className={filter.bookmarkedOnly ? "chip on" : "chip"} aria-pressed={filter.bookmarkedOnly} onClick={() => set({ bookmarkedOnly: !filter.bookmarkedOnly })}>★ Bookmarks</button>
        {chips.map((chip) => <button key={chip.label} className="chip on" onClick={chip.clear}>{chip.label} ✕</button>)}
      </div>
      {open && (
        <div className="fields panel">
          <Picker label="Category" all={[...new Set([...lists.expenseCategories, ...lists.incomeCategories])]} chosen={filter.categories} onChange={(categories) => set({ categories })} />
          <Picker label="Account" all={lists.allAccounts} chosen={filter.accounts} onChange={(accounts) => set({ accounts })} />
          {dates && (
            <>
              <label>From<input type="date" value={filter.from} onChange={(e) => set({ from: e.target.value })} /></label>
              <label>To<input type="date" value={filter.to} onChange={(e) => set({ to: e.target.value })} /></label>
            </>
          )}
          <label>Amount from<input type="number" min="0" value={filter.min} onChange={(e) => set({ min: e.target.value })} placeholder="₹ min" /></label>
          <label>Amount to<input type="number" min="0" value={filter.max} onChange={(e) => set({ max: e.target.value })} placeholder="₹ max" /></label>
        </div>
      )}
    </div>
  );
}

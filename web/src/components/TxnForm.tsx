import { useEffect, useMemo, useState } from "react";
import { isDay } from "../lib/dates";
import { options } from "../lib/options";
import { key, money } from "../lib/money";
import { Segmented } from "./Bits";
import { useStore } from "../store";
import type { Txn } from "../types";

/** Add, change or delete one Transaction. Balance adjustments are shown but only the app's Reconcile makes them. */
export function TxnForm() {
  const { editing, closeEditor, save, remove, data, canWrite, error } = useStore();
  const [draft, setDraft] = useState<Txn | null>(editing?.txn ?? null);
  const [amount, setAmount] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [problem, setProblem] = useState("");

  useEffect(() => {
    setDraft(editing?.txn ?? null);
    setAmount(editing && editing.txn.amount ? String(editing.txn.amount) : "");
    setConfirmDelete(false);
    setProblem("");
  }, [editing]);

  useEffect(() => {
    if (!editing) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && closeEditor();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [editing, closeEditor]);

  const lists = useMemo(() => (data ? options(data) : null), [data]);
  const suggestions = useMemo(() => {
    if (!data || !draft) return [];
    const counts = new Map<string, number>();
    for (const t of data.transactions) if (t.type === draft.type && t.description) counts.set(t.description, (counts.get(t.description) ?? 0) + 1);
    return [...counts].sort((a, b) => b[1] - a[1]).slice(0, 200).map(([text]) => text);
  }, [data, draft?.type]);

  if (!editing || !draft || !lists) return null;
  const readOnly = draft.type === "Adjustment" || !canWrite;
  const set = (patch: Partial<Txn>) => setDraft({ ...draft, ...patch });
  const withCurrent = (names: string[], current: string) => (current && !names.some((n) => key(n) === key(current)) ? [current, ...names] : names);
  const categories = draft.type === "Income" ? lists.incomeCategories : lists.expenseCategories;

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    const value = Number(amount);
    if (!isDay(draft.date)) return setProblem("Pick a date.");
    if (!(value > 0)) return setProblem("Enter an amount above zero.");
    if (draft.type === "Transfer") {
      if (!draft.from || !draft.to) return setProblem("Pick both accounts.");
      if (key(draft.from) === key(draft.to)) return setProblem("Pick two different accounts.");
    } else if (!draft.account) return setProblem("Pick an account.");
    setProblem("");
    setBusy(true);
    const isTransfer = draft.type === "Transfer";
    const saved = await save({
      ...draft,
      amount: value,
      category: isTransfer ? "" : draft.category,
      account: isTransfer ? "" : draft.account,
      from: isTransfer ? draft.from : "",
      to: isTransfer ? draft.to : "",
      description: draft.description.trim(),
      remarks: draft.remarks.trim(),
    });
    setBusy(false);
    if (saved) closeEditor();
  };

  const onDelete = async () => {
    setBusy(true);
    const removed = await remove(draft);
    setBusy(false);
    if (removed) closeEditor();
  };

  const accountSelect = (label: string, value: string, onChange: (name: string) => void) => (
    <label>
      {label}
      <select value={value} onChange={(e) => onChange(e.target.value)} disabled={readOnly}>
        <option value="">Choose…</option>
        {withCurrent(lists.accounts, value).map((name) => <option key={name}>{name}</option>)}
      </select>
    </label>
  );

  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && closeEditor()}>
      <form className="sheet" onSubmit={submit} aria-label={editing.isNew ? "Add transaction" : "Edit transaction"}>
        <header>
          <h2>{editing.isNew ? "Add transaction" : draft.type === "Adjustment" ? "Balance adjustment" : "Edit transaction"}</h2>
          <button type="button" className="ghost" onClick={closeEditor} aria-label="Close">✕</button>
        </header>

        {draft.type === "Adjustment" ? (
          <p className="note">Reconcile in the phone app made this {money(draft.amount, true)} correction to {draft.account}. It can only be changed there.</p>
        ) : (
          <Segmented label="Type" value={draft.type as "Expense" | "Income" | "Transfer"} onChange={(type) => set({ type, category: "" })}
            options={[{ value: "Expense", label: "Expense" }, { value: "Income", label: "Income" }, { value: "Transfer", label: "Transfer" }]} />
        )}

        <div className="fields">
          <label>
            Date
            <input type="date" value={draft.date} onChange={(e) => set({ date: e.target.value })} disabled={readOnly} required />
          </label>
          <label>
            Amount (₹)
            <input type="number" inputMode="decimal" min="0" step="0.01" value={draft.type === "Adjustment" ? String(draft.amount) : amount} onChange={(e) => setAmount(e.target.value)} disabled={readOnly} autoFocus={editing.isNew} placeholder="0" />
          </label>
          {draft.type === "Transfer" ? (
            <>
              {accountSelect("From", draft.from, (from) => set({ from }))}
              {accountSelect("To", draft.to, (to) => set({ to }))}
            </>
          ) : (
            <>
              {draft.type !== "Adjustment" && (
                <label>
                  Category
                  <select value={draft.category} onChange={(e) => set({ category: e.target.value })} disabled={readOnly}>
                    <option value="">None</option>
                    {withCurrent(categories, draft.category).map((name) => <option key={name}>{name}</option>)}
                  </select>
                </label>
              )}
              {accountSelect("Account", draft.account, (account) => set({ account }))}
            </>
          )}
          <label className="wide">
            Description
            <input list="descriptions" value={draft.description} onChange={(e) => set({ description: e.target.value })} disabled={readOnly} />
            <datalist id="descriptions">{suggestions.map((text) => <option key={text} value={text} />)}</datalist>
          </label>
          <label className="wide">
            Remarks
            <input value={draft.remarks} onChange={(e) => set({ remarks: e.target.value })} disabled={readOnly} />
          </label>
          <label className="check wide">
            <input type="checkbox" checked={draft.bookmarked} onChange={(e) => set({ bookmarked: e.target.checked })} disabled={readOnly} />
            Bookmark
          </label>
        </div>

        {(problem || error) && <p className="problem" role="alert">{problem || error}</p>}
        {!canWrite && <p className="note">The deployed Apps Script is too old to save from the web. Redeploy it from the app's Database Setup screen.</p>}

        <footer>
          {!editing.isNew && !readOnly && (
            confirmDelete
              ? <button type="button" className="danger" onClick={onDelete} disabled={busy}>Delete for good</button>
              : <button type="button" className="ghost danger-text" onClick={() => setConfirmDelete(true)} disabled={busy}>Delete</button>
          )}
          <span className="grow" />
          <button type="button" className="ghost" onClick={closeEditor}>{readOnly ? "Close" : "Cancel"}</button>
          {!readOnly && <button type="submit" className="primary" disabled={busy}>{busy ? "Saving…" : "Save"}</button>}
        </footer>
      </form>
    </div>
  );
}

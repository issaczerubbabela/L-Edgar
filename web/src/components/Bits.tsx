import type { ReactNode } from "react";
import { longDay } from "../lib/dates";
import { money } from "../lib/money";
import { useStore } from "../store";
import type { Txn } from "../types";

export function Card({ title, aside, children, className = "" }: { title?: string; aside?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`card ${className}`}>
      {(title || aside) && (
        <header>
          {title && <h2>{title}</h2>}
          {aside}
        </header>
      )}
      {children}
    </section>
  );
}

export function Stat({ label, value, hint, tone }: { label: string; value: string; hint?: ReactNode; tone?: "pos" | "neg" }) {
  return (
    <div className="stat">
      <span>{label}</span>
      <b className={tone}>{value}</b>
      {hint && <small>{hint}</small>}
    </div>
  );
}

export function Segmented<T extends string>({ value, options, onChange, label }: { value: T; options: { value: T; label: string }[]; onChange: (value: T) => void; label: string }) {
  return (
    <div className="segmented" role="tablist" aria-label={label}>
      {options.map((option) => (
        <button key={option.value} role="tab" aria-selected={value === option.value} className={value === option.value ? "on" : ""} onClick={() => onChange(option.value)}>
          {option.label}
        </button>
      ))}
    </div>
  );
}

export function Stepper({ label, onPrev, onNext, onToday }: { label: string; onPrev?: () => void; onNext?: () => void; onToday?: () => void }) {
  return (
    <div className="stepper">
      <button onClick={onPrev} disabled={!onPrev} aria-label="Previous">‹</button>
      <strong>{label}</strong>
      <button onClick={onNext} disabled={!onNext} aria-label="Next">›</button>
      {onToday && <button className="ghost" onClick={onToday}>Today</button>}
    </div>
  );
}

export const Empty = ({ children }: { children: ReactNode }) => <p className="empty">{children}</p>;

const SIGN: Record<string, string> = { Expense: "−", Income: "+", Transfer: "", Adjustment: "" };

export function amountText(txn: Txn) {
  if (txn.type === "Adjustment") return money(txn.amount, true);
  return `${SIGN[txn.type] ?? ""}${money(Math.abs(txn.amount))}`;
}

/** One Transaction in a list. Clicking it opens the form; the star toggles its Bookmark. */
export function TxnRow({ txn, showDate = false, trailing }: { txn: Txn; showDate?: boolean; trailing?: ReactNode }) {
  const { edit, toggleBookmark, canWrite } = useStore();
  const title = txn.description || txn.category || (txn.type === "Adjustment" ? "Balance adjustment" : txn.type);
  const where = txn.type === "Transfer" ? `${txn.from || "?"} → ${txn.to || "?"}` : txn.account;
  const detail = [showDate ? longDay(txn.date) : "", txn.type === "Transfer" ? "Transfer" : txn.type === "Adjustment" ? "Balance adjustment" : txn.category, where].filter(Boolean).join(" · ");
  return (
    <div className="txn" role="button" tabIndex={0} onClick={() => edit(txn)} onKeyDown={(e) => e.key === "Enter" && edit(txn)}>
      <div className="txn-main">
        <span>{title}</span>
        <small>{detail}{txn.remarks ? ` · ${txn.remarks}` : ""}</small>
      </div>
      <div className="txn-side">
        <b className={txn.type === "Income" ? "pos" : ""}>{amountText(txn)}</b>
        {trailing}
      </div>
      <button
        className={txn.bookmarked ? "star on" : "star"}
        aria-label={txn.bookmarked ? "Remove bookmark" : "Bookmark"}
        aria-pressed={txn.bookmarked}
        disabled={!canWrite}
        onClick={(e) => {
          e.stopPropagation();
          toggleBookmark(txn);
        }}
      >
        {txn.bookmarked ? "★" : "☆"}
      </button>
    </div>
  );
}

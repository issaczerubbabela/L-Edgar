import { useMemo, useState } from "react";
import { Card, Empty, Stat, Stepper } from "../components/Bits";
import { Meter } from "../components/Charts";
import { dayMonth, today } from "../lib/dates";
import { go } from "../lib/hooks";
import { money, rupees } from "../lib/money";
import { cycleRange, cycleSummary, type CategorySpend } from "../math/buckets";
import { emptyFilter } from "../math/filter";
import { useStore } from "../store";

/** The same eight hues the chart palette uses, so a bucket keeps one colour everywhere. */
const BUCKET_COLORS = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300", "#6250d6", "#e34948"];

/** Salary cycles and their Buckets, as last backed up by the phone. Planning stays in the app. */
export function Budget() {
  const { data, setFilter } = useStore();
  const now = today();
  const cycles = useMemo(() => [...(data?.cycles ?? [])].sort((a, b) => a.start.localeCompare(b.start)), [data]);
  const [index, setIndex] = useState(() => Math.max(cycles.length - 1, 0));
  const [open, setOpen] = useState<number | "unbucketed" | null>(null);
  const cycle = cycles[Math.min(index, cycles.length - 1)];
  const summary = useMemo(() => cycle && data && cycleSummary(cycle, data.transactions, now), [cycle, data, now]);

  if (!cycle || !summary) {
    return <Empty>No salary cycle has reached the Sheet yet. Start one in the phone app's Budget tab; it shows up here after the app's next backup.</Empty>;
  }
  const range = cycleRange(cycle, now);
  const openCategory = (category: string) => {
    setFilter({ ...emptyFilter, categories: [category], types: ["Expense"], from: range.start, to: summary.isRunning ? "" : range.end });
    go("search");
  };
  const categoryList = (categories: CategorySpend[]) => (
    <div className="sublist">
      {categories.length === 0 ? <p className="muted">No spending here yet.</p> : categories.map((c) => (
        <button key={c.category} onClick={() => openCategory(c.category)}><span>{c.category}</span><b>{money(c.amount)}</b></button>
      ))}
    </div>
  );

  return (
    <>
      <div className="toolbar">
        <Stepper label={`${dayMonth(cycle.start)} – ${dayMonth(cycle.end)} ${cycle.end.slice(0, 4)}`} onPrev={index > 0 ? () => setIndex(index - 1) : undefined} onNext={index < cycles.length - 1 ? () => setIndex(index + 1) : undefined} />
        <span className="muted">
          {summary.isRunning
            ? summary.daysOverdue > 0 ? `Running ${summary.daysOverdue} day${summary.daysOverdue === 1 ? "" : "s"} past its end date` : `Day ${summary.dayNumber} of ${summary.totalDays}`
            : "Closed"}
        </span>
      </div>

      <Card>
        <div className="stats flat">
          <Stat label={summary.isRunning ? "Left to spend" : "Left unspent"} value={rupees(summary.leftToSpend)} tone={summary.leftToSpend < 0 ? "neg" : undefined} hint={`of ${rupees(cycle.spendable)} spendable`} />
          <Stat label="Spent" value={rupees(summary.totalSpent)} />
          {summary.isRunning && <Stat label="Per day from here" value={rupees(summary.dailyPace)} hint={`${summary.daysLeft} day${summary.daysLeft === 1 ? "" : "s"} left`} />}
          <Stat label="Not in a bucket" value={rupees(summary.unallocated)} tone={summary.unallocated < 0 ? "neg" : undefined} hint={summary.unallocated < 0 ? "Buckets add up to more than the spendable amount" : "Spendable minus all buckets"} />
        </div>
        <Meter value={summary.totalSpent} total={cycle.spendable} tick={summary.isRunning ? summary.elapsed : undefined} />
        {summary.isRunning && <p className="muted small">The mark shows how far through the cycle today is.</p>}
      </Card>

      <div className="grid">
        {summary.buckets.map((b) => {
          const color = BUCKET_COLORS[((b.bucket.colorIndex % 8) + 8) % 8];
          const isOpen = open === b.bucket.id;
          return (
            <Card key={b.bucket.id} className="bucket">
              <button className="bucket-head" onClick={() => setOpen(isOpen ? null : b.bucket.id)} aria-expanded={isOpen}>
                <span className="swatch" style={{ background: color }}>{b.bucket.emoji}</span>
                <span className="bucket-name">{b.bucket.name}{b.bucket.note && <small>{b.bucket.note}</small>}</span>
                <span className="bucket-left">
                  <b className={b.remaining < 0 ? "neg" : ""}>{b.remaining < 0 ? `${rupees(-b.remaining)} over` : `${rupees(b.remaining)} left`}</b>
                  <small>{rupees(b.spent)} of {rupees(b.bucket.allocated)}</small>
                </span>
              </button>
              <Meter value={b.spent} total={b.bucket.allocated} tick={summary.isRunning ? summary.elapsed : undefined} color={color} />
              {isOpen && (
                <>
                  {categoryList(b.categories)}
                  <p className="muted small">Takes: {b.bucket.categories.join(", ") || "no categories yet"}</p>
                </>
              )}
            </Card>
          );
        })}
        {summary.unbucketed.length > 0 && (
          <Card className="bucket">
            <button className="bucket-head" onClick={() => setOpen(open === "unbucketed" ? null : "unbucketed")} aria-expanded={open === "unbucketed"}>
              <span className="swatch" />
              <span className="bucket-name">Unbucketed<small>Categories no bucket takes</small></span>
              <span className="bucket-left"><b>{rupees(summary.unbucketedSpent)}</b><small>spent</small></span>
            </button>
            {open === "unbucketed" && categoryList(summary.unbucketed)}
          </Card>
        )}
      </div>
      {summary.buckets.length === 0 && <Empty>This cycle has no buckets.</Empty>}
    </>
  );
}

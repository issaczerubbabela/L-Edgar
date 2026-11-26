import { useMemo, useState } from "react";
import { Card, Empty, Segmented, Stat, Stepper, TxnRow } from "../components/Bits";
import { GroupedBars, Lines } from "../components/Charts";
import { dayMonth, eachDay, fromDay, longDay, today } from "../lib/dates";
import { go } from "../lib/hooks";
import { money, rupees } from "../lib/money";
import { emptyFilter } from "../math/filter";
import { buildReport, days, periodFor, shift, type Period, type PeriodKind } from "../math/stats";
import { useStore } from "../store";

const IN = "var(--c-in)";
const OUT = "var(--c-out)";
const SAVED = "var(--c-saved)";

/** Earned, Spent, Saved and Left over for a week, month, year, Salary cycle or any range, with charts. */
export function Stats() {
  const { data, setFilter } = useStore();
  const now = today();
  const cycles = data?.cycles ?? [];
  const [period, setPeriod] = useState<Period>(() => periodFor("month", now, cycles, now));

  const report = useMemo(() => data && buildReport({ ...data, period, today: now }), [data, period, now]);
  if (!data || !report) return null;

  const kinds: { value: PeriodKind; label: string }[] = [
    { value: "week", label: "Week" }, { value: "month", label: "Month" }, { value: "year", label: "Year" },
    ...(cycles.length ? [{ value: "cycle" as const, label: "Cycle" }] : []), { value: "custom", label: "Custom" },
  ];
  const previous = shift(period, -1, cycles, now);
  const next = shift(period, 1, cycles, now);
  const isCycle = period.kind === "cycle";
  const t = report.totals;
  const daily = report.timeline.length === days(period);
  const pointTitles = report.timeline.map((p) => (p.start === p.end ? longDay(p.start) : `${dayMonth(p.start)} – ${dayMonth(p.end)}`));
  const pad = (values: number[]) => report.timeline.map((_, i) => values[i] ?? null);
  const openSearch = (patch: object) => {
    setFilter({ ...emptyFilter, from: period.start, to: period.end, ...patch });
    go("search");
  };
  const biggestCategory = Math.max(...report.categories.map((c) => Math.max(c.amount, c.usual ?? 0)), 1);
  const heat = heatSteps(report.timeline.filter((p) => p.start <= now).map((p) => p.spent));

  return (
    <>
      <div className="toolbar">
        <Segmented label="Period" value={period.kind} onChange={(kind) => setPeriod(periodFor(kind, now, cycles, now))} options={kinds} />
        {period.kind === "custom" ? (
          <div className="range">
            <input type="date" value={period.start} max={period.end} onChange={(e) => e.target.value && setPeriod({ ...period, start: e.target.value })} aria-label="From" />
            <span>to</span>
            <input type="date" value={period.end} min={period.start} onChange={(e) => e.target.value && setPeriod({ ...period, end: e.target.value })} aria-label="To" />
          </div>
        ) : (
          <Stepper label={period.label} onPrev={previous ? () => setPeriod(previous) : undefined} onNext={next ? () => setPeriod(next) : undefined} />
        )}
      </div>

      <div className="stats">
        <Stat label="Earned" value={rupees(t.earned)} hint={t.refunds > 0 && !isCycle ? `plus ${rupees(t.refunds)} refunds` : undefined} />
        <Stat label="Spent" value={rupees(t.spent)} hint={report.spentChangePercent === null ? undefined : `${report.spentChangePercent > 0 ? "▲" : "▼"} ${Math.abs(report.spentChangePercent)}% vs the same point last ${period.kind === "custom" ? "range" : period.kind}`} />
        <Stat label="Saved" value={rupees(t.saved)} />
        {isCycle
          ? <Stat label="Left to spend" value={rupees((cycles.find((c) => c.id === period.cycleId)?.spendable ?? 0) - t.spent)} hint="Spendable minus every expense, as on Budget" />
          : <Stat label="Left over" value={rupees(t.leftOver)} tone={t.leftOver < 0 ? "neg" : "pos"} hint="Earned − Spent − Saved" />}
      </div>

      {!report.hasTransactions && <Empty>No transactions in this period.</Empty>}

      <div className="grid">
        {daily && (
          <Card title="Spending pace" aside={report.projection !== null ? <span className="muted">Heading for {rupees(report.projection)}</span> : undefined}>
            <Lines labels={report.timeline.map((p) => p.label)} titles={pointTitles} series={[
              { name: "This period", color: OUT, values: pad(report.pace) },
              ...(report.previousPace.length ? [{ name: "Previous", color: "var(--muted)", values: pad(report.previousPace), dashed: true }] : []),
              ...(report.budgetPace.length ? [{ name: "Budget", color: SAVED, values: report.budgetPace, dashed: true }] : []),
            ]} />
          </Card>
        )}

        <Card title={daily ? "Day by day" : "Month by month"}>
          <GroupedBars labels={report.timeline.map((p) => p.label)} titles={pointTitles} onPick={(i) => openSearch({ from: report.timeline[i].start, to: report.timeline[i].end })}
            series={[{ name: "Earned", color: IN, values: report.timeline.map((p) => p.earned) }, { name: "Spent", color: OUT, values: report.timeline.map((p) => p.spent) }]} />
        </Card>

        <Card title="Where it went" aside={<span className="muted">Tick marks the usual</span>}>
          {report.categories.length === 0 ? <Empty>No spending yet.</Empty> : report.categories.map((c) => (
            <button key={c.category} className="hbar" onClick={() => openSearch({ categories: [c.category], types: ["Expense"] })} title={c.usual === null ? undefined : `Usually ${rupees(c.usual)}`}>
              <span>{c.category}</span>
              <div><i style={{ width: `${(c.amount / biggestCategory) * 100}%` }} />{c.usual !== null && <u style={{ left: `${(c.usual / biggestCategory) * 100}%` }} />}</div>
              <b>{rupees(c.amount)}</b>
              <small>{t.spent > 0 ? `${Math.round((c.amount / Math.max(t.spent + (isCycle ? 0 : t.refunds), 1)) * 100)}%` : ""}</small>
            </button>
          ))}
        </Card>

        {report.trend.length > 0 && (
          <Card title={period.kind === "year" ? "Months" : `Last ${report.trend.length} ${period.kind}s`}>
            <GroupedBars labels={report.trend.map((b) => b.label)} titles={report.trend.map((b) => b.period.label)} onPick={(i) => setPeriod(report.trend[i].period)}
              series={[{ name: "Earned", color: IN, values: report.trend.map((b) => b.earned) }, { name: "Spent", color: OUT, values: report.trend.map((b) => b.spent) }, { name: "Saved", color: SAVED, values: report.trend.map((b) => b.saved) }]} />
          </Card>
        )}

        {daily && days(period) <= 62 && (
          <Card title="Spending calendar">
            <div className="heat">
              {["M", "T", "W", "T", "F", "S", "S"].map((d, i) => <div key={i} className="dow">{d}</div>)}
              {Array.from({ length: (fromDay(period.start).getDay() + 6) % 7 }, (_, i) => <div key={`pad${i}`} />)}
              {eachDay(period.start, period.end).map((day, i) => {
                const spent = report.timeline[i]?.spent ?? 0;
                const future = day > now;
                return (
                  <button key={day} className={`cell s${future ? "x" : heat(spent)}`} title={`${longDay(day)}: ${future ? "—" : money(spent)}`} onClick={() => openSearch({ from: day, to: day })}>
                    {Number(day.slice(8))}
                  </button>
                );
              })}
            </div>
            <div className="legend"><span>Less</span>{[1, 2, 3, 4, 5].map((s) => <i key={s} className={`cell s${s}`} />)}<span>More</span></div>
          </Card>
        )}

        <Card title="Paid from">
          <table className="table plain">
            <tbody>
              <tr><td>Cards</td><td>{money(report.paidFrom.card)}</td></tr>
              <tr><td>Cash and accounts</td><td>{money(report.paidFrom.cashAndAccounts)}</td></tr>
              <tr><td>Moved between accounts</td><td>{money(report.paidFrom.transfers)}</td></tr>
            </tbody>
          </table>
        </Card>

        <Card title="Biggest expenses">
          {report.biggest.length === 0 ? <Empty>No spending yet.</Empty> : report.biggest.map((txn) => <TxnRow key={txn.id} txn={txn} showDate />)}
        </Card>
      </div>
    </>
  );
}

/** Five shades by quintile of the days that had spending, so one big bill doesn't wash the rest out. */
function heatSteps(spent: number[]) {
  const positives = spent.filter((v) => v > 0).sort((a, b) => a - b);
  return (value: number) => {
    if (value <= 0 || positives.length === 0) return 0;
    const rank = positives.filter((v) => v <= value).length / positives.length;
    return Math.min(Math.max(Math.floor(rank * 5), 1), 5);
  };
}

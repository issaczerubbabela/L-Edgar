import { useMemo, useState } from "react";
import { Card, Empty, Segmented, Stat, Stepper, TxnRow } from "../components/Bits";
import { FilterBar } from "../components/FilterBar";
import { eachDay, fromDay, longDay, monthEnd, monthLabel, monthOf, monthStart, shiftMonth, today } from "../lib/dates";
import { compact, money } from "../lib/money";
import { emptyFilter, matches, net, newestFirst, type Filter } from "../math/filter";
import { useStore } from "../store";
import type { Txn } from "../types";

type Mode = "daily" | "calendar" | "year";

/** Every Transaction, one month at a time: day by day, on a calendar, or as a year of months. */
export function Ledger() {
  const { data } = useStore();
  const [month, setMonth] = useState(monthOf(today()));
  const [mode, setMode] = useState<Mode>("daily");
  const [filter, setFilter] = useState<Filter>(emptyFilter);
  const [picked, setPicked] = useState<string | null>(null);

  const filtered = useMemo(() => (data?.transactions ?? []).filter((t) => matches(t, { ...filter, from: "", to: "" })), [data, filter]);
  const year = month.slice(0, 4);
  const inMonth = useMemo(() => filtered.filter((t) => monthOf(t.date) === month).sort(newestFirst), [filtered, month]);
  const inYear = useMemo(() => filtered.filter((t) => t.date.startsWith(year)), [filtered, year]);
  const shown = mode === "year" ? inYear : inMonth;
  const sums = net(shown);

  const byDay = useMemo(() => {
    const groups = new Map<string, Txn[]>();
    for (const t of inMonth) groups.set(t.date, [...(groups.get(t.date) ?? []), t]);
    return groups;
  }, [inMonth]);

  const step = (n: number) => {
    setPicked(null);
    setMonth(mode === "year" ? shiftMonth(month, 12 * n) : shiftMonth(month, n));
  };

  return (
    <>
      <div className="toolbar">
        <Stepper label={mode === "year" ? year : monthLabel(month)} onPrev={() => step(-1)} onNext={() => step(1)} onToday={() => { setPicked(null); setMonth(monthOf(today())); }} />
        <Segmented label="View" value={mode} onChange={(m) => { setMode(m); setPicked(null); }} options={[{ value: "daily", label: "Daily" }, { value: "calendar", label: "Calendar" }, { value: "year", label: "Year" }]} />
      </div>

      <div className="stats">
        <Stat label="Income" value={money(sums.income)} />
        <Stat label="Expenses" value={money(sums.expenses)} />
        <Stat label="Net" value={money(sums.net, true)} tone={sums.net < 0 ? "neg" : "pos"} hint="Income minus expenses for the rows shown" />
      </div>

      <FilterBar filter={filter} onChange={setFilter} dates={false} />

      {mode === "daily" && (
        byDay.size === 0 ? <Empty>No transactions in {monthLabel(month)}{filter === emptyFilter ? "" : " match these filters"}.</Empty> : (
          [...byDay].map(([day, rows]) => <DayCard key={day} day={day} rows={rows} />)
        )
      )}

      {mode === "calendar" && (
        <>
          <Card>
            <div className="calendar" role="grid" aria-label={monthLabel(month)}>
              {["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((d) => <div key={d} className="dow">{d}</div>)}
              {Array.from({ length: (fromDay(monthStart(month)).getDay() + 6) % 7 }, (_, i) => <div key={`pad${i}`} />)}
              {eachDay(monthStart(month), monthEnd(month)).map((day) => {
                const sum = net(byDay.get(day) ?? []);
                return (
                  <button key={day} className={`day${picked === day ? " on" : ""}${day === today() ? " today" : ""}`} onClick={() => setPicked(picked === day ? null : day)} aria-pressed={picked === day}>
                    <span>{Number(day.slice(8))}</span>
                    {sum.income > 0 && <small className="pos">+{compact(sum.income).slice(1)}</small>}
                    {sum.expenses > 0 && <small>−{compact(sum.expenses).slice(1)}</small>}
                  </button>
                );
              })}
            </div>
          </Card>
          {picked && (byDay.has(picked) ? <DayCard day={picked} rows={byDay.get(picked)!} /> : <Empty>Nothing on {longDay(picked)}.</Empty>)}
        </>
      )}

      {mode === "year" && (
        <Card>
          <table className="table">
            <thead><tr><th>Month</th><th>Income</th><th>Expenses</th><th>Net</th></tr></thead>
            <tbody>
              {Array.from({ length: 12 }, (_, i) => `${year}-${String(12 - i).padStart(2, "0")}`).map((m) => {
                const rows = inYear.filter((t) => monthOf(t.date) === m);
                if (rows.length === 0 && m > monthOf(today())) return null;
                const sum = net(rows);
                return (
                  <tr key={m} onClick={() => { setMonth(m); setMode("daily"); }} tabIndex={0} onKeyDown={(e) => e.key === "Enter" && (setMonth(m), setMode("daily"))}>
                    <td>{monthLabel(m)}</td><td>{money(sum.income)}</td><td>{money(sum.expenses)}</td><td className={sum.net < 0 ? "neg" : ""}>{money(sum.net, true)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Card>
      )}
    </>
  );
}

function DayCard({ day, rows }: { day: string; rows: Txn[] }) {
  const sum = net(rows);
  return (
    <Card title={longDay(day)} aside={<span className="muted">{sum.income > 0 && <span className="pos">+{money(sum.income)} </span>}{sum.expenses > 0 && `−${money(sum.expenses)}`}</span>}>
      {rows.map((t) => <TxnRow key={t.id} txn={t} />)}
    </Card>
  );
}


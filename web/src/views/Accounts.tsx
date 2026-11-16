import { useMemo, useState } from "react";
import { Card, Empty, Stat, TxnRow } from "../components/Bits";
import { GroupedBars, Lines } from "../components/Charts";
import { longDay, monthLabel, monthOf, monthShort, shiftMonth, today } from "../lib/dates";
import { go } from "../lib/hooks";
import { key, money } from "../lib/money";
import { balances, cashFlow, liabilityGroups, netWorthHistory, statement, totals } from "../math/accounts";
import { useStore } from "../store";

/** Accounts by group with live balances, Net worth over time and Cash flow. */
export function Accounts() {
  const { data } = useStore();
  const [showHidden, setShowHidden] = useState(false);
  const view = useMemo(() => {
    if (!data) return null;
    const liabilities = liabilityGroups(data.dropdowns);
    const months = Array.from({ length: 12 }, (_, i) => shiftMonth(monthOf(today()), i - 11));
    return {
      liabilities,
      months,
      balance: balances(data.accounts, data.transactions),
      totals: totals(data.accounts, liabilities, data.transactions),
      history: netWorthHistory(data.accounts, liabilities, data.transactions, months),
      flow: cashFlow(data.accounts, data.transactions, months),
    };
  }, [data]);
  if (!data || !view) return null;
  if (data.accounts.length === 0) return <Empty>No accounts have reached the Sheet yet. They appear after the phone app's next backup.</Empty>;

  const groupOrder = data.dropdowns.filter((d) => d.type === "ACCOUNT_GROUP").sort((a, b) => a.order - b.order).map((d) => key(d.name));
  const rank = (group: string) => (groupOrder.indexOf(key(group)) + 1 || 999);
  const visible = data.accounts.filter((a) => showHidden || !a.hidden).sort((a, b) => rank(a.group) - rank(b.group) || a.group.localeCompare(b.group) || a.order - b.order);
  const groups = [...new Set(visible.map((a) => a.group))];
  const labels = view.months.map(monthShort);
  const titles = view.months.map(monthLabel);

  return (
    <>
      <div className="stats">
        <Stat label="Assets" value={money(view.totals.assets)} />
        <Stat label="Liabilities" value={money(view.totals.liabilities)} />
        <Stat label="Net worth" value={money(view.totals.netWorth)} tone={view.totals.netWorth < 0 ? "neg" : undefined} hint="Assets minus liabilities" />
      </div>

      <div className="grid">
        <Card title="Net worth">
          <Lines labels={labels} titles={titles} series={[{ name: "Net worth", color: "var(--c-in)", values: view.history.map((p) => p.netWorth) }]} />
        </Card>
        <Card title="Cash flow">
          <GroupedBars labels={labels} titles={titles} series={[{ name: "Money in", color: "var(--c-in)", values: view.flow.map((f) => f.moneyIn) }, { name: "Money out", color: "var(--c-out)", values: view.flow.map((f) => f.moneyOut) }]} />
        </Card>
      </div>

      <div className="toolbar">
        <label className="check"><input type="checkbox" checked={showHidden} onChange={(e) => setShowHidden(e.target.checked)} />Show hidden accounts</label>
      </div>

      {groups.map((group) => {
        const members = visible.filter((a) => a.group === group);
        const sum = members.reduce((total, a) => total + (view.balance.get(key(a.name)) ?? 0), 0);
        return (
          <Card key={group} title={group || "No group"} aside={<span className="muted">{view.liabilities.has(key(group)) ? "Liability · " : ""}{money(sum)}</span>}>
            {members.map((a) => {
              const value = view.balance.get(key(a.name)) ?? 0;
              return (
                <button key={a.id + a.name} className="row" onClick={() => go(`accounts/${encodeURIComponent(a.name)}`)}>
                  <span>{a.name}<small>{[a.description, a.hidden ? "Hidden" : "", a.includedInTotals ? "" : "Not in totals"].filter(Boolean).join(" · ")}</small></span>
                  <b className={value < 0 ? "neg" : ""}>{money(value)}</b>
                </button>
              );
            })}
          </Card>
        );
      })}
    </>
  );
}

/** One Account's statement: every month opens, takes money in and out, and closes. */
export function AccountDetail({ name }: { name: string }) {
  const { data, add } = useStore();
  const account = data?.accounts.find((a) => key(a.name) === key(name));
  const months = useMemo(() => (account && data ? statement(account, data.transactions) : []), [account, data]);
  if (!data) return null;
  if (!account) return <Empty>There is no account called “{name}”.</Empty>;
  const balance = months[0]?.closing ?? account.initialBalance;

  return (
    <>
      <div className="toolbar">
        <button className="ghost" onClick={() => go("accounts")}>‹ Accounts</button>
        <span className="grow" />
        <button className="primary" onClick={() => add({ account: account.name })}>+ Add</button>
      </div>
      <div className="stats">
        <Stat label={account.name} value={money(balance)} tone={balance < 0 ? "neg" : undefined} hint={account.group} />
        <Stat label="Initial balance" value={money(account.initialBalance)} hint={account.asOf > "1970-01-01" ? `As of ${longDay(account.asOf)}` : undefined} />
        {account.reconciledAt && <Stat label="Last reconciled" value={longDay(account.reconciledAt)} />}
      </div>
      {months.length === 0 && <Empty>No transactions on this account.</Empty>}
      {months.map((m) => (
        <Card key={m.month} title={monthLabel(m.month)} aside={<span className="muted">{money(m.opening)} + {money(m.moneyIn)} − {money(m.moneyOut)} = <b>{money(m.closing)}</b></span>}>
          {m.rows.map((row) => (
            <TxnRow key={row.txn.id} txn={row.txn} showDate trailing={<small className="muted">{row.counts ? money(row.balanceAfter) : "Before the as-of date"}</small>} />
          ))}
        </Card>
      ))}
    </>
  );
}

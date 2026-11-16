import { useMemo, useState } from "react";
import { Card, Empty, Stat, TxnRow } from "../components/Bits";
import { FilterBar } from "../components/FilterBar";
import { money } from "../lib/money";
import { matches, net, newestFirst } from "../math/filter";
import { useStore } from "../store";

type Sort = "newest" | "oldest" | "largest" | "smallest";
const PAGE = 200;

/** Search across every Transaction, with the full filter panel. Bookmarks are one of its filters. */
export function Search() {
  const { data, filter, setFilter } = useStore();
  const [sort, setSort] = useState<Sort>("newest");
  const [limit, setLimit] = useState(PAGE);

  const rows = useMemo(() => {
    const found = (data?.transactions ?? []).filter((t) => matches(t, filter));
    switch (sort) {
      case "oldest": return found.sort((a, b) => newestFirst(b, a));
      case "largest": return found.sort((a, b) => Math.abs(b.amount) - Math.abs(a.amount));
      case "smallest": return found.sort((a, b) => Math.abs(a.amount) - Math.abs(b.amount));
      default: return found.sort(newestFirst);
    }
  }, [data, filter, sort]);
  const sums = net(rows);

  return (
    <>
      <FilterBar filter={filter} onChange={(f) => { setFilter(f); setLimit(PAGE); }} />
      <div className="stats">
        <Stat label="Transactions" value={String(rows.length)} />
        <Stat label="Income" value={money(sums.income)} />
        <Stat label="Expenses" value={money(sums.expenses)} />
        <Stat label="Net" value={money(sums.net, true)} tone={sums.net < 0 ? "neg" : "pos"} />
      </div>
      <Card title="Results" aside={
        <select value={sort} onChange={(e) => setSort(e.target.value as Sort)} aria-label="Sort">
          <option value="newest">Newest first</option><option value="oldest">Oldest first</option><option value="largest">Largest first</option><option value="smallest">Smallest first</option>
        </select>
      }>
        {rows.length === 0 ? <Empty>Nothing matches. Try fewer filters.</Empty> : rows.slice(0, limit).map((t) => <TxnRow key={t.id} txn={t} showDate />)}
        {rows.length > limit && <button className="more" onClick={() => setLimit(limit + PAGE)}>Show {Math.min(PAGE, rows.length - limit)} more</button>}
      </Card>
    </>
  );
}

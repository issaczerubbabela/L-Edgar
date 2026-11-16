import { TxnForm } from "./components/TxnForm";
import { go, useRoute } from "./lib/hooks";
import { useStore } from "./store";
import { AccountDetail, Accounts } from "./views/Accounts";
import { Budget } from "./views/Budget";
import { Ledger } from "./views/Ledger";
import { Search } from "./views/Search";
import { Settings, Setup } from "./views/Settings";
import { Stats } from "./views/Stats";

const TABS = [
  { route: "ledger", label: "Ledger", icon: "M4 6h16M4 12h16M4 18h10" },
  { route: "stats", label: "Stats", icon: "M5 20V10M12 20V4M19 20v-7" },
  { route: "budget", label: "Budget", icon: "M4 8h16v11H4zM8 8V5h8v3" },
  { route: "accounts", label: "Accounts", icon: "M3 10l9-6 9 6M5 10v9M19 10v9M9 19v-6h6v6M3 19h18" },
  { route: "search", label: "Search", icon: "M11 4a7 7 0 100 14 7 7 0 000-14zM20 20l-4-4" },
  { route: "settings", label: "Settings", icon: "M4 7h10M18 7h2M4 17h2M10 17h10M16 4v6M8 14v6" },
];

export function App() {
  const store = useStore();
  const route = useRoute() || "ledger";
  const [section, ...rest] = route.split("/");

  if (store.booting) return null;
  if (!store.url) return <Setup />;

  const title = TABS.find((tab) => tab.route === section)?.label ?? "Ledger";
  return (
    <div className="shell">
      <nav aria-label="Sections">
        <div className="brand"><img src="./icon.svg" alt="" width="28" height="28" /><span>L.Edgar</span></div>
        {TABS.map((tab) => (
          <a key={tab.route} href={`#/${tab.route}`} className={section === tab.route ? "on" : ""} aria-current={section === tab.route ? "page" : undefined}>
            <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path d={tab.icon} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
            <span>{tab.label}</span>
          </a>
        ))}
      </nav>
      <main>
        <header className="top">
          <h1>{title}</h1>
          <span className="grow" />
          <span className="muted sync">{store.refreshing ? "Reading the Sheet…" : store.data ? `Read ${ago(store.data.fetchedAt)}` : ""}</span>
          <button onClick={store.refresh} disabled={store.refreshing} aria-label="Refresh" title="Refresh">↻</button>
          <button className="primary" onClick={() => store.add()} disabled={!store.data || !store.canWrite}>+ Add</button>
        </header>
        {(store.error || store.notice) && !store.editing && (
          <div className={store.error ? "banner error" : "banner"} role="status">
            <span>{store.error || store.notice}</span>
            {!store.data && <button onClick={() => { store.disconnect(); go("ledger"); }}>Change URL</button>}
            <button onClick={store.dismiss} aria-label="Dismiss">✕</button>
          </div>
        )}
        {store.data && !store.canWrite && <div className="banner">The deployed Apps Script is older than this page needs, so it is view-only. Redeploy the script from the phone app's Database Setup screen.</div>}
        {!store.data ? (
          <p className="empty">{store.refreshing ? "Reading your Sheet for the first time. This takes a few seconds…" : "Nothing loaded yet."}</p>
        ) : section === "stats" ? <Stats />
          : section === "budget" ? <Budget />
          : section === "accounts" ? (rest.length ? <AccountDetail name={decodeURIComponent(rest.join("/"))} /> : <Accounts />)
          : section === "search" ? <Search />
          : section === "settings" ? <Settings />
          : <Ledger />}
      </main>
      <TxnForm />
    </div>
  );
}

function ago(time: number) {
  const minutes = Math.round((Date.now() - time) / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  return hours < 24 ? `${hours} h ago` : `${Math.round(hours / 24)} d ago`;
}

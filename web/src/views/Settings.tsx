import { useState } from "react";
import { looksLikeScriptUrl } from "../api";
import { settings } from "../cache";
import { Card, Segmented } from "../components/Bits";
import { useStore } from "../store";

export function applyTheme(theme: string) {
  if (theme === "auto") delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = theme;
}

/** First run: paste the same Apps Script web app URL the phone uses. */
export function Setup() {
  const { connect, refreshing, error } = useStore();
  const [url, setUrl] = useState("");
  const [problem, setProblem] = useState("");
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!looksLikeScriptUrl(url)) return setProblem("That doesn't look like an Apps Script web app URL. It starts with https://script.google.com/macros/s/ and ends with /exec.");
    setProblem("");
    await connect(url).catch(() => undefined);
  };
  return (
    <main className="setup">
      <form className="card" onSubmit={submit}>
        <img src="./icon.svg" alt="" width="56" height="56" />
        <h1>L.Edgar on the web</h1>
        <p>Paste the Apps Script web app URL from the phone app (More → Settings → Database Setup). This browser reads and writes the same Google Sheet, so changes show up on your phone after its next sync.</p>
        <label>
          Web app URL
          <input type="url" value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://script.google.com/macros/s/…/exec" autoFocus required />
        </label>
        {(problem || error) && <p className="problem" role="alert">{problem || error}</p>}
        <button className="primary" disabled={refreshing}>{refreshing ? "Connecting…" : "Connect"}</button>
        <p className="muted small">The URL is the only key to your Sheet and it stays in this browser. Don't connect on a computer you share.</p>
      </form>
    </main>
  );
}

const PHONE_ONLY = [
  "Auto-capture of bank and UPI alerts, and its review inbox",
  "Repeat rules (their transactions still show up here once added)",
  "Trips and split expenses",
  "Reconciling an account, and adding or editing accounts",
  "Planning buckets, starting a salary cycle, editing categories",
  "Reminders, app lock, Excel export and CSV import",
  "Adding or editing while offline",
];

export function Settings() {
  const { url, data, refresh, refreshing, disconnect } = useStore();
  const [theme, setTheme] = useState(settings.getTheme());
  const [reveal, setReveal] = useState(false);
  const pick = (next: string) => {
    setTheme(next);
    settings.setTheme(next);
    applyTheme(next);
  };
  return (
    <>
      <Card title="Google Sheet">
        <p className="mono">{reveal ? url : url.replace(/\/s\/([\w-]{6})[\w-]+/, "/s/$1…")}</p>
        <p className="muted">
          {data ? `Last read ${new Date(data.fetchedAt).toLocaleString("en-IN")} · ${data.transactions.length} transactions, ${data.accounts.length} accounts · script version ${data.scriptVersion || "unknown"}` : "Not read yet"}
        </p>
        <div className="actions">
          <button className="primary" onClick={refresh} disabled={refreshing}>{refreshing ? "Reading…" : "Refresh now"}</button>
          <button onClick={() => setReveal(!reveal)}>{reveal ? "Hide URL" : "Show URL"}</button>
          <button className="ghost danger-text" onClick={() => confirm("Disconnect this browser? The saved URL and the offline copy are removed. Your Sheet is not touched.") && disconnect()}>Disconnect</button>
        </div>
      </Card>
      <Card title="Appearance">
        <Segmented label="Theme" value={theme} onChange={pick} options={[{ value: "auto", label: "Match device" }, { value: "light", label: "Light" }, { value: "dark", label: "Dark" }]} />
      </Card>
      <Card title="How this stays in step with your phone">
        <p>Transactions you add, change or delete here go straight to the Sheet. The phone picks them up when you open the app or tap Sync now.</p>
        <p>Accounts, categories and buckets are shown as of the phone's last backup. Balances and bucket spending are worked out here from the transactions, so they include edits made on the web.</p>
      </Card>
      <Card title="Only in the phone app">
        <ul>{PHONE_ONLY.map((item) => <li key={item}>{item}</li>)}</ul>
      </Card>
    </>
  );
}

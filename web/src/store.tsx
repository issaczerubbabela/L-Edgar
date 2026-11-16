// One place holds the Sheet snapshot and every way to change it. Screens read from here and never
// call the script themselves.

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { ApiError, REQUIRED_SCRIPT_VERSION, StaleError, deleteTransactions, fetchSnapshot, upsertTransaction } from "./api";
import { clearSnapshot, loadSnapshot, saveSnapshot, settings } from "./cache";
import { today, timestampNow } from "./lib/dates";
import { emptyFilter, type Filter } from "./math/filter";
import type { Snapshot, Txn } from "./types";

/** What the transaction form is open on: an existing Transaction, a blank one, or nothing. */
export type Editing = { txn: Txn; isNew: boolean } | null;

interface Store {
  url: string;
  data: Snapshot | null;
  /** True until the cache has been read once, so the setup screen never flashes. */
  booting: boolean;
  refreshing: boolean;
  error: string;
  notice: string;
  /** False when the deployed script is too old to take writes from the web. */
  canWrite: boolean;
  connect: (url: string) => Promise<void>;
  disconnect: () => void;
  refresh: () => Promise<void>;
  save: (txn: Txn) => Promise<boolean>;
  remove: (txn: Txn) => Promise<boolean>;
  toggleBookmark: (txn: Txn) => void;
  dismiss: () => void;
  editing: Editing;
  edit: (txn: Txn) => void;
  add: (preset?: Partial<Txn>) => void;
  closeEditor: () => void;
  filter: Filter;
  setFilter: (filter: Filter) => void;
}

const Context = createContext<Store | null>(null);
export const useStore = () => {
  const store = useContext(Context);
  if (!store) throw new Error("useStore outside StoreProvider");
  return store;
};

export function blankTxn(preset: Partial<Txn> = {}): Txn {
  return {
    id: crypto.randomUUID(), revision: "", timestamp: timestampNow(), date: today(), type: "Expense", category: "", description: "",
    amount: 0, account: "", from: "", to: "", remarks: "", bookmarked: false, ...preset,
  };
}

export function StoreProvider({ children }: { children: ReactNode }) {
  const [url, setUrl] = useState(settings.getUrl);
  const [data, setData] = useState<Snapshot | null>(null);
  const [booting, setBooting] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [editing, setEditing] = useState<Editing>(null);
  const [filter, setFilter] = useState<Filter>(emptyFilter);
  const dataRef = useRef(data);
  dataRef.current = data;

  const commit = useCallback((next: Snapshot) => {
    setData(next);
    void saveSnapshot(next);
  }, []);

  const load = useCallback(async (target: string) => {
    setRefreshing(true);
    setError("");
    try {
      commit(await fetchSnapshot(target));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Something went wrong reading the Sheet.");
      throw e;
    } finally {
      setRefreshing(false);
    }
  }, [commit]);

  const refresh = useCallback(async () => {
    if (url) await load(url).catch(() => undefined);
  }, [url, load]);

  // Open on the cached copy at once, then bring it up to date in the background.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const cached = url ? await loadSnapshot() : undefined;
      if (cancelled) return;
      if (cached) setData(cached);
      setBooting(false);
      if (url) void load(url).catch(() => undefined);
    })();
    return () => {
      cancelled = true;
    };
    // Runs once: connect() and disconnect() look after later changes to the URL.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Pick up changes made on the phone when the tab comes back into view.
  useEffect(() => {
    const onVisible = () => {
      const age = Date.now() - (dataRef.current?.fetchedAt ?? 0);
      if (document.visibilityState === "visible" && url && age > 5 * 60_000) void load(url).catch(() => undefined);
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [url, load]);

  const connect = useCallback(async (next: string) => {
    const trimmed = next.trim();
    await load(trimmed);
    settings.setUrl(trimmed);
    setUrl(trimmed);
  }, [load]);

  const disconnect = useCallback(() => {
    settings.setUrl(null);
    void clearSnapshot();
    setUrl("");
    setData(null);
    setError("");
  }, []);

  const replaceTxn = useCallback((txn: Txn | null, id: string) => {
    const current = dataRef.current;
    if (!current) return;
    const others = current.transactions.filter((t) => t.id !== id);
    commit({ ...current, transactions: txn ? [...others, txn] : others });
  }, [commit]);

  const write = useCallback(async (work: () => Promise<void>): Promise<boolean> => {
    setError("");
    try {
      await work();
      return true;
    } catch (e) {
      if (e instanceof StaleError) {
        setNotice("That transaction was changed on your phone or in the Sheet first, so nothing was overwritten. Here is the latest version.");
        setEditing(null);
        await load(url).catch(() => undefined);
      } else {
        setError(e instanceof ApiError ? e.message : "Saving failed.");
      }
      return false;
    }
  }, [load, url]);

  const save = useCallback((txn: Txn) => write(async () => {
    const revision = await upsertTransaction(url, txn);
    replaceTxn({ ...txn, revision }, txn.id);
  }), [write, url, replaceTxn]);

  const remove = useCallback((txn: Txn) => write(async () => {
    await deleteTransactions(url, [txn.id]);
    replaceTxn(null, txn.id);
  }), [write, url, replaceTxn]);

  const toggleBookmark = useCallback((txn: Txn) => {
    const flipped = { ...txn, bookmarked: !txn.bookmarked };
    // Show the star at once; put it back if the script refuses.
    replaceTxn(flipped, txn.id);
    void write(async () => {
      try {
        const revision = await upsertTransaction(url, flipped);
        replaceTxn({ ...flipped, revision }, txn.id);
      } catch (e) {
        replaceTxn(txn, txn.id);
        throw e;
      }
    });
  }, [write, url, replaceTxn]);

  const store = useMemo<Store>(() => ({
    url, data, booting, refreshing, error, notice,
    canWrite: (data?.scriptVersion ?? 0) >= REQUIRED_SCRIPT_VERSION,
    connect, disconnect, refresh, save, remove, toggleBookmark,
    dismiss: () => {
      setError("");
      setNotice("");
    },
    editing,
    edit: (txn) => setEditing({ txn, isNew: false }),
    add: (preset) => setEditing({ txn: blankTxn(preset), isNew: true }),
    closeEditor: () => setEditing(null),
    filter, setFilter,
  }), [url, data, booting, refreshing, error, notice, connect, disconnect, refresh, save, remove, toggleBookmark, editing, filter]);

  return <Context.Provider value={store}>{children}</Context.Provider>;
}

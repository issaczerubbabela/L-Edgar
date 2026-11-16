// The last Sheet read, kept in IndexedDB so the app opens instantly and still shows data offline.
// Every call fails soft: a private window or blocked storage just means no cache.

import type { Snapshot } from "./types";

const DB = "ledgar-web";
const STORE = "kv";
const KEY = "snapshot";

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(STORE);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function run<T>(mode: IDBTransactionMode, work: (store: IDBObjectStore) => IDBRequest<T>): Promise<T | undefined> {
  try {
    const db = await open();
    return await new Promise<T>((resolve, reject) => {
      const request = work(db.transaction(STORE, mode).objectStore(STORE));
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  } catch {
    return undefined;
  }
}

export const loadSnapshot = () => run<Snapshot | undefined>("readonly", (s) => s.get(KEY));
export const saveSnapshot = (snapshot: Snapshot) => run("readwrite", (s) => s.put(snapshot, KEY));
export const clearSnapshot = () => run("readwrite", (s) => s.delete(KEY));

const read = (name: string) => {
  try {
    return localStorage.getItem(name);
  } catch {
    return null;
  }
};
const write = (name: string, value: string | null) => {
  try {
    if (value === null) localStorage.removeItem(name);
    else localStorage.setItem(name, value);
  } catch {
    /* storage blocked: the setting lasts for this visit only */
  }
};

export const settings = {
  getUrl: () => read("ledgar.scriptUrl") ?? "",
  setUrl: (url: string | null) => write("ledgar.scriptUrl", url),
  getTheme: () => read("ledgar.theme") ?? "auto",
  setTheme: (theme: string) => write("ledgar.theme", theme),
};

// Keeps the app itself (page, script, styles, icons) available offline, so it opens from the home
// screen with no connection and shows the last copy of the Sheet from IndexedDB. Calls to the Apps
// Script are on another origin and are never touched here: they always go to the network.

const CACHE = "ledgar-shell-v1";
// Match on the address alone: a host that answers with "Vary: Origin" would otherwise make the copy
// stored at install look different from the one the page asks for.
const LOOSE = { ignoreVary: true, ignoreSearch: true };

// The first visit loads the page before this worker is in charge, so its script and stylesheet
// never pass through "fetch" below. Read their names out of the page and store them here instead.
async function precache() {
  const cache = await caches.open(CACHE);
  const page = await fetch("./", { cache: "no-store" });
  const html = await page.clone().text();
  await cache.put("./", page);
  const assets = [...html.matchAll(/(?:src|href)="(\.\/[^"]+)"/g)].map((match) => match[1]);
  await Promise.all([...new Set(assets)].map((url) => cache.add(url).catch(() => undefined)));
}

self.addEventListener("install", (event) => {
  event.waitUntil(precache().then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((names) => Promise.all(names.filter((name) => name !== CACHE).map((name) => caches.delete(name)))).then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET" || new URL(request.url).origin !== self.location.origin) return;
  const store = (response) => {
    if (response.ok) {
      const copy = response.clone();
      caches.open(CACHE).then((cache) => cache.put(request, copy));
    }
    return response;
  };
  // Built files carry a hash in their name, so a cached copy is never out of date. Everything else
  // (the page itself) is fetched fresh when online, so a new deploy shows up on the next open.
  if (new URL(request.url).pathname.includes("/assets/")) {
    event.respondWith(caches.match(request, LOOSE).then((hit) => hit || fetch(request).then(store)));
  } else {
    event.respondWith(fetch(request).then(store).catch(() => caches.match(request, LOOSE).then((hit) => hit || caches.match("./", LOOSE))));
  }
});

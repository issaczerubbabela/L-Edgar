import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { settings } from "./cache";
import { StoreProvider } from "./store";
import { applyTheme } from "./views/Settings";
import "./styles.css";

applyTheme(settings.getTheme());

// Offline opening. Left off in development, where a cached page would hide fresh edits.
if (import.meta.env.PROD && "serviceWorker" in navigator) {
  window.addEventListener("load", () => void navigator.serviceWorker.register("./sw.js").catch(() => undefined));
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <StoreProvider>
      <App />
    </StoreProvider>
  </StrictMode>,
);

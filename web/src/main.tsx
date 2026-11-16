import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { settings } from "./cache";
import { StoreProvider } from "./store";
import { applyTheme } from "./views/Settings";
import "./styles.css";

applyTheme(settings.getTheme());

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <StoreProvider>
      <App />
    </StoreProvider>
  </StrictMode>,
);

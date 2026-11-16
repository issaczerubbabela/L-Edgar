import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// A relative base lets the built site be served from any path (GitHub Pages project sites included).
export default defineConfig({
  base: "./",
  plugins: [react()],
});

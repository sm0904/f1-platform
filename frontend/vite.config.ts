import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In development, /api is proxied to the Dropwizard API, so the browser sees one origin and no CORS
// configuration is needed. The API runs on port 8080 (see docs/API.md).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: "http://localhost:8080", changeOrigin: false },
    },
  }
});

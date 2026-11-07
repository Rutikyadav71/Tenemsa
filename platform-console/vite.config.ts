import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Its own port, so it never collides with the main site (5173).
export default defineConfig({
  plugins: [react()],
  server: { port: 5174, strictPort: true },
});

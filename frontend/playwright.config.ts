import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  retries: 0,
  workers: 1,
  use: { baseURL: "http://127.0.0.1:14178", browserName: "chromium" },
  webServer: {
    command: "npm run dev -- --host 127.0.0.1 --port 14178 --strictPort",
    url: "http://127.0.0.1:14178",
    reuseExistingServer: false,
  },
});

import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 120 * 1000,
  use: {
    baseURL: "http://localhost:8100/api",
  },
  retries: 0,
});

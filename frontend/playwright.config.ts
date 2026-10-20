import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests run against the full stack (nginx + frontend + backend + Postgres), e.g.
 * `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --wait`, then
 * `BASE_URL=http://localhost:8080 npm run test:e2e`. BASE_URL must equal the stack's APP_BASE_URL
 * (the backend's Origin check). Use a fresh database: the first test runs first-run setup.
 */
const baseURL = process.env.BASE_URL ?? "http://localhost:8080";

export default defineConfig({
  testDir: "./e2e",
  // The flows share one database (setup happens once), so they run in order.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL,
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});

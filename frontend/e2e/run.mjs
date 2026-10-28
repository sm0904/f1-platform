// Browser test for the dashboard. Serves the production build, mocks the API with committed fixtures
// (so no database is needed), and checks the prediction page.
//
// Run from the frontend folder:  npm run e2e
// First time only:               npx playwright install chromium
import { spawn } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const here = dirname(fileURLToPath(import.meta.url));
const PORT = 4173;
const URL = `http://127.0.0.1:${PORT}`;
const prediction = readFileSync(join(here, "fixtures-prediction.json"), "utf8");
const seasons = readFileSync(join(here, "fixtures-seasons.json"), "utf8");

const failures = [];
function check(name, ok, detail = "") {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? `  (${detail})` : ""}`);
  if (!ok) failures.push(name);
}

const preview = spawn("npx", ["vite", "preview", "--host", "127.0.0.1", "--port", String(PORT), "--strictPort"], {
  cwd: join(here, ".."), stdio: "ignore",
});
const stop = () => preview.kill();

try {
  // Wait for the preview server.
  for (let i = 0; i < 60; i++) {
    try { if ((await fetch(URL)).ok) break; } catch { /* not up yet */ }
    await new Promise((r) => setTimeout(r, 250));
  }

  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1100, height: 900 } });
  const consoleErrors = [];
  page.on("pageerror", (e) => consoleErrors.push(e.message));
  page.on("console", (m) => { if (m.type() === "error") consoleErrors.push(m.text()); });
  await page.route("**/api/v1/**", (route) => {
    const u = route.request().url();
    const body = u.includes("/predictions/") ? prediction : u.endsWith("/seasons") ? seasons : "[]";
    route.fulfill({ status: 200, contentType: "application/json", body });
  });

  await page.goto(URL, { waitUntil: "networkidle" });
  await page.getByRole("button", { name: "Prediction" }).click();
  await page.waitForSelector(".recharts-bar-rectangle");

  // 1. The first view already shows the final bars (regression check for the animation bug).
  const barsAt = () => page.$$eval(".recharts-bar-rectangle path", (els) => els.map((e) => e.getAttribute("d")));
  const first = await barsAt();
  await page.waitForTimeout(2500);
  const settled = await barsAt();
  check("chart bars are final at first paint", JSON.stringify(first) === JSON.stringify(settled), `${first.length} bars`);

  // 2. Verstappen's win and podium values come through unchanged.
  const top = await page.locator("table tbody tr").first().innerText();
  check("table shows Verstappen at 20.5% win, 53.7% podium", top.includes("Verstappen") && top.includes("20.5%") && top.includes("53.7%"), top.replace(/\s+/g, " "));

  // 3. The caveats are shown.
  const callout = await page.locator(".callout").innerText();
  check("pre-qualifying caveat is shown", callout.includes("Pre-qualifying") && callout.includes("Retirements are not modelled"));

  // 4. Chart has the expected number of drivers (top ten).
  check("chart shows the top ten drivers", first.length === 20, `${first.length} bars for 10 drivers`);

  // 5. Standings page renders from the mocked season and has no console errors.
  await page.getByRole("button", { name: "Standings" }).click();
  await page.waitForSelector("text=Championship standings");

  check("no console errors", consoleErrors.length === 0, consoleErrors.join("; ").slice(0, 200));

  await page.locator(".chart").first().screenshot({ path: join(here, "..", "e2e-prediction-chart.png") }).catch(() => {});
  await browser.close();
} finally {
  stop();
}

console.log(failures.length === 0 ? "\nAll browser checks passed." : `\n${failures.length} browser check(s) failed.`);
process.exit(failures.length === 0 ? 0 : 1);

#!/usr/bin/env bash
# Runs every automated check: Java tests (needs JDK 21, Maven, Docker), the dashboard type check,
# unit tests, the production build, and the browser test. Stops at the first failure.
set -euo pipefail
cd "$(dirname "$0")/.."

step() { printf '\n==> %s\n' "$1"; }

step "Java tests (27 across domain, ingestion, prediction, api)"
mvn -q -B test

step "Dashboard: install, type check, unit tests, build"
cd frontend
npm ci --no-audit --no-fund
npm run typecheck
npm test
npm run build

step "Dashboard: browser test (first install of the browser: npx playwright install chromium)"
npm run e2e

printf '\nAll checks passed.\n'

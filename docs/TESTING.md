# Testing

Every check can be run on your own machine. Nothing needs the live database, except the Java integration tests,
which start their own PostgreSQL container.

## One command

```
bash scripts/test-all.sh
```

Needs JDK 21, Maven 3.9 or later, Node 22 or later, and Docker running. The first browser run also needs
`cd frontend && npx playwright install chromium`, once.

## Individual checks

| What | Command | Needs |
|---|---|---|
| Java tests (27): status mapping, loader, Elo and Plackett–Luce, API endpoints | `mvn -q -B test` | JDK 21, Maven, Docker |
| Dashboard type check | `cd frontend && npm run typecheck` | Node |
| Dashboard unit tests (display helpers) | `cd frontend && npm test` | Node |
| Dashboard production build | `cd frontend && npm run build` | Node |
| Dashboard browser test | `cd frontend && npm run e2e` | Node, Chromium |

## What the browser test covers

`frontend/e2e/run.mjs` serves the production build and replaces the API with committed fixtures, so no
database is needed. It checks:

1. The prediction chart shows its final bars at first paint. This catches the animation bug fixed in `e7dba0e`.
2. Verstappen's row shows 20.5% to win and 53.7% to podium.
3. The pre-qualifying caveat is shown.
4. The chart has ten drivers.
5. The standings page renders, and the browser logs no errors.

It writes a screenshot of the chart to `e2e-prediction-chart.png` in the project root (git-ignored), so you can look at it.

## Testing against the real API

Start the stack as in `docs/RUN.md`, then open http://localhost:3000. The dashboard reads the real stored data there.

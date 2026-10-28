# Dashboard

React and TypeScript on Vite. It reads the read-only F1 API (see `../docs/API.md`).

## Run

1. Start the API on port 8080 (see `docs/API.md`).
2. From this folder:

```
npm install
npm run dev        # http://localhost:5173, with /api proxied to the API
```

## Check

```
npm run typecheck  # TypeScript
npm test           # unit tests for the display helpers
npm run build      # production build into dist/
```

## Pages

- **Standings:** driver and constructor championship for a season, with a points chart and table.
- **Race results:** every race in a season, with the race, sprint, or qualifying results.
- **Prediction:** the stored Austin 2026 pre-qualifying run, with its cutoff, model version, and caveats.

## Browser test

```
npx playwright install chromium   # first time only
npm run e2e
```

See `../docs/TESTING.md` for what it checks.

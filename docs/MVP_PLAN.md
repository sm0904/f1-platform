# MVP Plan: Austin 2026 Verification

Target: a working dashboard, historical insights, and a stored pre-race prediction for the
United States Grand Prix in Austin, 23 to 25 October 2026.
Verification: after the race (25 October), compare the stored prediction against the official
result and record the scores in the repo.

Confirm the race dates against the official F1 calendar before freezing the prediction.

---

## 1. Scope

**In**
- Historical ingestion from Jolpica-F1 into PostgreSQL, using the V1 schema. All seasons from 1950.
- Catalog, standings, and insight read API (Dropwizard, one process).
- Elo rating model in Java, with a pre-race prediction stored with its cutoff timestamp.
- One static dashboard page with charts, served by Dropwizard.
- A verification report: predicted versus actual, scored with log loss, Brier score, and Spearman correlation.

**Out, until after Austin**
- Kafka, OpenF1 live ingestion, telemetry, and SSE.
- Deployment, load tests, authentication, and Kubernetes.
- Frontend frameworks. The dashboard is plain HTML with Chart.js.

**Why this scope:** nothing in the Austin goal needs live data or a message broker. PostgreSQL is the durable store, and the prediction is a batch computation with a timestamp.

---

## 2. Data

| Data | Source | Storage | Size |
|---|---|---|---|
| Seasons, races, circuits, drivers, constructors | Jolpica | V1 tables | Under 10 MB |
| Session results (race, qualifying, sprint) | Jolpica | `session_result` | Under 100 MB |
| Standings snapshots | Computed from results | Derived tables | Small |
| Predictions | Computed by the Elo model | New V2 tables | Small |

Historical volume is small enough that PostgreSQL handles it without partitioning, indexing beyond the V1 design, or object storage.

---

## 3. Schedule

Today is Thursday 15 October. Race weekend starts Friday 23 October. The prediction must be frozen before the first practice session.

| Date | Work | Pass criteria |
|---|---|---|
| Thu 15 – Fri 16 Oct | Local PostgreSQL via Docker; Jolpica client and loader for all seasons; load into V1 tables | Row counts match the source per season. Re-running the loader creates no duplicates. A sample of 2024 and 2025 race winners matches official results. |
| Sat 17 – Sun 18 Oct | Catalog endpoints (seasons, races, results, drivers); standings endpoint | Resource tests pass against Testcontainers PostgreSQL. A 2025 driver standings response matches the official table. |
| Mon 19 – Tue 20 Oct | Insights endpoints: points progression, qualifying-to-race conversion, head-to-head, circuit history | Each insight is checked by hand on one season. Results carry the computation version. |
| Wed 21 Oct | Elo model: ratings from all completed races; V2 prediction tables; prediction run for Austin with cutoff set to the last completed race | Model is backtested on 2024 and 2025 before use. Calibration numbers are recorded. The cutoff is stored and checked by a test. |
| Thu 22 Oct | Dashboard page: standings chart, season view, insight charts, prediction panel | Page loads from the Dropwizard server. Every chart has a matching API call. |
| Fri 23 Oct (morning) | Freeze: tag the commit, export the prediction and its inputs to a file in the repo | The tag exists. The prediction file can be regenerated from the tagged commit. |
| Sun 25 Oct (after race) | Verification: load the official result, score the prediction, write `docs/reports/austin-2026.md` | The report lists the scores, the misses, and what the model got wrong. |

Days already include slack. If a day slips, cut insight endpoints first, then the dashboard styling. Do not cut the prediction freeze or the verification.

---

## 4. Prediction design (Austin)

- **Model:** Elo ratings for drivers, updated after each race. The starting rating for a new season comes from the previous season's final rating. A simple constructor rating is included, because car pace matters more than driver skill in most seasons.
- **Outputs:** win probability and podium probability for each driver, and an expected finishing position.
- **Cutoff:** only races completed before the freeze are used. The cutoff timestamp is stored with the prediction.
- **Baseline:** a naive model that ranks drivers by championship points. The Elo model must beat it on the 2024 and 2025 backtest, or we report that honestly.
- **Expectation:** the model will be imperfect. A mid-season car upgrade or a qualifying surprise will not show up in ratings. Recording those misses is part of the deliverable.

---

## 5. Dashboard (minimum)

1. Driver standings for a chosen season, as a bar chart.
2. Points progression across the season, as a line chart.
3. Qualifying-to-race position change, per race.
4. Austin 2026 prediction: win and podium probabilities, with the cutoff stated on the page.

Each chart is fed by one API endpoint. The page has no build step.

---

## 6. Risks

| Risk | Effect | Mitigation |
|---|---|---|
| Jolpica is slow, rate-limited, or missing recent rounds | Loader stalls | Cache raw responses in the repo's data folder, and re-run from the cache |
| 2026 data incomplete before the freeze | Prediction uses stale ratings | Freeze uses the last completed race and says so on the page |
| Elo underperforms the baseline | Weak prediction | Report it. The honest result is the portfolio result. |
| Time runs short | Missing deliverable | Priorities in section 3: freeze and verification are non-negotiable |

---

## 7. After Austin

Kafka comes next, after the MVP is validated. The plan is an OpenF1 session replay through the same consumers, first on one partition and then on several. Deployment and load testing follow after that.
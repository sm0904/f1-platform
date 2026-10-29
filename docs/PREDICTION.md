# Prediction method and results

Status: the Austin 2026 pre-qualifying prediction (exported 22 October 2026) has been verified against the result.
See `reports/austin-2026.md`. A post-qualifying run is not produced, because the model does not use the grid.

## What is predicted

For one race, the model estimates for each car:

- the probability of winning,
- the probability of a podium finish,
- the expected finishing position.

The model does not yet estimate retirements. Every probability assumes the whole field takes the start.

## How it works

1. **Ratings.** Each driver and each constructor has a rating on the Elo scale (400 points per factor of 10 in
   odds). A car's strength is its driver rating plus its constructor rating.
2. **Updating.** Each race is treated as pairwise outcomes between classified finishers. Each pair updates
   the ratings with K = 32, scaled by the number of cars so one race moves a rating by about K. The driver
   takes the full change, and the constructor takes half of the average change over its classified cars.
   Cars that did not finish do not move ratings in this version.
3. **Season shrink (λ).** At the start of each season, every rating is multiplied by λ. λ = 1 keeps ratings
   fully, and λ = 0 resets them to the average.
4. **Probabilities.** The Plackett–Luce model turns strengths into finishing orders. Each car's chance of
   winning is exact. Podium chances and expected positions come from 20,000 simulated races with a fixed seed,
   so the same inputs always give the same output.

## Baseline

Cars are ranked by championship points before the race. Points include sprint points from the same weekend,
since the sprint happens first. The baseline uses the same Plackett–Luce conversion, with a fixed scale of 0.1
per point. It was not tuned.

## Backtest

The backtest is time-based. Ratings are built in date order. Each race is predicted from the ratings before it,
and the result is applied afterwards. Nothing from a later race is used.

λ was chosen on 2022–2023 only:

| λ | Winner log loss (lower is better) | Brier | Spearman |
|---|---|---|---|
| 0.00 | 2.366 | 0.043 | 0.694 |
| 0.25 | 2.234 | 0.042 | 0.732 |
| 0.50 | 2.079 | 0.041 | 0.732 |
| 0.75 | 1.877 | 0.038 | 0.731 |
| 1.00 | 1.567 | 0.034 | 0.723 |

The baseline scored 2.902 log loss on the same window.

**Chosen λ = 1.00 (decided by the owner, 22 October 2026, from the backtest).** This is full carry-over, not the shrink that was planned. The tuning
window includes the 2022 technical regulation change, and carry-over still scored best there. That is evidence
for carry-over across a regulation change, but it is one change, so it is weak. 2026 is also a regulation change,
so this is the main assumption to watch. λ = 0.75 is a sensible sensitivity check, and the Austin run was
compared against it (see below).

Out-of-sample results, using λ = 1.00:

| Window | Races | Model log loss | Baseline log loss | Model Brier | Baseline Brier | Model Spearman | Baseline Spearman |
|---|---|---|---|---|---|---|---|
| 2024–2025 | 48 | 2.016 | 2.929 | 0.041 | 0.047 | 0.666 | 0.662 |
| 2026 to date | 17 | 2.536 | 3.043 | 0.042 | 0.043 | 0.666 | 0.700 |

**Reading the results.**

- The model beats the points baseline on log loss and Brier score in both windows. The gap is large on log loss.
- On rank correlation the model is level with the baseline for 2024–2025 (0.666 against 0.662), and behind it for
  2026 so far (0.666 against 0.700). The baseline's rank advantage in 2026 is the kind of result to watch as the
  season goes on.
- Calibration is reasonable at low probabilities. The mid-range bins have few races, so their observed rates are
  noisy. In 2026 the 0.1–0.2 bin was too high (predicted 0.128, observed 0.074), which is a sign that the
  model may be overconfident this season.

## Austin 2026 (run id 1)

| Field | Value |
|---|---|
| Target | 2026 round 18, United States Grand Prix, 25 October 2026 |
| Data cutoff | 11 October 2026, the end of round 17 (Singapore) |
| Races used | 1166, all completed races |
| Grid | not known. The field is the 22 cars that raced in Singapore. |
| Model | K = 32, λ = 1.00, Plackett–Luce, 20,000 simulations, seed 2026 |
| Stored | 22 rows in `prediction`, plus the run in `prediction_run` |
| Export | `predictions/austin-2026-pre-qualifying.csv` |

Top of the order: Verstappen (win 20.5%, podium 53.7%, expected position 3.8), Norris (13.5%, 39.2%, 4.9),
Leclerc (11.2%, 33.0%, 5.5), Russell (10.4%, 31.9%, 5.6).

**Sensitivity to λ.** With λ = 0.75, the same model gives Verstappen 13.5% to win, with a flatter field
(the top six are all between 9% and 13.5%). The order of the top four is unchanged. The choice of λ mainly
changes how confident the model is, not who it expects to finish near the front.

**Reproducibility.** The run is deterministic. The win probability is the exact Plackett–Luce value. The podium
probability and expected position come from 20,000 simulated races with seed 2026. The cars are listed in order of
their provider keys, compared as bytes, so the result does not depend on how the database returns rows. The local
database and the container database produce identical rows for all 22 drivers.

**Caveats.** The field assumes all 22 cars start. Drivers who change teams, or who miss the race, are not
modelled. The 2026 regulation change may have moved the true pecking order by more than ratings can capture.
Nothing in the model knows about upgrades, weather, or tyre strategy.

## Freeze and verification

- Each run is stored with its cutoff and seed. A unique key (model version, target, grid status) stops a second run
  from overwriting it.
- The cutoff is enforced in the query: only races dated before the target race are read. A run cannot see later results.
- The Austin run was exported to `predictions/austin-2026-pre-qualifying.csv` on 22 October, before the race. The
  database was rebuilt on 29 October. The rebuilt run matches that export row for row.

## Verification

Each race is scored against the official result with the same measures as the backtest: winner log loss and Brier
score over the full field, Spearman correlation over classified finishers, and the points baseline for comparison.
The Austin 2026 report is in `docs/reports/austin-2026.md`.

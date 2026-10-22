# Prediction method and results

Status: Austin 2026 pre-qualifying prediction stored (run id 1). Post-qualifying run planned for Saturday 24 October.

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
| 0.00 | 2.366 | 0.043 | 0.706 |
| 0.25 | 2.234 | 0.042 | 0.732 |
| 0.50 | 2.079 | 0.041 | 0.732 |
| 0.75 | 1.877 | 0.038 | 0.731 |
| 1.00 | 1.567 | 0.034 | 0.723 |

The baseline scored 2.902 log loss on the same window.

**Chosen λ = 1.00, the data's choice.** This is full carry-over, not the shrink that was planned. The tuning
window includes the 2022 technical regulation change, and carry-over still scored best there. That is evidence
for carry-over across a regulation change, but it is one change, so it is weak. 2026 is also a regulation change,
so this is the main assumption to watch. λ = 0.75 is a sensible sensitivity check, and the Austin run was
compared against it (see below).

Out-of-sample results, using λ = 1.00:

| Window | Races | Model log loss | Baseline log loss | Model Brier | Baseline Brier | Model Spearman | Baseline Spearman |
|---|---|---|---|---|---|---|---|
| 2024–2025 | 48 | 2.016 | 2.929 | 0.041 | 0.047 | 0.666 | 0.667 |
| 2026 to date | 17 | 2.536 | 3.043 | 0.042 | 0.043 | 0.666 | 0.728 |

**Reading the results.**

- The model beats the points baseline on log loss and Brier score in both windows. The gap is large on log loss.
- On rank correlation the model ties the baseline for 2024–2025 and falls behind it for 2026 so far.
  The baseline's rank advantage in 2026 is the kind of result to watch as the season goes on.
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

Top of the order: Verstappen (win 20.6%, podium 53.6%, expected position 3.8), Norris (13.5%, 39.4%, 4.9),
Leclerc (11.2%, 33.6%, 5.4), Russell (10.7%, 31.4%, 5.6).

**Sensitivity to λ.** With λ = 0.75, the same model gives Verstappen 13.9% to win, with a flatter field
(the top five are all between 9% and 14%). The order of the top four is unchanged. The choice of λ mainly
changes how confident the model is, not who it expects to finish near the front.

**Caveats.** The field assumes all 22 cars start. Drivers who change teams, or who miss the race, are not
modelled. The 2026 regulation change may have moved the true pecking order by more than ratings can capture.
Nothing in the model knows about upgrades, weather, or tyre strategy.

## Freeze procedure

- The run is stored with its cutoff and seed. The unique key (model version, target, grid status) stops any
  second run from overwriting it. A changed model needs a new version, so the stored run is never changed.
- On Friday 23 October, before first practice: tag the commit that produced the run, then re-export the CSV.
- On Saturday 24 October, after qualifying: store a second run with `grid_known = true`, using the grid as
  the field order. It is stored separately, and both runs are kept for the verification report.

## Verification (after the race, 25 October)

Score the stored runs against the official result: log loss and Brier score for the winner, podium accuracy,
and Spearman correlation for the finishing order. Compare against the baseline on the same race. Record the
misses in `docs/reports/austin-2026.md`.

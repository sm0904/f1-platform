# Verification: United States Grand Prix 2026 (Austin, round 18)

Race date: 25 October 2026. Verification written 29 October 2026.

## What was predicted

- **Prediction:** Elo ratings with Plackett–Luce probabilities, model version `elo-k32-lambda1.0`, pre-qualifying,
  with the data cutoff at round 17 (Singapore, 11 October 2026).
- **Where it was stored:** the export `predictions/austin-2026-pre-qualifying.csv` was made on 22 October 2026, before
  the race. The database was reset later, so the record in the database was rebuilt on 29 October from the same
  code and data.
- **Reproduction:** rebuilding the run from races before 25 October gives all 22 rows identical to the 22 October
  export, to the precision the export stores. The rebuild is therefore the same prediction, not a new one.

## What happened

Winner: **Antonelli** (Mercedes). Podium: Antonelli, Leclerc, Verstappen. Three cars did not finish:
Bearman, Colapinto, Bottas.

## Scores

Scored over the full field of 22 for win probability. Rank correlation is over the 19 classified finishers.

| Measure | Model | Points baseline |
|---|---|---|
| Probability given to the winner (Antonelli) | 7.3% | 5.2% |
| Log loss for the winner (lower is better) | 2.610 | 2.948 |
| Brier score (lower is better) | 0.0437 | 0.0428 |
| Spearman (predicted order vs finishing order) | 0.895 | 0.930 |
| Top three predicted and actual | 2 of 3 right | - |
| Mean absolute error of expected position | 2.13 places | - |

The baseline is the same Plackett–Luce conversion applied to championship points before the race, as in the backtest.

**Reading the scores.**

- The model gives the winner a better probability than the baseline does, as measured by log loss.
- The Brier score and the rank correlation are slightly worse than the baseline.
- The winner was ranked seventh by win probability. The model did not expect the result.
- The top three were predicted in the wrong order: Verstappen first, then Norris, then Leclerc. Norris finished fifth.

**This is one race.** No conclusion about the model's quality can be drawn from one race. The backtest in
`docs/PREDICTION.md` covers 2024 and 2025 (48 races) and the 2026 races up to round 17.

## Predicted against actual

| Pred. rank | Driver | Expected pos | Win % | Actual |
|---|---|---|---|---|
| 1 | Verstappen | 3.81 | 20.5% | 3 |
| 2 | Norris | 4.87 | 13.5% | 5 |
| 3 | Leclerc | 5.45 | 11.2% | 2 |
| 4 | Russell | 5.61 | 10.4% | 8 |
| 5 | Hamilton | 6.06 | 9.0% | 4 |
| 6 | Piastri | 6.19 | 8.8% | 7 |
| 7 | Antonelli | 6.77 | 7.3% | 1 |
| 8 | Hadjar | 8.38 | 4.6% | 6 |
| 9 | Lawson | 12.29 | 1.7% | 9 |
| 10 | Hülkenberg | 12.64 | 1.6% | 11 |
| 11 | Lindblad | 12.98 | 1.5% | 12 |
| 12 | Gasly | 13.36 | 1.3% | 10 |
| 13 | Alonso | 14.00 | 1.2% | 16 |
| 14 | Bearman | 14.55 | 1.0% | did not finish |
| 15 | Sainz | 14.67 | 1.0% | 18 |
| 16 | Bortoleto | 14.76 | 1.0% | 13 |
| 17 | Ocon | 15.05 | 0.9% | 14 |
| 18 | Colapinto | 15.11 | 0.9% | did not finish |
| 19 | Albon | 15.44 | 0.8% | 17 |
| 20 | Stroll | 16.35 | 0.6% | 15 |
| 21 | Pérez | 16.70 | 0.6% | 19 |
| 22 | Bottas | 17.95 | 0.4% | did not finish |

## What the model got wrong, and why

- **Retirements were not modelled.** The model assumed every car finishes, so the expected positions of cars that
  retired are biased upwards. Bearman, Colapinto, and Bottas are all predicted in the bottom half and did not finish.
- **Antonelli was underrated.** His rating was far below the front-runners, so the model put him seventh on win
  probability, and he won.
- **Norris and Verstappen were overrated relative to the result.** This race is one data point. It doesn't show
  whether either is a systematic error.
- **Nothing in the model knows about 2026 car development.** The ratings come from earlier results. A regulation
  year with changing performance will reward a model that adapts quickly. This one does not.

## What this report does not cover

- The post-qualifying prediction. The model does not use the starting grid, so a post-qualifying run would be the
  same prediction. It was not produced.
- Retirement probabilities, sprint results in the ratings, and tie-breaking by countback. All are listed as open
  limitations in `docs/PREDICTION.md`.

## Next steps

1. Model retirements as a separate, constructor-level probability, then re-run the backtest.
2. Decide whether sprint results should update the ratings, and test that in the backtest.
3. Use the grid in the model, and test it on the historical races that have grids.

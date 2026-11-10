# Model evaluation protocol

Status: the protocol is in place. The numbers below come from `EvaluationReport` on the full history.
The full printed output is in `docs/reports/validation-run-2026-10-29.txt`.

## What is being predicted

The finishing order of the classified finishers in each race. Retirements are not part of the ordering, because
they are not evidence of pace. Retirements are tracked separately (see below).

## Data

All race results from 1950 to 2026 (1,167 races), read in date order. The model only sees results from races
before the one it predicts.

## Measures

- **Plackett–Luce log likelihood of the observed order (higher is better).** This is the proper scoring rule for
  an ordering model. The model gives each car a strength; the likelihood is the probability the model assigns to
  the actual finishing order. Uniform guessing scores −log(n!). A value of −31 means a race with 22 classified
  finishers is scored about e^−31 per ordering, relative to the uniform benchmark of −35.5 for the same races.
  Differences between models are what matter, and they are reported as nats per race (natural log units).
- **Spearman rank correlation (1 is a perfect ordering, 0 is none).** It measures how closely the predicted order
  follows the finishing order. It ignores how confident the model was. Where every car has the same score
  (for example, zero points at the start of a season), the correlation is reported as 0, because a constant
  ranking carries no information.
- **Retirement rate and reason (descriptive).** Counts of retirements by season and by reason group, from the
  provider's status text. These are not used in any strength.

Why both likelihood and Spearman: the likelihood scores the whole distribution of the order, so it rewards
calibrated probability across the field. Spearman checks only the ordering. A model can improve one without the
other, and the results below show that happening.

## Baselines

- **Uniform:** no information.
- **Points:** championship points before the race, starting from zero each season, sprint points included.
- **Previous season:** the previous season's final points, held fixed for the whole season. This is the
  information a fan has when a season starts. It is the fairer benchmark for the start of a season.

All three are converted to strengths with the same Plackett–Luce formula. Each baseline's scale is tuned
separately on the tuning window, so no baseline is handicapped by a poor default.

## Protocol

1. **Tuning window, 2014–2021.** Choose the model's K (rating update size) and λ (season shrink), and each
   baseline's scale, by the highest mean likelihood.
2. **Test window, 2022–2026.** Score every race with the chosen values, using only earlier races.
3. **Uncertainty.** 95% intervals from a bootstrap that resamples whole seasons (5,000 resamples, fixed seed). It
   keeps races within a season together, because they are correlated.

Grids were widened until every chosen value fell inside its grid, rather than at the edge of it.

## Results

Chosen values: K = 128, λ = 0.75, points scale 3.00, previous-season scale 1.50.

| Comparison (test window, 110 races) | Difference | 95% interval |
|---|---|---|
| Likelihood: model minus points | +1.47 nats per race | +0.88 to +1.96 |
| Likelihood: model minus previous season | +2.37 nats per race | +1.38 to +3.34 |
| Likelihood: model minus uniform | +4.22 nats per race | +4.00 to +4.43 |
| Spearman: model minus points | +0.036 | +0.023 to +0.046 |

Mean Spearman: model 0.718, points 0.683, previous season 0.589.

By season, the model is ahead of points on likelihood in every test season. It is also ahead on Spearman in every
test season, by between 0.016 (2024) and 0.051 (2025).

**Reading these numbers.**

- The likelihood gain over the points baseline is large and clearly above zero.
- The Spearman gain is small. The interval excludes zero, but the effect is modest. The model orders cars only
  slightly better than points do.
- The model's advantage on likelihood is larger than on Spearman. That means most of its gain is in the
  probabilities, not in the order.

## Retirements

Retirements are about 28% accident or collision, 26% engine or power unit, and 16% gearbox or transmission over
all seasons. Only 0.3% are unclassified, so the grouping covers nearly all cases.

**Data gap.** From 2023 onward most retirements carry no reason ("Retired", or a lap count). The reason is
unavailable for those seasons, so reason-level tracking is effectively unavailable for the recent races, and any
analysis of recent retirement causes would be incomplete. The retirement rate itself is still recorded.

## Limits

1. **The test window has been used before.** During this session I changed the protocol and the grids after
   seeing results. Each change was a choice made with the test numbers in view, so the test numbers are
   optimistic. The honest test is prospective: races scored after their forecasts are stored. The Mexico City
   forecast (`predictions/mexico-city-2026-pre-qualifying.csv`) is the first of these. Its result will be scored
   with the same measures.
2. **One era of model structure.** The model has no grid, no qualifying, no weather, and no circuit information.
   It is a rating model, and the results describe that model only.
3. **Retirements are not in the probabilities yet.** The likelihood is conditional on the classified finishers.
   Predicting who finishes, and how likely each retirement reason is, is a separate model that is not built yet.
4. **Multiple comparisons.** Several settings were compared on the tuning window. The interval does not account for
   that selection.

## Planned next steps

- Score the Mexico City forecast when the result is in, with the same measures.
- Add the non-finish probability as a separate, league-level component, with its own uncertainty, and keep it out
  of the strengths, as agreed.
- Add the grid as a model input, and test it on historical races with grids.

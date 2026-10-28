import { useState } from "react";
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, type PredictionRun } from "../api";
import { Status } from "../components/Status";
import { useAsync } from "../useAsync";
import { formatDate, formatExpectedPosition, formatPercent } from "../format";

/** The target race for the stored prediction. Austin 2026 is round 18. */
const TARGET = { year: 2026, round: 18 };

export function PredictionPage() {
  const runs = useAsync(() => api.predictions(TARGET.year, TARGET.round), [TARGET.year, TARGET.round]);
  const [chosenId, setChosenId] = useState<number | null>(null);
  const run: PredictionRun | undefined =
    runs.data?.find((r) => r.id === chosenId) ?? runs.data?.[0];

  return (
    <section>
      <header className="page-header">
        <h2>Prediction</h2>
      </header>

      <Status loading={runs.loading} error={runs.error} empty={runs.data?.length === 0} emptyText="No stored prediction for this race yet." />

      {run && (
        <>
          {runs.data && runs.data.length > 1 && (
            <label className="field">
              Run
              <select value={run.id} onChange={(e) => setChosenId(Number(e.target.value))}>
                {runs.data.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.modelVersion} · {r.gridKnown ? "post-qualifying" : "pre-qualifying"} · {formatDate(r.createdAt.slice(0, 10))}
                  </option>
                ))}
              </select>
            </label>
          )}

          <div className="callout">
            <p>
              <strong>{run.targetName}</strong>, {formatDate(run.targetDate)}.{" "}
              {run.gridKnown ? "Starting grid known." : "Pre-qualifying: the grid is not known."}
            </p>
            <p className="muted">
              Built from {run.inputRaces.toLocaleString("en-GB")} completed races, with the last race ending on{" "}
              {formatDate(run.dataCutoffDate)}. Model {run.modelVersion}, {run.simulations.toLocaleString("en-GB")} simulated
              races, seed {run.randomSeed}. Stored {formatDate(run.createdAt.slice(0, 10))}.
            </p>
            <p className="muted">
              Probabilities assume every car starts the race. Retirements are not modelled, and the model knows nothing
              about upgrades, weather, or tyre strategy.
            </p>
          </div>

          <div className="chart">
            <p className="muted">Top ten by expected finish. The full field is in the table below.</p>
            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={run.predictions.slice(0, 10).map((p) => ({ name: p.familyName, win: p.winProb * 100, podium: p.podiumProb * 100 }))} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" interval={0} />
                <YAxis unit="%" />
                <Tooltip formatter={(v) => `${Number(v).toFixed(1)}%`} />
                <Legend />
                <Bar dataKey="win" name="Win" fill="#e10600" isAnimationActive={false} />
                <Bar dataKey="podium" name="Podium" fill="#1e41ff" isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          <table>
            <thead>
              <tr><th>Expected pos</th><th>Driver</th><th>Team</th><th>Win</th><th>Podium</th></tr>
            </thead>
            <tbody>
              {run.predictions.map((p) => (
                <tr key={`${p.familyName}-${p.constructor}`}>
                  <td>{formatExpectedPosition(p.expectedPosition)}</td>
                  <td>{p.givenName} {p.familyName}</td>
                  <td>{p.constructor}</td>
                  <td>{formatPercent(p.winProb)}</td>
                  <td>{formatPercent(p.podiumProb)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}

import { useState } from "react";
import { api, type PredictionRun } from "../api";
import { Status } from "../components/Status";
import { useAsync } from "../useAsync";
import { formatDate, formatExpectedPosition, formatPercent } from "../format";
import { teamColor } from "../teamColors";
import { getDriverHeadshot } from "../driverMeta";

const TARGETS = [
  { year: 2026, round: 21, label: "Las Vegas (R21)" },
  { year: 2026, round: 19, label: "Mexico City (R19)" },
  { year: 2026, round: 18, label: "Austin (R18)" },
];

export function PredictionPage() {
  const [target, setTarget] = useState(TARGETS[0]);
  const runs = useAsync(() => api.predictions(target.year, target.round), [target.year, target.round]);
  const [chosenId, setChosenId] = useState<number | null>(null);
  const run: PredictionRun | undefined =
    runs.data?.find((r) => r.id === chosenId) ?? runs.data?.[0];

  const maxWin = run ? Math.max(...run.predictions.map((p) => p.winProb)) : 1;

  return (
    <section>
      <header className="page-header">
        <h2>Race prediction</h2>
        <label className="field">
          Race
          <select
            value={target.round}
            onChange={(e) => { setTarget(TARGETS.find((t) => t.round === Number(e.target.value))!); setChosenId(null); }}
          >
            {TARGETS.map((t) => <option key={t.round} value={t.round}>{t.label}</option>)}
          </select>
        </label>
      </header>

      <Status loading={runs.loading} error={runs.error} empty={runs.data?.length === 0} emptyText="No stored prediction for this race yet." />

      {run && (
        <>
          {runs.data && runs.data.length > 1 && (
            <label className="field" style={{ marginBottom: "0.75rem" }}>
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

          <div className="card-grid">
            <div className="stat-card">
              <span className="stat-label">Favourite</span>
              <span className="stat-value" style={{ color: teamColor(run.predictions[0]?.constructor ?? "") }}>
                {run.predictions[0]?.familyName ?? "-"}
              </span>
              <span className="stat-sub">{formatPercent(run.predictions[0]?.winProb ?? 0)} to win</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Race date</span>
              <span className="stat-value" style={{ fontSize: "1.2rem" }}>{formatDate(run.targetDate)}</span>
              <span className="stat-sub">{run.targetName}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Data</span>
              <span className="stat-value" style={{ fontSize: "1.2rem" }}>{run.inputRaces.toLocaleString("en-GB")}</span>
              <span className="stat-sub">races analysed (cutoff {formatDate(run.dataCutoffDate)})</span>
            </div>
          </div>

          <div className="callout">
            <p className="muted" style={{ fontSize: "0.82rem" }}>
              {run.gridKnown ? "Grid known." : "Pre-qualifying: grid not known."}{" "}
              Model {run.modelVersion}, {run.simulations.toLocaleString("en-GB")} simulations.
              Assumes every car starts. Does not model retirements, upgrades, weather, or tyre strategy.
            </p>
          </div>

          <h3 className="section-title">Full field</h3>

          <div className="prediction-grid">
            {run.predictions.map((p, i) => {
              const code = p.familyName.slice(0, 3).toUpperCase();
              const headshot = getDriverHeadshot(code);
              const color = teamColor(p.constructor);
              return (
                <div key={`${p.familyName}-${p.constructor}`} className="prediction-row">
                  <span className={`rank ${i < 3 ? "top3" : ""}`}>{i + 1}</span>
                  <div>
                    {headshot ? (
                      <img className="driver-avatar" src={headshot} alt="" loading="lazy" style={{ border: `2px solid ${color}` }} />
                    ) : (
                      <div className="driver-avatar" style={{ border: `2px solid ${color}`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: "0.7rem", color: "var(--fg-muted)" }}>
                        {code}
                      </div>
                    )}
                  </div>
                  <div>
                    <div className="driver-cell" style={{ gap: "0.3rem" }}>
                      <div className="driver-name">
                        <span className="family">{p.familyName}</span>
                        <span className="given">{p.givenName} · <span className="team-badge"><span className="team-dot" style={{ background: color }} />{p.constructor}</span></span>
                      </div>
                    </div>
                  </div>
                  <div className="prob-bar-container">
                    <div className="prob-label">
                      <span>Win {formatPercent(p.winProb)}</span>
                      <span>Podium {formatPercent(p.podiumProb)}</span>
                    </div>
                    <div className="prob-bar">
                      <div className="prob-bar-fill" style={{ width: `${(p.winProb / maxWin) * 100}%`, background: color }} />
                    </div>
                  </div>
                  <div style={{ textAlign: "right", minWidth: 48 }}>
                    <span style={{ fontSize: "0.78rem", color: "var(--fg-muted)" }}>E.Pos</span>
                    <div style={{ fontWeight: 700, fontSize: "0.95rem" }}>{formatExpectedPosition(p.expectedPosition)}</div>
                  </div>
                </div>
              );
            })}
          </div>
        </>
      )}
    </section>
  );
}

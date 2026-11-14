import { useState } from "react";
import { CartesianGrid, ResponsiveContainer, Scatter, ScatterChart, Tooltip, XAxis, YAxis, ReferenceLine } from "recharts";
import { api, type GridFinishRow, type SeasonSummary } from "../api";
import { SeasonPicker } from "../components/SeasonPicker";
import { Status } from "../components/Status";
import { useAsync } from "../useAsync";
import { formatDate } from "../format";
import { teamColor } from "../teamColors";
import { getDriverHeadshot } from "../driverMeta";

interface Props { seasons: SeasonSummary[] }

export function GridPage({ seasons }: Props) {
  const latest = seasons[seasons.length - 1]?.year ?? 2024;
  const [year, setYear] = useState(latest);
  const races = useAsync(() => api.races(year), [year]);
  const [round, setRound] = useState<number | null>(null);
  const selected = races.data?.find((r) => r.round === round) ?? races.data?.[races.data.length - 1];
  const activeRound = selected?.round ?? null;
  const grid = useAsync(
    () => (activeRound ? (api as any).gridVsFinish(year, activeRound) as Promise<GridFinishRow[]> : Promise.resolve([])),
    [year, activeRound],
  );

  const chartData = (grid.data ?? []).map((d) => ({
    name: d.driverCode ?? d.familyName,
    x: d.grid,
    y: d.finish,
    gained: d.gained,
    constructor: d.constructor,
    color: teamColor(d.constructor),
  }));
  const maxPos = Math.max(...chartData.map((d) => Math.max(d.x, d.y)), 20);

  const bigGainers = [...chartData].sort((a, b) => b.gained - a.gained).slice(0, 3);
  const bigLosers = [...chartData].sort((a, b) => a.gained - b.gained).slice(0, 3);

  return (
    <section>
      <header className="page-header">
        <h2>Grid vs finish</h2>
        <SeasonPicker seasons={seasons} value={year} onChange={(y) => { setYear(y); setRound(null); }} />
      </header>

      <Status loading={races.loading} error={races.error} />

      {races.data && races.data.length > 0 && (
        <>
          <div className="race-picker" role="tablist">
            {races.data.map((r) => (
              <button key={r.round} type="button" className={r.round === activeRound ? "chip active" : "chip"}
                      onClick={() => setRound(r.round)}>R{r.round}</button>
            ))}
          </div>

          {selected && (
            <div className="card" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "0.75rem" }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: "1.1rem" }}>{selected.name}</div>
                <div style={{ color: "var(--fg-secondary)", fontSize: "0.88rem" }}>
                  {selected.circuit} · {formatDate(selected.date)}
                </div>
              </div>
            </div>
          )}

          <Status loading={grid.loading} error={grid.error} empty={grid.data?.length === 0} emptyText="No grid data for this race." />

          {chartData.length > 0 && (
            <>
              {/* Stat cards: biggest movers */}
              <div className="card-grid">
                <div className="stat-card" style={{ borderLeft: "3px solid var(--green)" }}>
                  <span className="stat-label">Biggest gainer</span>
                  <span className="stat-value" style={{ color: "var(--green)" }}>{bigGainers[0]?.name ?? "-"}</span>
                  <span className="stat-sub">+{bigGainers[0]?.gained ?? 0} positions (P{bigGainers[0]?.x} → P{bigGainers[0]?.y})</span>
                </div>
                <div className="stat-card" style={{ borderLeft: "3px solid var(--accent)" }}>
                  <span className="stat-label">Biggest loser</span>
                  <span className="stat-value" style={{ color: "var(--accent)" }}>{bigLosers[0]?.name ?? "-"}</span>
                  <span className="stat-sub">{bigLosers[0]?.gained ?? 0} positions (P{bigLosers[0]?.x} → P{bigLosers[0]?.y})</span>
                </div>
                <div className="stat-card">
                  <span className="stat-label">Field size</span>
                  <span className="stat-value">{chartData.length}</span>
                  <span className="stat-sub">classified drivers</span>
                </div>
              </div>

              <div className="chart">
                <p className="chart-title">Grid position vs race finish · above diagonal = gained places</p>
                <ResponsiveContainer width="100%" height={420}>
                  <ScatterChart margin={{ top: 16, right: 16, bottom: 24, left: 8 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                    <XAxis type="number" dataKey="x" name="Grid" domain={[1, maxPos]} tick={{ fontSize: 11 }}
                           label={{ value: "Grid position", position: "insideBottom", offset: -12, style: { fill: "var(--fg-muted)", fontSize: 12 } }} />
                    <YAxis type="number" dataKey="y" name="Finish" domain={[1, maxPos]} reversed tick={{ fontSize: 11 }}
                           label={{ value: "Finish position", angle: -90, position: "insideLeft", offset: 4, style: { fill: "var(--fg-muted)", fontSize: 12 } }} />
                    <ReferenceLine segment={[{ x: 1, y: 1 }, { x: maxPos, y: maxPos }]} stroke="var(--fg-muted)" strokeDasharray="4 4" strokeOpacity={0.5} />
                    <Tooltip content={({ payload }) => {
                      if (!payload?.length) return null;
                      const d = payload[0].payload;
                      return (
                        <div style={{ background: "var(--bg-raised)", border: "1px solid var(--border)", borderRadius: 6, padding: "8px 12px", fontSize: "0.88rem" }}>
                          <div style={{ fontWeight: 700, marginBottom: 2 }}>{d.name}</div>
                          <div>Grid P{d.x} → Finish P{d.y}</div>
                          <div style={{ color: d.gained > 0 ? "var(--green)" : d.gained < 0 ? "var(--accent)" : "var(--fg-muted)", fontWeight: 600 }}>
                            {d.gained > 0 ? `+${d.gained}` : d.gained} positions
                          </div>
                        </div>
                      );
                    }} />
                    <Scatter data={chartData} isAnimationActive={false}>
                      </Scatter>
                  </ScatterChart>
                </ResponsiveContainer>
              </div>

              {/* Results table */}
              <h3 className="section-title">Full grid</h3>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th style={{width:48}}>Finish</th><th>Driver</th><th>Team</th><th>Grid</th><th>+/-</th>
                    </tr>
                  </thead>
                  <tbody>
                    {grid.data!.map((d) => {
                      const headshot = getDriverHeadshot(d.driverCode);
                      return (
                        <tr key={`${d.driverCode}-${d.grid}`}>
                          <td>
                            <span className={`pos-badge ${d.finish <= 3 ? `p${d.finish}` : ""}`}>
                              {d.finish}
                            </span>
                          </td>
                          <td>
                            <div className="driver-cell">
                              {headshot && <img className="driver-avatar" src={headshot} alt="" loading="lazy" />}
                              <div className="driver-name">
                                <span className="family">{d.familyName}</span>
                                <span className="given">{d.driverCode}</span>
                              </div>
                            </div>
                          </td>
                          <td>
                            <span className="team-badge">
                              <span className="team-dot" style={{ background: teamColor(d.constructor) }} />
                              {d.constructor}
                            </span>
                          </td>
                          <td>{d.grid}</td>
                          <td>
                            <span className={d.gained > 0 ? "gained-pos" : d.gained < 0 ? "lost-pos" : "same-pos"}>
                              {d.gained > 0 ? `+${d.gained}` : d.gained === 0 ? "—" : d.gained}
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </>
      )}
    </section>
  );
}

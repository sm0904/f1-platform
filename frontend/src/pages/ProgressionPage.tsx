import { useState } from "react";
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, type PointsProgression, type SeasonSummary } from "../api";
import { SeasonPicker } from "../components/SeasonPicker";
import { Status } from "../components/Status";
import { useAsync } from "../useAsync";
import { teamColor } from "../teamColors";
import { formatPoints } from "../format";

interface Props { seasons: SeasonSummary[] }

export function ProgressionPage({ seasons }: Props) {
  const latest = seasons[seasons.length - 1]?.year ?? 2024;
  const [year, setYear] = useState(latest);
  const prog = useAsync(() => (api as any).pointsProgression(year) as Promise<PointsProgression[]>, [year]);

  const rounds = new Map<number, Record<string, unknown>>();
  const drivers: { key: string; name: string; color: string; finalPts: number }[] = [];

  if (prog.data) {
    const sorted = [...prog.data].sort((a, b) => {
      const la = a.rounds[a.rounds.length - 1]?.cumulative ?? 0;
      const lb = b.rounds[b.rounds.length - 1]?.cumulative ?? 0;
      return lb - la;
    });
    sorted.forEach((d) => {
      const key = d.driverCode ?? d.familyName;
      const finalPts = d.rounds[d.rounds.length - 1]?.cumulative ?? 0;
      drivers.push({ key, name: `${d.givenName} ${d.familyName}`, color: teamColor(d.constructor), finalPts });
      for (const r of d.rounds) {
        const row = rounds.get(r.round) ?? { round: r.round, label: `R${r.round}` };
        row[key] = r.cumulative;
        rounds.set(r.round, row);
      }
    });
  }
  const chartData = [...rounds.values()].sort((a, b) => (a.round as number) - (b.round as number));

  // Show top-5 drivers by default, with toggle for all
  const [showAll, setShowAll] = useState(false);
  const visibleDrivers = showAll ? drivers : drivers.slice(0, 8);

  return (
    <section>
      <header className="page-header">
        <h2>{year} Points progression</h2>
        <div style={{ display: "flex", gap: "0.75rem", alignItems: "center" }}>
          <button type="button" className={showAll ? "chip" : "chip active"} onClick={() => setShowAll(false)}>Top 8</button>
          <button type="button" className={showAll ? "chip active" : "chip"} onClick={() => setShowAll(true)}>All drivers</button>
          <SeasonPicker seasons={seasons} value={year} onChange={setYear} />
        </div>
      </header>

      <Status loading={prog.loading} error={prog.error} empty={prog.data?.length === 0} emptyText="No data for this season." />

      {chartData.length > 0 && (
        <>
          {/* Top 3 stat cards */}
          {drivers.length >= 3 && (
            <div className="card-grid">
              {drivers.slice(0, 3).map((d, i) => (
                <div key={d.key} className="stat-card" style={{ borderLeft: `3px solid ${d.color}` }}>
                  <span className="stat-label">{["Leader", "2nd", "3rd"][i]}</span>
                  <span className="stat-value" style={{ color: d.color }}>{d.name}</span>
                  <span className="stat-sub">{formatPoints(d.finalPts)} pts</span>
                </div>
              ))}
            </div>
          )}

          <div className="chart chart-wide">
            <p className="chart-title">Cumulative championship points</p>
            <ResponsiveContainer width="100%" height={480}>
              <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="label" interval={0} tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip
                  contentStyle={{ background: "var(--bg-raised)", border: "1px solid var(--border)", borderRadius: 6 }}
                  labelStyle={{ color: "var(--fg-secondary)" }}
                />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                {visibleDrivers.map((d) => (
                  <Line
                    key={d.key}
                    type="monotone"
                    dataKey={d.key}
                    name={d.name}
                    stroke={d.color}
                    dot={false}
                    strokeWidth={2.5}
                    isAnimationActive={false}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </div>
        </>
      )}
    </section>
  );
}

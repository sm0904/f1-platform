import { useState } from "react";
import { CartesianGrid, ResponsiveContainer, Scatter, ScatterChart, Tooltip, XAxis, YAxis, ReferenceLine } from "recharts";
import { api, type GridFinishRow, type SeasonSummary } from "../api";
import { SeasonPicker } from "../components/SeasonPicker";
import { Status } from "../components/Status";
import { useAsync } from "../useAsync";
import { formatDate } from "../format";

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
    name: d.driverCode ?? d.familyName, x: d.grid, y: d.finish, gained: d.gained,
  }));
  const maxPos = Math.max(...chartData.map((d) => Math.max(d.x, d.y)), 20);

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
          {selected && <p className="note">{selected.name} · {formatDate(selected.date)}</p>}
          <Status loading={grid.loading} error={grid.error} empty={grid.data?.length === 0} emptyText="No grid data for this race." />
          {chartData.length > 0 && (
            <div className="chart">
              <ResponsiveContainer width="100%" height={400}>
                <ScatterChart margin={{ top: 16, right: 16, bottom: 16, left: 16 }}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis type="number" dataKey="x" name="Grid" domain={[1, maxPos]} tick={{ fontSize: 12 }} label={{ value: "Grid position", position: "insideBottom", offset: -4 }} />
                  <YAxis type="number" dataKey="y" name="Finish" domain={[1, maxPos]} reversed tick={{ fontSize: 12 }} label={{ value: "Finish", angle: -90, position: "insideLeft" }} />
                  <ReferenceLine segment={[{ x: 1, y: 1 }, { x: maxPos, y: maxPos }]} stroke="var(--line)" strokeDasharray="4 4" />
                  <Tooltip content={({ payload }) => {
                    if (!payload?.length) return null;
                    const d = payload[0].payload;
                    return <div className="callout" style={{ padding: "6px 10px", fontSize: "0.9rem" }}>
                      <strong>{d.name}</strong><br/>Grid {d.x} → Finish {d.y} ({d.gained > 0 ? `+${d.gained}` : d.gained} places)
                    </div>;
                  }} />
                  <Scatter data={chartData} fill="#e10600" />
                </ScatterChart>
              </ResponsiveContainer>
              <p className="muted">Points above the diagonal gained places; below lost places.</p>
            </div>
          )}
          {(grid.data ?? []).length > 0 && (
            <table>
              <thead><tr><th>Pos</th><th>Driver</th><th>Team</th><th>Grid</th><th>+/-</th></tr></thead>
              <tbody>
                {grid.data!.map((d) => (
                  <tr key={`${d.driverCode}-${d.grid}`}>
                    <td>{d.finish}</td><td>{d.familyName}</td><td>{d.constructor}</td><td>{d.grid}</td>
                    <td style={{ color: d.gained > 0 ? "#00a82d" : d.gained < 0 ? "#e10600" : undefined, fontWeight: 600 }}>
                      {d.gained > 0 ? `+${d.gained}` : d.gained}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </section>
  );
}

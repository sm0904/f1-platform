import { useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, type DriverResultRow } from "../api";
import { Status } from "../components/Status";
import { useAsync } from "../useAsync";
import { formatPoints, positionLabel } from "../format";

export function DriverPage() {
  const [code, setCode] = useState("VER");
  const [input, setInput] = useState("VER");
  const data = useAsync(() => (api as any).driverResults(code) as Promise<{ driver: any; results: DriverResultRow[] }>, [code]);

  const raceResults = (data.data?.results ?? []).filter((r) => r.sessionType === "RACE");
  const seasons = [...new Set(raceResults.map((r) => r.season))];
  const chartData = seasons.map((s) => ({
    season: s, points: raceResults.filter((r) => r.season === s).reduce((sum, r) => sum + r.points, 0),
  })).sort((a, b) => a.season - b.season).slice(-10);

  return (
    <section>
      <header className="page-header">
        <h2>Driver</h2>
        <form className="inline-form" onSubmit={(e) => { e.preventDefault(); setCode(input.toUpperCase()); }}>
          <input type="text" value={input} onChange={(e) => setInput(e.target.value)}
                 placeholder="Driver code (e.g. VER)" maxLength={3} className="code-input" />
          <button type="submit" className="chip active">Go</button>
        </form>
      </header>
      <Status loading={data.loading} error={data.error} />
      {data.data && (
        <>
          <div className="callout">
            <p><strong>{data.data.driver.givenName} {data.data.driver.familyName}</strong>
              {data.data.driver.nationality && ` · ${data.data.driver.nationality}`}
              {data.data.driver.permanentNumber && ` · #${data.data.driver.permanentNumber}`}</p>
          </div>
          {chartData.length > 0 && (
            <div className="chart">
              <p className="muted">Points per season (last {chartData.length} seasons)</p>
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="season" /><YAxis /><Tooltip />
                  <Bar dataKey="points" fill="#e10600" isAnimationActive={false} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
          <h3>Results ({raceResults.length} races)</h3>
          <div className="table-scroll">
          <table>
            <thead><tr><th>Season</th><th>Rnd</th><th>Race</th><th>Pos</th><th>Grid</th><th>Pts</th><th>Team</th><th>Status</th></tr></thead>
            <tbody>
              {raceResults.slice(0, 60).map((r, i) => (
                <tr key={i}>
                  <td>{r.season}</td><td>{r.round}</td><td>{r.raceName.replace(" Grand Prix", " GP")}</td>
                  <td>{positionLabel(r.position, r.positionText)}</td><td>{r.grid ?? "-"}</td>
                  <td>{formatPoints(r.points)}</td><td>{r.constructor}</td><td className="muted">{r.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        </>
      )}
    </section>
  );
}

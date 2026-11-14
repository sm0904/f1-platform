import { useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, Cell } from "recharts";
import { api, type DriverResultRow } from "../api";
import { Status } from "../components/Status";
import { useAsync } from "../useAsync";
import { formatPoints, positionLabel } from "../format";
import { teamColor } from "../teamColors";
import { getDriverHeadshot, getDriverNumber, getDriverCountry } from "../driverMeta";

export function DriverPage() {
  const [code, setCode] = useState("VER");
  const [input, setInput] = useState("VER");
  const data = useAsync(() => (api as any).driverResults(code) as Promise<{ driver: any; results: DriverResultRow[] }>, [code]);

  const raceResults = (data.data?.results ?? []).filter((r) => r.sessionType === "RACE");
  const seasons = [...new Set(raceResults.map((r) => r.season))];
  const chartData = seasons.map((s) => {
    const rows = raceResults.filter((r) => r.season === s);
    const team = rows[rows.length - 1]?.constructor ?? "";
    return {
      season: String(s),
      points: rows.reduce((sum, r) => sum + r.points, 0),
      color: teamColor(team),
    };
  }).sort((a, b) => Number(a.season) - Number(b.season)).slice(-10);

  const latestTeam = raceResults[0]?.constructor ?? "";
  const color = teamColor(latestTeam);
  const headshot = getDriverHeadshot(code);
  const driverNum = getDriverNumber(code);
  const country = getDriverCountry(code);

  const totalWins = raceResults.filter((r) => r.position === 1).length;
  const totalPodiums = raceResults.filter((r) => r.position !== null && r.position <= 3).length;
  const totalPts = raceResults.reduce((s, r) => s + r.points, 0);

  return (
    <section>
      <header className="page-header">
        <h2>Driver lookup</h2>
        <form className="inline-form" onSubmit={(e) => { e.preventDefault(); setCode(input.toUpperCase()); }}>
          <input type="text" value={input} onChange={(e) => setInput(e.target.value)}
                 placeholder="e.g. VER" maxLength={3} className="code-input" />
          <button type="submit" className="chip active">Go</button>
        </form>
      </header>

      <Status loading={data.loading} error={data.error} />

      {data.data && (
        <>
          {/* Profile card */}
          <div className="driver-profile" style={{ borderLeft: `3px solid ${color}` }}>
            {headshot ? (
              <img className="driver-profile-photo" src={headshot} alt="" loading="lazy" style={{ borderColor: color }} />
            ) : (
              <div className="driver-profile-photo" style={{ borderColor: color, display: "flex", alignItems: "center", justifyContent: "center", fontSize: "1.4rem", fontWeight: 800, color: "var(--fg-muted)" }}>
                {code}
              </div>
            )}
            <div className="driver-profile-info">
              <h3>
                {data.data.driver.givenName}{" "}
                <span style={{ color }}>{data.data.driver.familyName}</span>
                {driverNum && <span style={{ marginLeft: "0.5rem", fontSize: "0.85rem", color: "var(--fg-muted)" }}>#{driverNum}</span>}
              </h3>
              <div className="meta">
                {data.data.driver.nationality && <span>{country ? `${country} ` : ""}{data.data.driver.nationality}</span>}
                <span className="team-badge"><span className="team-dot" style={{ background: color }} />{latestTeam}</span>
              </div>
            </div>
          </div>

          {/* Stat cards */}
          <div className="card-grid">
            <div className="stat-card">
              <span className="stat-label">Races</span>
              <span className="stat-value">{raceResults.length}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Wins</span>
              <span className="stat-value" style={{ color: totalWins > 0 ? "#d4a80e" : undefined }}>{totalWins}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Podiums</span>
              <span className="stat-value">{totalPodiums}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Total points</span>
              <span className="stat-value">{formatPoints(totalPts)}</span>
            </div>
          </div>

          {/* Points-per-season chart */}
          {chartData.length > 0 && (
            <div className="chart">
              <p className="chart-title">Points per season (last {chartData.length})</p>
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="season" interval={0} tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip
                    contentStyle={{ background: "var(--bg-raised)", border: "1px solid var(--border)", borderRadius: 6 }}
                  />
                  <Bar dataKey="points" radius={[4, 4, 0, 0]} isAnimationActive={false}>
                    {chartData.map((d, i) => <Cell key={i} fill={d.color} />)}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}

          {/* Results table */}
          <h3 className="section-title">Race results</h3>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Season</th><th>Rnd</th><th>Race</th><th style={{width:48}}>Pos</th><th>Grid</th><th>Pts</th><th>Team</th><th>Status</th>
                </tr>
              </thead>
              <tbody>
                {raceResults.slice(0, 60).map((r, i) => (
                  <tr key={i}>
                    <td>{r.season}</td>
                    <td>{r.round}</td>
                    <td>{r.raceName.replace(" Grand Prix", " GP")}</td>
                    <td>
                      <span className={`pos-badge ${r.position !== null && r.position <= 3 ? `p${r.position}` : ""}`}>
                        {positionLabel(r.position, r.positionText)}
                      </span>
                    </td>
                    <td>{r.grid ?? "-"}</td>
                    <td style={{ fontWeight: r.points > 0 ? 700 : 400 }}>{formatPoints(r.points)}</td>
                    <td>
                      <span className="team-badge">
                        <span className="team-dot" style={{ background: teamColor(r.constructor) }} />
                        {r.constructor}
                      </span>
                    </td>
                    <td style={{ color: r.status !== "CLASSIFIED" ? "var(--fg-muted)" : undefined }}>{r.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {raceResults.length > 60 && (
            <p className="muted" style={{ textAlign: "center", marginTop: "0.5rem" }}>
              Showing 60 of {raceResults.length} results.
            </p>
          )}
        </>
      )}
    </section>
  );
}

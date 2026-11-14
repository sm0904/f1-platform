import { useState } from "react";
import { BarChart, Bar, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, Cell } from "recharts";
import { api } from "../api";
import { SeasonPicker } from "../components/SeasonPicker";
import { Status } from "../components/Status";
import { useAsync } from "../useAsync";
import { formatPoints } from "../format";
import { teamColor } from "../teamColors";
import { getDriverHeadshot } from "../driverMeta";
import type { SeasonSummary } from "../api";

interface Props {
  seasons: SeasonSummary[];
}

export function StandingsPage({ seasons }: Props) {
  const latest = seasons[seasons.length - 1]?.year ?? 2024;
  const [year, setYear] = useState(latest);
  const drivers = useAsync(() => api.driverStandings(year), [year]);
  const constructors = useAsync(() => api.constructorStandings(year), [year]);
  const [view, setView] = useState<"drivers" | "constructors">("drivers");

  const driverChart = (drivers.data ?? []).slice(0, 10).map((d) => ({
    name: d.driverCode ?? d.familyName.slice(0, 3).toUpperCase(),
    points: d.points,
    fullName: `${d.givenName} ${d.familyName}`,
  }));

  const constructorChart = (constructors.data ?? []).slice(0, 10).map((c) => ({
    name: c.name.replace(/ Racing$/i, "").replace(/ F1 Team$/i, ""),
    points: c.points,
    color: teamColor(c.name),
  }));

  const leader = drivers.data?.[0];
  const topTeam = constructors.data?.[0];

  return (
    <section>
      <header className="page-header">
        <h2>{year} Championship</h2>
        <SeasonPicker seasons={seasons} value={year} onChange={setYear} />
      </header>

      {leader && topTeam && (
        <div className="card-grid">
          <div className="stat-card">
            <span className="stat-label">Leader</span>
            <span className="stat-value">{leader.givenName} {leader.familyName}</span>
            <span className="stat-sub">{formatPoints(leader.points)} pts · {leader.wins} wins</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Constructors</span>
            <span className="stat-value" style={{ color: teamColor(topTeam.name) }}>{topTeam.name}</span>
            <span className="stat-sub">{formatPoints(topTeam.points)} pts · {topTeam.wins} wins</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Drivers</span>
            <span className="stat-value">{drivers.data?.length ?? "-"}</span>
            <span className="stat-sub">scored points this season</span>
          </div>
        </div>
      )}

      <div className="race-picker">
        <button type="button" className={view === "drivers" ? "chip active" : "chip"} onClick={() => setView("drivers")}>Drivers</button>
        <button type="button" className={view === "constructors" ? "chip active" : "chip"} onClick={() => setView("constructors")}>Constructors</button>
      </div>

      {view === "drivers" && (
        <>
          <Status loading={drivers.loading} error={drivers.error} empty={drivers.data?.length === 0} emptyText="No results for this season." />
          {drivers.data && drivers.data.length > 0 && (
            <>
              <div className="chart">
                <p className="chart-title">Points — top 10</p>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={driverChart} margin={{ top: 4, right: 12, bottom: 4, left: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="name" interval={0} tick={{ fontSize: 11 }} />
                    <YAxis tick={{ fontSize: 11 }} />
                    <Tooltip formatter={(v) => formatPoints(Number(v))} labelFormatter={(_, payload) => payload?.[0]?.payload?.fullName ?? ""} />
                    <Bar dataKey="points" radius={[4, 4, 0, 0]} fill="#e10600" isAnimationActive={false} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr><th style={{width:48}}>Pos</th><th>Driver</th><th>Points</th><th>Wins</th></tr>
                  </thead>
                  <tbody>
                    {drivers.data.map((d) => {
                      const headshot = getDriverHeadshot(d.driverCode);
                      return (
                        <tr key={`${d.position}-${d.driverCode}-${d.familyName}`}>
                          <td>
                            <span className={`pos-badge ${d.position <= 3 ? `p${d.position}` : ""}`}>
                              {d.position}
                            </span>
                          </td>
                          <td>
                            <div className="driver-cell">
                              {headshot && <img className="driver-avatar" src={headshot} alt="" loading="lazy" />}
                              <div className="driver-name">
                                <span className="family">{d.familyName}</span>
                                <span className="given">{d.givenName}</span>
                              </div>
                            </div>
                          </td>
                          <td style={{ fontWeight: 700, fontSize: "0.95rem" }}>{formatPoints(d.points)}</td>
                          <td>{d.wins}</td>
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

      {view === "constructors" && (
        <>
          <Status loading={constructors.loading} error={constructors.error} empty={constructors.data?.length === 0} emptyText="No results for this season." />
          {constructors.data && constructors.data.length > 0 && (
            <>
              <div className="chart">
                <p className="chart-title">Points — all teams</p>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={constructorChart} margin={{ top: 4, right: 12, bottom: 4, left: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="name" interval={0} tick={{ fontSize: 10 }} angle={-20} textAnchor="end" height={60} />
                    <YAxis tick={{ fontSize: 11 }} />
                    <Tooltip />
                    <Bar dataKey="points" radius={[4, 4, 0, 0]} isAnimationActive={false}>
                      {constructorChart.map((c, i) => <Cell key={i} fill={c.color} />)}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr><th style={{width:48}}>Pos</th><th>Team</th><th>Points</th><th>Wins</th></tr>
                  </thead>
                  <tbody>
                    {constructors.data.map((c) => (
                      <tr key={`${c.position}-${c.name}`}>
                        <td>
                          <span className={`pos-badge ${c.position <= 3 ? `p${c.position}` : ""}`}>{c.position}</span>
                        </td>
                        <td>
                          <span className="team-badge">
                            <span className="team-dot" style={{ background: teamColor(c.name) }} />
                            {c.name}
                          </span>
                        </td>
                        <td style={{ fontWeight: 700 }}>{formatPoints(c.points)}</td>
                        <td>{c.wins}</td>
                      </tr>
                    ))}
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

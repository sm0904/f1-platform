import { useState } from "react";
import { BarChart, Bar, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api } from "../api";
import { SeasonPicker } from "../components/SeasonPicker";
import { Status } from "../components/Status";
import { useAsync } from "../useAsync";
import { formatPoints } from "../format";
import type { SeasonSummary } from "../api";

interface Props {
  seasons: SeasonSummary[];
}

export function StandingsPage({ seasons }: Props) {
  const latest = seasons[seasons.length - 1]?.year ?? 2024;
  const [year, setYear] = useState(latest);
  const drivers = useAsync(() => api.driverStandings(year), [year]);
  const constructors = useAsync(() => api.constructorStandings(year), [year]);

  const driverChart = (drivers.data ?? []).slice(0, 10).map((d) => ({
    name: d.driverCode ?? d.familyName,
    points: d.points,
  }));
  const constructorChart = (constructors.data ?? []).slice(0, 10).map((c) => ({
    name: c.name,
    points: c.points,
  }));

  return (
    <section>
      <header className="page-header">
        <h2>Championship standings</h2>
        <SeasonPicker seasons={seasons} value={year} onChange={setYear} />
      </header>
      <p className="note">
        Points include sprint points. Wins count races only. Ties are ordered by wins, then by name.
      </p>

      <h3>Drivers</h3>
      <Status loading={drivers.loading} error={drivers.error} empty={drivers.data?.length === 0} emptyText="No results for this season." />
      {drivers.data && drivers.data.length > 0 && (
        <>
          <div className="chart">
            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={driverChart} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" interval={0} />
                <YAxis />
                <Tooltip />
                <Legend />
                <Bar dataKey="points" name="Points" fill="#e10600" />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <table>
            <thead><tr><th>Pos</th><th>Driver</th><th>Points</th><th>Wins</th></tr></thead>
            <tbody>
              {drivers.data.map((d) => (
                <tr key={`${d.position}-${d.driverCode}-${d.familyName}`}>
                  <td>{d.position}</td>
                  <td>{d.givenName} {d.familyName}</td>
                  <td>{formatPoints(d.points)}</td>
                  <td>{d.wins}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      <h3>Constructors</h3>
      <Status loading={constructors.loading} error={constructors.error} empty={constructors.data?.length === 0} emptyText="No results for this season." />
      {constructors.data && constructors.data.length > 0 && (
        <>
          <div className="chart">
            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={constructorChart} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" interval={0} />
                <YAxis />
                <Tooltip />
                <Legend />
                <Bar dataKey="points" name="Points" fill="#1e41ff" />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <table>
            <thead><tr><th>Pos</th><th>Constructor</th><th>Points</th><th>Wins</th></tr></thead>
            <tbody>
              {constructors.data.map((c) => (
                <tr key={`${c.position}-${c.name}`}>
                  <td>{c.position}</td>
                  <td>{c.name}</td>
                  <td>{formatPoints(c.points)}</td>
                  <td>{c.wins}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}

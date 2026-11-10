import { useState } from "react";
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, type PointsProgression, type SeasonSummary } from "../api";
import { SeasonPicker } from "../components/SeasonPicker";
import { Status } from "../components/Status";
import { useAsync } from "../useAsync";

const COLORS = ["#e10600", "#1e41ff", "#00d2be", "#ff8700", "#006f62", "#0090ff", "#2b4562", "#b6babd", "#005aff", "#900000",
  "#c92d4b", "#0060ff", "#7c878e", "#f596c8", "#469bff", "#c8c8c8", "#52e252", "#ff6600", "#000000", "#999900"];

interface Props { seasons: SeasonSummary[] }

export function ProgressionPage({ seasons }: Props) {
  const latest = seasons[seasons.length - 1]?.year ?? 2024;
  const [year, setYear] = useState(latest);
  const prog = useAsync(() => (api as any).pointsProgression(year) as Promise<PointsProgression[]>, [year]);

  // Build the chart data: one row per round, one column per driver
  const rounds = new Map<number, Record<string, unknown>>();
  const drivers: { key: string; name: string; color: string }[] = [];
  if (prog.data) {
    // Sort drivers by final total descending
    const sorted = [...prog.data].sort((a, b) => {
      const la = a.rounds[a.rounds.length - 1]?.cumulative ?? 0;
      const lb = b.rounds[b.rounds.length - 1]?.cumulative ?? 0;
      return lb - la;
    });
    sorted.forEach((d, i) => {
      const key = d.driverCode ?? d.familyName;
      drivers.push({ key, name: `${d.givenName} ${d.familyName}`, color: COLORS[i % COLORS.length] });
      for (const r of d.rounds) {
        const row = rounds.get(r.round) ?? { round: r.round, label: `R${r.round}` };
        row[key] = r.cumulative;
        rounds.set(r.round, row);
      }
    });
  }
  const chartData = [...rounds.values()].sort((a, b) => (a.round as number) - (b.round as number));

  return (
    <section>
      <header className="page-header">
        <h2>Points progression</h2>
        <SeasonPicker seasons={seasons} value={year} onChange={setYear} />
      </header>
      <Status loading={prog.loading} error={prog.error} empty={prog.data?.length === 0} emptyText="No data for this season." />
      {chartData.length > 0 && (
        <div className="chart chart-wide">
          <ResponsiveContainer width="100%" height={460}>
            <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 0 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="label" interval={0} tick={{ fontSize: 11 }} />
              <YAxis />
              <Tooltip />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              {drivers.map((d) => (
                <Line key={d.key} type="monotone" dataKey={d.key} name={d.name}
                      stroke={d.color} dot={false} strokeWidth={2} isAnimationActive={false} />
              ))}
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </section>
  );
}

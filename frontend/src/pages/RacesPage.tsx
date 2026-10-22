import { useState } from "react";
import { api, type SessionType } from "../api";
import type { RaceSummary, SeasonSummary } from "../api";
import { SeasonPicker } from "../components/SeasonPicker";
import { Status } from "../components/Status";
import { useAsync } from "../useAsync";
import { formatDate, formatPoints, positionLabel, sessionLabel } from "../format";

interface Props {
  seasons: SeasonSummary[];
}

const SESSIONS: SessionType[] = ["RACE", "SPRINT", "QUALIFYING"];

export function RacesPage({ seasons }: Props) {
  const latest = seasons[seasons.length - 1]?.year ?? 2024;
  const [year, setYear] = useState(latest);
  const races = useAsync(() => api.races(year), [year]);
  const [round, setRound] = useState<number | null>(null);
  const [session, setSession] = useState<SessionType>("RACE");

  const selected: RaceSummary | undefined =
    races.data?.find((r) => r.round === round) ?? races.data?.[races.data.length - 1];
  const activeRound = selected?.round ?? null;

  const results = useAsync(
    () => (activeRound === null ? Promise.resolve([]) : api.results(year, activeRound, session)),
    [year, activeRound, session],
  );

  return (
    <section>
      <header className="page-header">
        <h2>Race results</h2>
        <SeasonPicker seasons={seasons} value={year} onChange={(y) => { setYear(y); setRound(null); }} />
      </header>

      <Status loading={races.loading} error={races.error} empty={races.data?.length === 0} emptyText="No races in this season." />
      {races.data && races.data.length > 0 && (
        <>
          <div className="race-picker" role="tablist" aria-label="Races">
            {races.data.map((r) => (
              <button
                key={r.round}
                type="button"
                className={r.round === activeRound ? "chip active" : "chip"}
                onClick={() => setRound(r.round)}
                aria-pressed={r.round === activeRound}
              >
                R{r.round} {r.name.replace(" Grand Prix", " GP")}
              </button>
            ))}
          </div>

          {selected && (
            <p className="note">
              {selected.name}, {selected.circuit}
              {selected.locality ? `, ${selected.locality}` : ""} · {formatDate(selected.date)}
            </p>
          )}

          <div className="session-picker">
            {SESSIONS.map((s) => (
              <button
                key={s}
                type="button"
                className={s === session ? "chip active" : "chip"}
                onClick={() => setSession(s)}
                aria-pressed={s === session}
              >
                {sessionLabel(s)}
              </button>
            ))}
          </div>

          <Status loading={results.loading} error={results.error} empty={results.data?.length === 0} emptyText={`No ${sessionLabel(session).toLowerCase()} results for this race.`} />
          {results.data && results.data.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Pos</th><th>No.</th><th>Driver</th><th>Team</th>
                  {session !== "QUALIFYING" && <th>Grid</th>}
                  {session === "QUALIFYING" ? <><th>Q1</th><th>Q2</th><th>Q3</th></> : <><th>Laps</th><th>Time or status</th><th>Points</th></>}
                </tr>
              </thead>
              <tbody>
                {results.data.map((r) => (
                  <tr key={`${r.driverCode}-${r.carNumber}-${r.positionText}`}>
                    <td>{positionLabel(r.position, r.positionText)}</td>
                    <td>{r.carNumber ?? "-"}</td>
                    <td>{r.givenName} {r.familyName}</td>
                    <td>{r.constructor}</td>
                    {session !== "QUALIFYING" && <td>{r.grid ?? "-"}</td>}
                    {session === "QUALIFYING" ? (
                      <>
                        <td>{r.q1 ?? "-"}</td><td>{r.q2 ?? "-"}</td><td>{r.q3 ?? "-"}</td>
                      </>
                    ) : (
                      <>
                        <td>{r.laps ?? "-"}</td>
                        <td>{r.timeText ?? r.statusDetail ?? r.status}</td>
                        <td>{formatPoints(r.points)}</td>
                      </>
                    )}
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

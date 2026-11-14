import { useState } from "react";
import { api, type SessionType } from "../api";
import type { RaceSummary, SeasonSummary } from "../api";
import { SeasonPicker } from "../components/SeasonPicker";
import { Status } from "../components/Status";
import { useAsync } from "../useAsync";
import { formatDate, formatPoints, positionLabel, sessionLabel } from "../format";
import { teamColor } from "../teamColors";
import { getDriverHeadshot } from "../driverMeta";

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

  const winner = results.data?.[0];

  return (
    <section>
      <header className="page-header">
        <h2>Results</h2>
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
                R{r.round}
              </button>
            ))}
          </div>

          {selected && (
            <div className="card" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "0.75rem" }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: "1.1rem" }}>{selected.name}</div>
                <div style={{ color: "var(--fg-secondary)", fontSize: "0.88rem" }}>
                  {selected.circuit}{selected.locality ? ` · ${selected.locality}` : ""} · {formatDate(selected.date)}
                </div>
              </div>
              {winner && session === "RACE" && (
                <div style={{ display: "flex", alignItems: "center", gap: "0.6rem" }}>
                  {getDriverHeadshot(winner.driverCode) && (
                    <img className="driver-avatar" src={getDriverHeadshot(winner.driverCode)!} alt="" loading="lazy" />
                  )}
                  <div>
                    <div style={{ fontSize: "0.78rem", color: "var(--fg-muted)" }}>Winner</div>
                    <div style={{ fontWeight: 700 }}>{winner.givenName} {winner.familyName}</div>
                  </div>
                </div>
              )}
            </div>
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
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th style={{width:48}}>Pos</th><th>Driver</th><th>Team</th>
                    {session !== "QUALIFYING" && <th>Grid</th>}
                    {session === "QUALIFYING" ? <><th>Q1</th><th>Q2</th><th>Q3</th></> : <><th>Laps</th><th>Time / Status</th><th>Pts</th></>}
                  </tr>
                </thead>
                <tbody>
                  {results.data.map((r) => {
                    const headshot = getDriverHeadshot(r.driverCode);
                    return (
                      <tr key={`${r.driverCode}-${r.carNumber}-${r.positionText}`}>
                        <td>
                          <span className={`pos-badge ${r.position !== null && r.position <= 3 ? `p${r.position}` : ""}`}>
                            {positionLabel(r.position, r.positionText)}
                          </span>
                        </td>
                        <td>
                          <div className="driver-cell">
                            {headshot && <img className="driver-avatar" src={headshot} alt="" loading="lazy" />}
                            <div className="driver-name">
                              <span className="family">{r.familyName}</span>
                              <span className="given">{r.givenName}{r.carNumber ? ` #${r.carNumber}` : ""}</span>
                            </div>
                          </div>
                        </td>
                        <td>
                          <span className="team-badge">
                            <span className="team-dot" style={{ background: teamColor(r.constructor) }} />
                            {r.constructor}
                          </span>
                        </td>
                        {session !== "QUALIFYING" && <td>{r.grid ?? "-"}</td>}
                        {session === "QUALIFYING" ? (
                          <><td>{r.q1 ?? "-"}</td><td>{r.q2 ?? "-"}</td><td>{r.q3 ?? "-"}</td></>
                        ) : (
                          <>
                            <td>{r.laps ?? "-"}</td>
                            <td style={{ color: r.status !== "CLASSIFIED" ? "var(--fg-muted)" : undefined }}>
                              {r.timeText ?? r.statusDetail ?? r.status}
                            </td>
                            <td style={{ fontWeight: r.points > 0 ? 700 : 400 }}>{formatPoints(r.points)}</td>
                          </>
                        )}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  );
}

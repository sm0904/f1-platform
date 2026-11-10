// Typed client for the read-only F1 API (docs/API.md). Shapes mirror the Java records in Dtos.java.

export interface SeasonSummary { year: number; races: number }
export interface RaceSummary {
  season: number; round: number; name: string; date: string;
  circuit: string; locality: string | null; country: string | null;
}
export interface ResultRow {
  position: number | null; positionText: string; status: string; statusDetail: string | null;
  carNumber: number | null; grid: number | null; laps: number | null; timeText: string | null;
  points: number; driverCode: string | null; givenName: string; familyName: string;
  constructor: string; q1: string | null; q2: string | null; q3: string | null;
}
export interface DriverStanding {
  position: number; driverCode: string | null; givenName: string; familyName: string;
  points: number; wins: number;
}
export interface ConstructorStanding { position: number; name: string; points: number; wins: number }
export interface PredictedDriver {
  givenName: string; familyName: string; constructor: string;
  ratingStrength: number; winProb: number; podiumProb: number; expectedPosition: number;
}
export interface PredictionRun {
  id: number; modelName: string; modelVersion: string;
  targetSeason: number; targetRound: number; targetName: string; targetDate: string;
  dataCutoffDate: string; inputRaces: number; gridKnown: boolean;
  simulations: number; randomSeed: number; createdAt: string;
  parameters: Record<string, unknown>; predictions: PredictedDriver[];
}

export type SessionType = "RACE" | "SPRINT" | "QUALIFYING" | "SPRINT_QUALIFYING";

/** An error the API returned as RFC 9457 problem details, or a network failure. */
export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function getJson<T>(path: string): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, { headers: { Accept: "application/json" } });
  } catch {
    throw new ApiError(0, "Cannot reach the API. Is it running on port 8080?");
  }
  if (!response.ok) {
    let detail = response.statusText;
    try {
      const problem = (await response.json()) as { detail?: string };
      if (problem.detail) detail = problem.detail;
    } catch {
      // Keep the status text when the body is not JSON.
    }
    throw new ApiError(response.status, detail);
  }
  return (await response.json()) as T;
}

export const api = {
  seasons: () => getJson<SeasonSummary[]>("/api/v1/seasons"),
  races: (year: number) => getJson<RaceSummary[]>(`/api/v1/seasons/${year}/races`),
  results: (year: number, round: number, session: SessionType) =>
    getJson<ResultRow[]>(`/api/v1/seasons/${year}/races/${round}/results?session=${session}`),
  driverStandings: (year: number) => getJson<DriverStanding[]>(`/api/v1/seasons/${year}/standings/drivers`),
  constructorStandings: (year: number) =>
    getJson<ConstructorStanding[]>(`/api/v1/seasons/${year}/standings/constructors`),
  predictions: (year: number, round: number) =>
    getJson<PredictionRun[]>(`/api/v1/predictions/${year}/${round}`),
};

export interface PointsProgression {
  driverCode: string | null; givenName: string; familyName: string; constructor: string;
  rounds: { round: number; raceName: string; cumulative: number }[];
}
export interface DriverProfile {
  id: number; code: string | null; givenName: string; familyName: string;
  nationality: string | null; permanentNumber: number | null;
}
export interface DriverResultRow {
  season: number; round: number; raceName: string; circuit: string; sessionType: string;
  position: number | null; positionText: string; status: string; grid: number | null;
  points: number; constructor: string;
}
export interface ConstructorProfile { id: number; name: string; nationality: string | null }
export interface ConstructorSeasonRow { season: number; points: number; wins: number; podiums: number; bestDriver: string | null }
export interface GridFinishRow { driverCode: string | null; familyName: string; constructor: string; grid: number; finish: number; gained: number }

// Add to the api object
Object.assign(api, {
  pointsProgression: (year: number) => getJson<PointsProgression[]>(`/api/v1/seasons/${year}/progression`),
  driverProfile: (code: string) => getJson<DriverProfile>(`/api/v1/drivers/${code}`),
  driverResults: (code: string, season?: number) =>
    getJson<{ driver: DriverProfile; results: DriverResultRow[] }>(`/api/v1/drivers/${code}/results${season ? `?season=${season}` : ""}`),
  constructorProfile: (name: string) => getJson<ConstructorProfile>(`/api/v1/constructors/${name}`),
  constructorSeasons: (name: string) =>
    getJson<{ constructor: ConstructorProfile; seasons: ConstructorSeasonRow[] }>(`/api/v1/constructors/${name}/seasons`),
  gridVsFinish: (year: number, round: number) => getJson<GridFinishRow[]>(`/api/v1/seasons/${year}/races/${round}/grid`),
});

import { lazy, Suspense, useState } from "react";
import { api, type SeasonSummary } from "./api";
import { Status } from "./components/Status";
const PredictionPage = lazy(() => import("./pages/PredictionPage").then((m) => ({ default: m.PredictionPage })));
const RacesPage = lazy(() => import("./pages/RacesPage").then((m) => ({ default: m.RacesPage })));
const StandingsPage = lazy(() => import("./pages/StandingsPage").then((m) => ({ default: m.StandingsPage })));
import { useAsync } from "./useAsync";

type Tab = "standings" | "races" | "prediction";

const TABS: { id: Tab; label: string }[] = [
  { id: "standings", label: "Standings" },
  { id: "races", label: "Race results" },
  { id: "prediction", label: "Prediction" },
];

export function App() {
  const [tab, setTab] = useState<Tab>("standings");
  const seasons = useAsync<SeasonSummary[]>(() => api.seasons(), []);

  return (
    <div className="app">
      <header className="app-header">
        <span className="brand">F1 Platform</span>
        <nav aria-label="Sections">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              className={t.id === tab ? "tab active" : "tab"}
              onClick={() => setTab(t.id)}
              aria-current={t.id === tab ? "page" : undefined}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </header>

      <main>
        <Suspense fallback={<p className="muted">Loading...</p>}>
          {tab === "prediction" ? (
            <PredictionPage />
          ) : seasons.data ? (
            tab === "standings" ? <StandingsPage seasons={seasons.data} /> : <RacesPage seasons={seasons.data} />
          ) : (
            <Status loading={seasons.loading} error={seasons.error} />
          )}
        </Suspense>
      </main>
    </div>
  );
}

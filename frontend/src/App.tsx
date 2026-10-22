import { useState } from "react";
import { api, type SeasonSummary } from "./api";
import { Status } from "./components/Status";
import { PredictionPage } from "./pages/PredictionPage";
import { RacesPage } from "./pages/RacesPage";
import { StandingsPage } from "./pages/StandingsPage";
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
        {tab === "prediction" ? (
          <PredictionPage />
        ) : seasons.data ? (
          tab === "standings" ? <StandingsPage seasons={seasons.data} /> : <RacesPage seasons={seasons.data} />
        ) : (
          <Status loading={seasons.loading} error={seasons.error} />
        )}
      </main>
    </div>
  );
}

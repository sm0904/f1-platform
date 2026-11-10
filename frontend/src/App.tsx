import { lazy, Suspense, useState } from "react";
import { api, type SeasonSummary } from "./api";
import { Status } from "./components/Status";
import { useAsync } from "./useAsync";

const StandingsPage = lazy(() => import("./pages/StandingsPage").then((m) => ({ default: m.StandingsPage })));
const RacesPage = lazy(() => import("./pages/RacesPage").then((m) => ({ default: m.RacesPage })));
const PredictionPage = lazy(() => import("./pages/PredictionPage").then((m) => ({ default: m.PredictionPage })));
const ProgressionPage = lazy(() => import("./pages/ProgressionPage").then((m) => ({ default: m.ProgressionPage })));
const DriverPage = lazy(() => import("./pages/DriverPage").then((m) => ({ default: m.DriverPage })));
const GridPage = lazy(() => import("./pages/GridPage").then((m) => ({ default: m.GridPage })));

type Tab = "standings" | "progression" | "races" | "grid" | "driver" | "prediction";

const TABS: { id: Tab; label: string }[] = [
  { id: "standings", label: "Standings" },
  { id: "progression", label: "Progression" },
  { id: "races", label: "Results" },
  { id: "grid", label: "Grid vs finish" },
  { id: "driver", label: "Drivers" },
  { id: "prediction", label: "Prediction" },
];

export function App() {
  const [tab, setTab] = useState<Tab>("standings");
  const seasons = useAsync<SeasonSummary[]>(() => api.seasons(), []);

  const page = () => {
    if (tab === "prediction") return <PredictionPage />;
    if (tab === "driver") return <DriverPage />;
    if (!seasons.data) return <Status loading={seasons.loading} error={seasons.error} />;
    switch (tab) {
      case "standings": return <StandingsPage seasons={seasons.data} />;
      case "progression": return <ProgressionPage seasons={seasons.data} />;
      case "races": return <RacesPage seasons={seasons.data} />;
      case "grid": return <GridPage seasons={seasons.data} />;
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <span className="brand">F1 Platform</span>
        <nav aria-label="Sections">
          {TABS.map((t) => (
            <button key={t.id} type="button" className={t.id === tab ? "tab active" : "tab"}
                    onClick={() => setTab(t.id)} aria-current={t.id === tab ? "page" : undefined}>{t.label}</button>
          ))}
        </nav>
      </header>
      <main>
        <Suspense fallback={<p className="muted">Loading...</p>}>{page()}</Suspense>
      </main>
    </div>
  );
}

// Official 2026 team colours from OpenF1.
// Used for bar fills, line strokes, and team accent borders.

export const TEAM_COLORS: Record<string, string> = {
  "Red Bull":       "#4781D7",
  "McLaren":        "#F47600",
  "Ferrari":        "#ED1131",
  "Mercedes":       "#00D7B6",
  "Aston Martin":   "#229971",
  "Alpine":         "#00A1E8",
  "Williams":       "#1868DB",
  "Racing Bulls":   "#6C98FF",
  "Haas":           "#9C9FA2",
  "Audi":           "#F50537",
  "Cadillac":       "#909090",
  // Historical fallbacks
  "Renault":        "#FFD800",
  "Toro Rosso":     "#4E7C9B",
  "Force India":    "#F596C8",
  "Sauber":         "#52E252",
  "Alfa Romeo":     "#A62249",
  "AlphaTauri":     "#4E7C9B",
  "Lotus":          "#FFB800",
  "Marussia":       "#6E0000",
  "Manor":          "#6E0000",
  "Caterham":       "#005030",
  "HRT":            "#968040",
};

// Normalize constructor names: API may say "Red Bull Racing", "Haas F1 Team", etc.
export function teamColor(constructor: string): string {
  const key = constructor
    .replace(/ Racing$/i, "")
    .replace(/ F1 Team$/i, "")
    .replace(/ Racing Team$/i, "")
    .replace(/^Stake F1 Team /, "")
    .replace(/^Visa Cash App /, "")
    .replace(/ Honda RBPT$/i, "")
    .replace(/ Aramco$/i, "")
    .replace(/ Petronas$/i, "")
    .replace(/ Mercedes$/i, "")
    .replace(/ BWT$/i, "")
    .replace(/ Renault$/i, "");
  return TEAM_COLORS[key] ?? "#666";
}

// Generate a semi-transparent version for backgrounds
export function teamColorAlpha(constructor: string, alpha = 0.15): string {
  const hex = teamColor(constructor);
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}

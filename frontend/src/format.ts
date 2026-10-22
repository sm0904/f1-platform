// Pure display helpers. Kept free of React so they can be unit tested.

/** Formats a probability in [0, 1] as a percentage with one decimal place. */
export function formatPercent(p: number): string {
  if (!Number.isFinite(p)) return "-";
  return `${(p * 100).toFixed(1)}%`;
}

/** Formats championship points: whole numbers stay whole, half points keep their half. */
export function formatPoints(points: number): string {
  return Number.isInteger(points) ? String(points) : points.toFixed(1);
}

/** Expected finishing position with two decimal places. */
export function formatExpectedPosition(p: number): string {
  return Number.isFinite(p) ? p.toFixed(2) : "-";
}

/** A short label for a classified result: the position, or the provider's code (R, D, W) otherwise. */
export function positionLabel(position: number | null, positionText: string): string {
  return position !== null ? String(position) : positionText;
}

/** Turns an ISO date (YYYY-MM-DD) into a readable form such as "11 Oct 2026". Parses as UTC to avoid time-zone shifts. */
export function formatDate(iso: string): string {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString("en-GB", {
    day: "numeric", month: "short", year: "numeric", timeZone: "UTC",
  });
}

/** Human label for a session type. */
export function sessionLabel(type: string): string {
  switch (type) {
    case "RACE": return "Race";
    case "SPRINT": return "Sprint";
    case "QUALIFYING": return "Qualifying";
    case "SPRINT_QUALIFYING": return "Sprint qualifying";
    default: return type;
  }
}

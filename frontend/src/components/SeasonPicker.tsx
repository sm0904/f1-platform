import type { SeasonSummary } from "../api";

interface Props {
  seasons: SeasonSummary[];
  value: number;
  onChange: (year: number) => void;
}

export function SeasonPicker({ seasons, value, onChange }: Props) {
  return (
    <label className="field">
      Season
      <select value={value} onChange={(e) => onChange(Number(e.target.value))}>
        {[...seasons].reverse().map((s) => (
          <option key={s.year} value={s.year}>
            {s.year}
          </option>
        ))}
      </select>
    </label>
  );
}

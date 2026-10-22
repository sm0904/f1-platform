# Result status mapping (Jolpica to session_result.status)

Source fields: `positionText` and `status` (free text). The loader sets `status` from `positionText`,
refined by `status` text, and stores the raw `status` text in `status_detail`.

| positionText | Condition on status text | Schema status |
|---|---|---|
| numeric | any | CLASSIFIED (official classification; reason kept in status_detail) |
| R | "Not classified" | NOT_CLASSIFIED |
| R | otherwise | RETIRED |
| D | "Excluded" | EXCLUDED |
| D | otherwise | DISQUALIFIED |
| W | "Did not start" | NOT_CLASSIFIED |
| W | otherwise | WITHDRAWN |

Rules:
- A numeric position is the official classification, even when the status text gives a retirement
  reason (e.g. "Engine") for a car that completed enough distance.
- Any positionText not listed above fails the load, rather than being guessed.
- Qualifying Q1, Q2, Q3 values are stored as text. Accepted formats: m:ss.mmm, mm:ss.mmm, ss.mmm.

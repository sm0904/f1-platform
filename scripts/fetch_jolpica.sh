#!/usr/bin/env bash
# Downloads Jolpica (Ergast-compatible) results for one session type and a range of seasons.
# Usage: scripts/fetch_jolpica.sh <session> <first_year> [last_year]
#   session: race | sprint | qualifying
# Output: data/raw/jolpica/<session>/<year>/results-<offset>.json (one file per page)
# Pages use limit=100, the server's effective maximum. Existing pages are skipped,
# so re-running is safe. Failed requests are retried with backoff.
set -euo pipefail

BASE="https://api.jolpi.ca/ergast/f1"
LIMIT=100
PAUSE_SECONDS=1
OUT_ROOT="data/raw/jolpica"

session="${1:?usage: $0 <race|sprint|qualifying> <first_year> [last_year]}"
first="${2:?usage: $0 <race|sprint|qualifying> <first_year> [last_year]}"
last="${3:-$first}"

case "$session" in
  race)       endpoint="results" ;;
  sprint)     endpoint="sprint" ;;
  qualifying) endpoint="qualifying" ;;
  *) echo "unknown session: $session" >&2; exit 2 ;;
esac

for year in $(seq "$first" "$last"); do
  dir="$OUT_ROOT/$session/$year"
  mkdir -p "$dir"
  offset=0
  while :; do
    file="$dir/results-$offset.json"
    if [[ ! -s "$file" ]]; then
      url="$BASE/$year/$endpoint.json?limit=$LIMIT&offset=$offset"
      tmp="$file.part"
      ok=0
      for attempt in 1 2 3 4 5; do
        if curl -sS -f --max-time 60 -o "$tmp" "$url"; then ok=1; break; fi
        echo "retry $attempt for $url" >&2
        # exponential backoff: 5, 10, 20, 40 seconds, capped at 60, with full jitter
        delay=$(( 5 * (1 << (attempt - 1)) ))
        (( delay > 60 )) && delay=60
        sleep $(( RANDOM % (delay + 1) ))
      done
      if (( ok == 0 )); then echo "failed: $url" >&2; exit 1; fi
      mv "$tmp" "$file"
      sleep "$PAUSE_SECONDS"
    fi
    total=$(grep -oE '"total":"[0-9]+"' "$file" | head -1 | grep -oE '[0-9]+')
    offset=$((offset + LIMIT))
    if (( offset >= total )); then
      break
    fi
  done
  echo "$session $year: total rows $total"
done

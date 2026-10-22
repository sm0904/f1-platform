# F1 API (v1)

Read-only HTTP API over the loaded database. Public port 8080, admin port 8081.
The service never writes. Load and migrate with the ingestion command first.

## Running

```
export F1_DB_PASSWORD=...        # the database password, from the environment only
java -cp "api/target/classes:persistence/target/classes:<dependency classpath>" \
     io.f1platform.api.F1Application server api/config/f1.yml
```

Health: `GET http://localhost:8081/healthcheck` (reports the database as healthy or not).

## Endpoints

| Method and path | Returns |
|---|---|
| `GET /api/v1/seasons` | Each season with its number of races. |
| `GET /api/v1/seasons/{year}/races` | Races in a season, by round, with circuit. |
| `GET /api/v1/seasons/{year}/races/{round}/results?session=RACE` | Results for one session. `session` is `RACE` (default), `SPRINT`, `QUALIFYING`, or `SPRINT_QUALIFYING`. |
| `GET /api/v1/seasons/{year}/standings/drivers` | Driver championship: points (race and sprint), wins (races only), by position. |
| `GET /api/v1/seasons/{year}/standings/constructors` | Constructor championship, same rules. |
| `GET /api/v1/predictions/{year}/{round}` | Every stored prediction run for that race, newest first. Each run lists its drivers, best expected finish first. |

## Conventions

- **Dates** are ISO strings (`2026-10-25`). Times are ISO 8601 in UTC (`2026-10-22T11:55:18Z`).
- **Points** are decimal strings with one place (`437.0`), so half points are exact.
- **Errors** follow RFC 9457 problem details, with `Content-Type: application/problem+json`:
  unknown season or race returns 404, an unknown session returns 400.
- **Caching:** historical endpoints send `Cache-Control: public, max-age=3600`, because past results do not change.
  Predictions send `max-age=60`, because a new run can appear at any time. Runs are never changed once stored.

## Example

```
GET /api/v1/predictions/2026/18
```

Returns the Austin 2026 pre-qualifying run, with `dataCutoffDate` 2026-10-11, `gridKnown` false, and each
driver's `winProb`, `podiumProb`, and `expectedPosition`.

## Limits (known, to address later)

- No authentication. Every endpoint is public and read-only.
- No pagination. The largest response is a full race or season, which is small.
- No ETag. Caching uses `max-age` only.
- Standings break ties by wins and then name, not by countback.
- Predictions assume the whole field starts (see docs/PREDICTION.md).

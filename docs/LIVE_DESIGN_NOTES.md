# Live path and weekend data: design notes

Status: working notes, not yet ADRs. Figures marked UNVERIFIED have not been measured.

## 1. Requirements (as stated by the owner)

- **Delay:** a few seconds from provider event to what a user sees. Target: p95 at or below 3 seconds.
- **Weekend data:** the API serves all data for the current race weekend (practice, sprint qualifying,
  qualifying, sprint, race).
- **Recency order:** for an ongoing race, the newest events come first, across all sessions of the weekend.
- **Live predictions:** predictions for the weekend update as data arrives. They use only data up to their cutoff.
- **Sources:** Jolpica for historical results (approved). OpenF1 for live timing and, approved here,
  for sprint qualifying results from 2023 onward (six sessions per year, confirmed 2023 to 2026).

## 2. Schedule risk: read this first

- The Austin prediction is due to freeze before first practice on Friday 23 October. Today is Thursday 22 October.
- The live path is out of scope for the Austin MVP (MVP_PLAN.md, section 1). Live predictions cannot be
  delivered before the freeze.
- The race results are fetched as JSON but are not yet loaded into PostgreSQL. The loader is not written.
  The Elo model and the cutoff logic are not written either.
- **Proposal:** Austin ships the static pre-race prediction, stored with its cutoff, plus verification.
  The live weekend view and live predictions are the first phase after Austin, built on the Kafka path.
  This needs your decision.

## 3. Delay budget (few seconds)

| Stage | Budget | Basis |
|---|---|---|
| Provider publish to our receipt (OpenF1 MQTT) | unknown, most of the budget | UNVERIFIED. Measure during a live or replay session. |
| Parse, normalise, sequence | under 50 ms | Estimate |
| Produce to Kafka and consume | under 100 ms on one broker | Estimate, local only |
| Update projection (weekend timeline) | under 50 ms | Estimate |
| Push to SSE subscribers | under 100 ms | Estimate |
| Client render | depends on the client | Out of our control |

Provider latency will dominate. We cannot promise a few seconds until it is measured. The first live
measurement is a gate for the live phase.

## 4. Rate limiting and backoff (see config/ingestion-policy.yaml)

- **Live data uses MQTT push, not REST polling.** Polling at a live rate would hit any REST limit quickly.
- **REST (OpenF1) is for backfill and catch-up only,** through a token bucket at 2 requests per second.
  The real limit is UNVERIFIED. On HTTP 429 we honour `Retry-After`, capped at 10 seconds during a live session.
- **MQTT reconnect backoff is capped at 2 seconds** (0.25 s, 0.5 s, 1 s, 2 s, then 2 s, with plus or minus 20 percent jitter).
  A longer wait would break the delay budget.
- **Silence detection:** no message for 10 seconds marks the stream DEGRADED and raises an alert.
- **Historical (Jolpica) backoff** is 5 to 60 seconds with full jitter. A slow batch is acceptable there.
- **Retries must be bounded.** An unbounded retry during a live session hides a failure and makes the delay
  grow silently. Exhausting the retries moves the stream to a failed state.

## 5. Recency order and the weekend view

- **Weekend key:** OpenF1 provides `meeting_key`, which groups all sessions of one weekend. Jolpica has
  season and round. A mapping table joins them, keyed by season and round, and is built from dates and names.
  UNVERIFIED for every round until the mapping is checked against both sources.
- **Event ordering:** Kafka orders events only within one partition. We key partitions by session, so order
  holds within a session. Order across sessions is not guaranteed by Kafka.
- **Recency across sessions** is therefore computed in the projection, by provider timestamp, then by
  session key and sequence number as tie-breakers.
- **Clock caveat:** provider timestamps from different streams may not agree exactly. Recency is correct
  to within that skew, not to the millisecond. Record receive time as well, so skew can be measured.
- **Newest-first endpoint:** `GET /weekend/{meetingKey}/events?limit=N` returns the newest N events across all
  sessions, with a cursor (timestamp and event ID) for older pages.
- **Live stream:** SSE delivers the same events in arrival order and includes the Last-Event-ID for resume.

## 6. Caches

- **Historical catalog:** immutable after a race. Long-lived HTTP caching and per-instance Caffeine.
- **Live weekend projection:** in memory per API instance, updated on each event. No TTL cache, because
  a stale cache breaks the recency order. Serve from the projection, not from a cache.
- **Predictions:** cached by model version and cutoff, since both are fixed once stored.
- **Client-side:** Jolpica sends max-age 600. We honour it for historical data only.
- **Redis:** not added until several API instances need one shared live view and measurement shows the need.

## 7. Concurrency

- **Ingesters:** exactly one active ingester per session, enforced with a lease in PostgreSQL.
- **Kafka:** one partition per session key, so events for one session stay ordered. Consumer lag is the
  scaling signal.
- **API replicas:** each replica has its own consumer group, so every replica sees every event.
- **SSE subscribers:** each has a bounded queue. When the queue is full, the subscriber is disconnected and
  must resume with Last-Event-ID.
- **Target concurrent viewers:** NOT YET GIVEN. Sizing depends on this number. Open item.

## 8. Historical data requirements for the live path

- **Live replay source:** OpenF1 from 2023 onward. Earlier weekends have no live data, so replay tests use
  2023 to 2026 sessions only.
- **Weekend window:** the weekend view needs the current weekend's events. Older weekends are served from
  stored events, not the live stream.
- **Retention:** keep live events for the current weekend in full. Older weekends can keep only summaries.
  Raw telemetry is the large item. Its size is an estimate and must be measured on a real session.

## 9. Failure modes

| Failure | Behaviour |
|---|---|
| MQTT disconnect | Reconnect with capped backoff. State shows DEGRADED. Events missed during the gap are backfilled from REST. |
| Provider silence for 10 seconds | DEGRADED, alert raised, no silent stall. |
| Kafka unavailable | Ingester buffers a bounded amount, then stops and alerts. No silent drops. |
| API replica restart | Subscribers reconnect and resume with Last-Event-ID. |
| Ingester crash | Lease expires, another ingester takes over. Duplicate events are dropped by (session, sequence). |
| Clock skew between streams | Recency is approximate within the skew. Logged as a metric. |

## 10. Open items

1. Target concurrent viewers (needed for sizing).
2. Confirm the first live measurement of provider latency.
3. Austin scope decision (section 2).
4. OpenF1 REST real limit (measure with live responses, honour 429).
5. Jolpica to OpenF1 weekend mapping, checked for every round from 2023 onward.
6. Sprint qualifying results: OpenF1 shape differs from Jolpica (driver number, durations in seconds, no
   constructor field). The adapter must map these and must not reuse the Jolpica identity mapping as is.

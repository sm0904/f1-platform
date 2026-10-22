# F1 Platform: System Design

Status: Draft v0.1
Scope: data ingestion, storage, analytics, live timing, predictions, API, and frontend.

---

## 1. Goals and non-goals

**Goals**
- Serve historical F1 data (1950 to present) and live session data through one coherent API.
- Provide analytical insights: driver and team comparisons, stint and tyre analysis, pit strategy, lap-time evolution.
- Provide probabilistic race and qualifying predictions with honest evaluation against held-out results.
- Stay modular and horizontally scalable, with each concern replaceable behind an interface.

**Non-goals (for now)**
- Redistributing official F1 timing or media. The platform is a personal, non-commercial portfolio project.
- Real-time betting-grade latency. Live updates target a few seconds of end-to-end delay, not milliseconds.
- Multi-tenant SaaS. Authentication exists only for admin and personalization features.

---

## 2. Data sources

| Source | Role | Coverage | Access | Constraints |
|---|---|---|---|---|
| Jolpica-F1 | Historical results, standings, schedules, circuits | 1950 to present | Public REST, Ergast-compatible; DB dumps | Rate-limited; verify current limits; treat as read-mostly |
| OpenF1 | Live timing, laps, stints, car telemetry, positions, weather, race control, pit stops | 2023 to present | Public REST; MQTT for live streams | Unofficial; no affiliation with F1; coverage gaps possible |
| FastF1 (Python) | Reference for telemetry parsing and data quirks | Varies | Library, not a runtime dependency | Used for study and validation only |

Design rules for providers:
- Each provider has exactly one adapter. Adapters map provider DTOs into domain records. The domain never sees provider shapes.
- Every stored row carries a `source_key` and an ingest-run reference, so any value can be traced to its origin.
- Overlapping coverage (e.g. 2023+ lap data available from both sources) is resolved by an explicit precedence rule, recorded in an ADR, and never by accident.

---

## 3. Data volume estimates

These are order-of-magnitude estimates to drive design. Verify against real loads before committing to capacity numbers.

**Historical**
- About 1,200 Grand Prix races since 1950, each with roughly 20 classified entries: on the order of 25,000 race result rows.
- Including qualifying, sprints, and practice, plus standings snapshots: low hundreds of thousands of rows.
- Timed lap data, which starts around 1996: roughly 20 cars times 60 laps times 22 races per season over 30 seasons, so under a few million lap rows.

Conclusion: historical data fits comfortably in a single PostgreSQL instance. Partitioning isn't needed yet.

**Live telemetry (the real volume driver)**
- Car telemetry at about 3.7 samples per second per car, for 20 cars over a 2-hour race: about 530,000 samples per race for each telemetry stream.
- Position and location streams are comparable in size.
- A season, including practice and qualifying, reaches tens of millions of telemetry rows.

Conclusion: telemetry needs time-based partitioning, and it should be queried by pre-aggregated summaries in normal use. Raw telemetry stays in object storage (Parquet, partitioned by season, round, and session), with the database holding indexes and summaries.

**Traffic**
- Read traffic peaks during live sessions, when many clients subscribe to the same stream. Fan-out, not write volume, is the scaling problem.
- Design for a fan-out of thousands of concurrent SSE clients per instance-set, with a single ingestion writer per session.

---

## 4. Architecture overview

```
   Jolpica REST ─┐                     ┌──────────── Read API (Dropwizard, stateless, N replicas)
                 │                     │              REST: historical, insights, predictions
   OpenF1 REST ──┼─► Ingestion ──► PostgreSQL ◄──────┤
                 │   (batch loaders)   │   (+ Object  │ SSE: live updates
   OpenF1 MQTT ──┘   (live ingester)   │    storage)  │
                          │            │              │
                          ▼            ▼              ▼
                    Event log    Analytics       Cache (Caffeine per node,
                    (in-process  + summary       Redis later for shared)
                     now, Kafka  materializers
                     later)           │
                          │           ▼
                          └──► Prediction service ─► model artifacts
                                 (features, train,      (versioned, ONNX)
                                  serve)
```

The system starts as a modular monolith with clear module boundaries (`ingestion`, `analytics`, `prediction`, `api`). Modules communicate through interfaces and events, so any module can become its own service later without rewriting the others. Splitting early costs operational complexity without a scaling benefit at this volume.

### Module boundaries

| Module | Responsibility | Depends on |
|---|---|---|
| domain | Records, invariants, domain services | nothing |
| ingestion | Provider adapters, loaders, live ingester, mapping, reconciliation | domain, persistence ports |
| persistence | JDBI or JPA adapters, Flyway migrations | domain |
| analytics | Metric computation, summary materialization | domain, persistence ports |
| prediction | Feature building, model training, scoring, evaluation | domain, analytics |
| api | Dropwizard resources, DTOs, SSE, configuration | domain, analytics, prediction |

Dependency rule: arrows point inward toward the domain. The domain depends on no framework, and the api module depends on no provider.

---

## 5. Data model layers

1. **Raw.** Provider payloads, stored as received (JSONB for small payloads, object storage for telemetry), with source, endpoint, fetch time, and checksum. Immutable and append-only.
2. **Canonical.** Domain entities in normalized PostgreSQL tables, as built in V1. Keyed by internal IDs, with `source_key` for provenance.
3. **Derived.** Materialized summaries and features: stint summaries, tyre degradation coefficients, driver ratings, feature sets for models. Rebuildable from canonical data, and versioned by the code that produced them.

Rebuildability is a core requirement. Any derived table can be dropped and recomputed from canonical data. That makes analytics changes safe to deploy and gives a clean story for schema and logic evolution.

---

## 6. Ingestion design

**Batch (historical)**
- A scheduled job discovers new rounds and results, fetches by endpoint, and writes raw payloads before mapping.
- Idempotent upserts keyed by `source_key`, so reruns don't duplicate data.
- Each run records start and end time, counts, and errors in an `ingest_run` table.
- Backfill is a separate command, not part of the normal schedule, and it uses the same code path.

**Live**
- The live ingester subscribes to OpenF1 MQTT topics for the active session. It normalizes messages into domain events, such as `LapCompleted`, `PositionChanged`, `PitStopRecorded`, and `RaceControlMessage`.
- Events are sequenced by session time and processed in order per session. Out-of-order arrival is buffered briefly and then flushed.
- A replay source reads a recorded session file at controlled speed. All live features are developed and tested against replay. No race weekend is required to develop or test.
- Reconnect logic is an explicit state machine: connecting, streaming, degraded, reconnecting, and ended. The state is exposed through health checks and metrics.

**Event flow**
```
MQTT message ─► decode ─► normalize ─► sequence (per session) ─► persist raw
                                                  │
                                    ┌─────────────┼───────────────┐
                                    ▼             ▼               ▼
                              write canonical  update live   publish to
                              (batched)        state model   SSE subscribers
```

---

## 7. Asynchronous processing and messaging

Phase 1 uses an in-process event bus behind an `EventPublisher` interface, with a bounded queue and explicit backpressure. This is enough for one ingester and one API instance.

Phase 3 moves live events to Kafka (or Redpanda, which is Kafka-compatible and lighter to run locally):

| Topic | Key | Producers | Consumers |
|---|---|---|---|
| `f1.live.raw` | session ID | live ingester | normalizer |
| `f1.live.events` | session ID | normalizer | canonical writer, live-state, SSE fan-out |
| `f1.analytics.requests` | season/round | API, scheduler | analytics workers |

Partitioning by session ID preserves order within a session, which is the only ordering that matters. Consumer lag is the autoscaling signal on Kubernetes.

Delivery semantics: at-least-once delivery, with idempotent consumers keyed by `(session, sequence number)`. Exactly-once isn't needed when consumers are idempotent.

Heavy analytics (for example, recomputing tyre models for a full season) runs as background jobs, not request-time work. API responses return a job reference, and the result is polled or streamed when ready.

---

## 8. Analytics design

**Insight categories**
- Season and driver comparison: points progression, qualifying-to-race conversion, head-to-head records.
- Lap and race pace: fuel-corrected lap times, race pace estimates that exclude safety cars and pit laps.
- Tyre and stint analysis: degradation per compound, using a fitted model per stint.
- Pit strategy: pit loss by circuit (time lost versus a neighbouring-lap baseline), undercut and overcut outcomes.
- Circuit profiles: overtaking rate, average speed, and characteristic lap time.

**Method**
- Metrics are pure functions in the analytics module, with inputs from canonical data and outputs written to derived tables. They have no hidden state.
- Each metric has a version. Changing a formula bumps the version, and the derived rows record which version produced them.
- Fuel correction, safety car exclusion, and pit-lap removal are explicit, individually tested transformations, not hidden in the query.

**Quality**
- Every metric has a fixture test with a hand-checked small race, and a property test for invariants (for example, corrected lap time is never negative, and stints partition the laps they cover).

---

## 9. Prediction design

**Targets**
- Race finishing order, expressed as a probability distribution over positions.
- Qualifying position for each driver.
- Points outcomes, derived from the finishing distribution.

**Model approach (staged)**
1. Baseline: driver and constructor ratings using Elo-style updates over races, with a prior from the previous season. Easy to explain, easy to beat.
2. Plackett-Luce ranking model: a standard approach for ordered outcomes, which fits a strength parameter per driver and produces full finishing distributions.
3. Feature-based gradient-boosted model: features include qualifying gap to pole, grid position, recent form, circuit history, team pace estimate, and tyre strategy priors. Used only if it beats the earlier stages on held-out data.

**Evaluation (the part that makes it credible)**
- Time-based splits only. Train on seasons before S, test on S. Random splits leak future information and are not allowed.
- Metrics: log loss and Brier score for winner and podium probabilities, Spearman correlation for finishing order, and calibration plots (reliability diagrams).
- A published comparison table on the site: baseline versus each model, per season, against the actual results. Honest reporting of where the models are wrong is part of the design, not a weakness.
- Predictions are stored with their timestamp and the information available at that time. A prediction made after qualifying can't use race data, and the store enforces that.

**Serving**
- Models are trained offline and exported to a versioned artifact (ONNX or a serialized Java model).
- The prediction module loads the artifact at startup and exposes a scoring endpoint. A model version is part of every prediction record, so results stay reproducible.

**Language choice:** training in Python is standard in industry (scikit-learn, XGBoost, PyTorch), but the platform is Java-first. Options are covered in ADR-0005.

---

## 10. API design

**Style:** REST for resources, SSE for live streams. Resource-oriented URLs, versioned under `/api/v1`.

| Area | Example endpoints |
|---|---|
| Historical | `GET /api/v1/seasons/{year}`, `GET /api/v1/seasons/{year}/races/{round}/results` |
| Drivers and teams | `GET /api/v1/drivers/{id}/results`, `GET /api/v1/constructors/{id}/standings` |
| Insights | `GET /api/v1/insights/tyre-degradation?season=2024&compound=SOFT` |
| Live | `GET /api/v1/live/sessions/current`, `GET /api/v1/live/sessions/{id}/stream` (SSE) |
| Predictions | `GET /api/v1/races/{id}/prediction`, `GET /api/v1/models/{version}/evaluation` |
| Operations | `/healthcheck`, `/metrics` on the admin port, not exposed publicly |

**Conventions**
- Pagination with cursors for large collections. Offset pagination is avoided for telemetry.
- Caching headers: historical responses are immutable and cacheable for long periods. Live responses are not.
- Errors follow RFC 9457 problem details.
- Response DTOs are separate from domain records. A mapping layer owns the translation.
- Long-running analytics return `202 Accepted` with a job location.

---

## 11. Dropwizard considerations

**What Dropwizard gives us:** Jetty, Jersey, Jackson, configuration validation, Metrics, health checks, and a separate admin port. The admin port is the right place for health and metrics, and it stays off the public network.

**Where to be deliberate**
- **SSE on Jersey.** Jersey supports SSE through its SSE feature. Keep one long-lived response per client, use bounded per-subscriber queues, and drop slow consumers rather than buffering without limit.
- **Async resources.** Jersey's async processing lets blocked calls release request threads. Use it for slow analytics endpoints, but keep the database connection pool sized deliberately, because it's usually the real bottleneck.
- **Connection pools.** Configure HikariCP explicitly. Pool size should be based on the database's capacity, not on request thread counts.
- **Metrics.** Dropwizard Metrics covers request timing, pool stats, and custom business counters. Export to Prometheus through a reporter so Kubernetes dashboards work. Verify the current reporter module before depending on it.
- **Health checks.** Separate liveness (process is up) from readiness (database and model artifacts are loaded, and live ingester state is acceptable). Kubernetes probes use these.
- **Configuration.** Typed YAML, validated at startup. Secrets come from environment variables or a secret store, never from the YAML checked into Git.
- **Lifecycle.** `Managed` objects own the ingester, the event bus, and scheduled jobs. Start and stop order is explicit in the composition root.

**Spring mapping** (for interview fluency): Dropwizard's composition root plays the role of Spring's application context. `Managed` maps to `SmartLifecycle`, the admin port maps to Actuator's management port, and Jersey resources map to `@RestController`.

---

## 12. Scalability and deployment

**API tier:** stateless replicas behind a load balancer. Horizontal scaling is the default. Session-affine routing is needed only for SSE, and only if the fan-out design requires shared state.

**Ingestion tier:** exactly one active live ingester per session, enforced with a lease in PostgreSQL (or Kubernetes leader election). Multiple ingesters would duplicate events.

**Analytics and prediction workers:** scaled by queue depth or Kafka lag. Stateless, idempotent, and interruptible.

**Database:** PostgreSQL primary with read replicas once read load justifies them. Telemetry partitioned by session date. Object storage for raw telemetry.

**Caching:** Caffeine per instance for hot reads. Redis is added when multiple instances need a shared cache, and it's justified by measurement, not assumed.

**Deployment:** containerized with multi-stage Docker builds, deployed to Kubernetes with health probes, resource requests and limits, and HPA. Infrastructure described in Terraform. Target platform is a decision (see section 15). Local development uses Docker Compose with PostgreSQL and Redpanda, and kind or minikube for Kubernetes testing.

**Load testing:** k6 or Gatling scripts simulating a race weekend: many SSE subscribers, periodic historical reads, and a replayed live session. Results are recorded in the repo as a benchmark report.

---

## 13. Observability and operations

- **Metrics:** request latency and error rate per endpoint, ingestion lag (provider timestamp to persisted timestamp), SSE subscriber count, queue depth, and model scoring latency.
- **Logs:** structured JSON with session, round, and request correlation IDs.
- **Tracing:** OpenTelemetry spans across ingestion, persistence, and API calls. Adds value once Kafka is in place.
- **Alerts:** ingestion stalled during a live session, provider error rate above a threshold, and no new canonical rows for an active session.
- **Runbooks:** short docs in `docs/runbooks/` for common failures: provider outage, MQTT reconnect storm, failed migration, and model artifact missing.

---

## 14. Quality and testing strategy

| Layer | Approach |
|---|---|
| Domain | Unit tests for every invariant. Property-based tests with jqwik for ordering and partitioning properties. |
| Adapters | Contract tests against recorded provider fixtures with WireMock. Changes in provider shape fail the build. |
| Persistence | Integration tests on PostgreSQL with Testcontainers, including Flyway migrations applied from scratch. |
| Analytics | Hand-checked fixture races with expected outputs. Versioned golden files. |
| Prediction | Backtests on held-out seasons. Regression guard: a new model must beat the baseline or it isn't promoted. |
| API | Resource tests with Dropwizard's test extension. Contract tests for response schema. |
| Live | Replay-based end-to-end tests with fixed inputs and expected event sequences. |
| Performance | Scripted load scenarios, with results committed as benchmark reports. |

---

## 15. Open decisions

These are recorded as ADRs under `docs/adr/` once decided.

| ID | Decision | Options | Recommendation |
|---|---|---|---|
| ADR-0001 | Frontend stack | React + TypeScript + Vite; Next.js; server-rendered Dropwizard views | React + TypeScript + Vite, static build served separately |
| ADR-0002 | Messaging for live events | In-process bus; Kafka; Redpanda | In-process first, Redpanda in Phase 3 |
| ADR-0003 | Live push mechanism | SSE; WebSocket | SSE: simpler, HTTP-native, adequate for one-way updates |
| ADR-0004 | Persistence access | JDBI; JPA/Hibernate; jOOQ | JDBI: explicit SQL, suits time-series queries |
| ADR-0005 | Prediction training and serving | Python training + ONNX; pure Java (Tribuo or custom) | Python training with ONNX serving in Java, documented; keep a pure-Java baseline |
| ADR-0006 | Deployment target | OCI (Kubernetes); local kind only; AWS | Local kind first; OCI as the production target |

---

## 16. Phased roadmap

| Phase | Deliverable | Exit criterion |
|---|---|---|
| 0 | Domain model, V1 schema, sandbox git and bundle | Done |
| 1 | Jolpica adapter, historical loader, read API for seasons, races, results | Full backfill runs idempotently; API tests pass against Testcontainers |
| 2 | Analytics: standings, pace, pit-loss, tyre degradation | Hand-checked fixtures pass; insights endpoints live |
| 3 | OpenF1 adapter, replay harness, live ingester, SSE stream | Replayed session produces expected event sequence and SSE output |
| 4 | Kafka/Redpanda event path, background analytics jobs | Consumer lag observable; restart mid-session loses no events |
| 5 | Prediction baseline (Elo), Plackett-Luce, evaluation page | Backtest report published with calibration plots |
| 6 | Frontend: insights, live dashboard, predictions | Core pages usable on desktop and mobile |
| 7 | Hardening: Docker, Kubernetes, Terraform, observability, load test | Load report committed; runbooks written |

---

## 17. Risks

- **Provider changes or outages.** Mitigated by adapters, contract tests, raw payload retention, and graceful degradation.
- **Unofficial data quality.** Mitigated by validation rules, reconciliation reports, and documented known gaps.
- **Licensing.** OpenF1 and Jolpica data are used under their stated terms for a personal, non-commercial project. Branding avoids implying an official F1 product.
- **Scope.** The roadmap is long. Each phase ends in something runnable, so the project remains demonstrable at every stage.

---

## Appendix A. Terminology: Control Plane and Data Plane

The platform is split into two planes. The control plane is the Dropwizard backend that governs work and serves results. The data plane is every engine that does the work on data, including the predictor, ingestion, ETL, and plain data access. Engines claim jobs through a shared job contract. The full control-plane design is in `CONTROL_PLANE.md`. The data-plane design (ingestion, training, prediction, and outcome generation) will be written as `DATA_PLANE.md`.
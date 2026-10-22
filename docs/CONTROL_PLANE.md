# Control Plane: Backend Components

Status: Draft v0.1
Scope: the backend application that governs the platform. Predictions, ingestion, training, and outcome generation are covered separately in the data plane document.

---

## 1. Terminology

| Term | Meaning in this project |
|---|---|
| Control plane | The Dropwizard application. It accepts requests, governs work, tracks state, and serves results. It never performs heavy per-record processing. |
| Data plane | Everything that does the work on data: ingestion, transforms, feature building, model training, prediction, and outcome generation. Each kind of work is a data-plane engine. |
| Job | A unit of data-plane work described by a type, a version, parameters, and an idempotency key. |
| Engine | A data-plane component that claims and executes jobs of one or more types. The predictor is one engine. Ingestion, ETL, and plain data access are others. |

The boundary rule: the control plane decides what runs, when, with which parameters, and who may see the result. The data plane decides how the work is done. The control plane must not know how an Elo rating or a Plackett-Luce likelihood is computed. It knows only that a model version exists, what its evaluation metrics are, and whether it is promoted.

This separation is what lets us add new data operations without touching the control plane. A new engine registers new job types. The API and job machinery stay the same.

---

## 2. Framework and runtime

- Dropwizard 5.0.2 on Java 21 (Java 17 minimum), using the Jakarta namespace and Jetty 12.
- Jersey for resources, Jackson for JSON, Jakarta Validation for request validation.
- Explicit composition root in the Application class. No DI container at this stage; the choice is recorded as an ADR so it can be revisited.
- Dropwizard Metrics for timing and business counters, exported to Prometheus. Health checks on the admin port.
- Virtual threads: Java 21 supports them, and they would suit blocking I/O in the job API. Adopt only after confirming support in our Dropwizard and Jetty versions. Record the decision in an ADR.

Why Dropwizard for the control plane: its opinions (typed configuration, admin port, health checks, metrics, lifecycle) match what an operated service needs. It also keeps the wiring visible, which is useful for learning the patterns.

---

## 3. Internal structure

```
control-plane/
  api/           Jersey resources, request and response DTOs, problem mapping, SSE endpoints
  jobs/          job submission, state machine, claim and lease protocol, idempotency
  catalog/       read services for seasons, races, results, drivers, constructors, circuits
  insights/      read services over derived analytics tables
  predictions/   read services for stored predictions and their metadata
  models/        model registry: versions, metadata, metrics, promotion state
  live/          live session control: which sessions are active, ingester leases
  config/        typed configuration classes and validation
  ops/           health checks, readiness logic, metrics registration
```

The control plane depends on the domain module and on persistence ports. It does not depend on any data-plane engine code. Engines depend on the domain and on the same job contract.

Module responsibilities are summarized in the table below. Each row names what the module owns, so a reviewer can tell where a change belongs.

| Module | Owns | Does not own |
|---|---|---|
| api | HTTP contract, validation, error shape | Business rules; it delegates to services |
| jobs | Job lifecycle, claim and lease, retries, idempotency | What any job computes |
| catalog | Read queries over canonical data | Ingestion of that data |
| insights | Query and paging over derived metrics | Metric formulas (data plane) |
| predictions | Read and filter stored predictions | Model logic, scoring |
| models | Registry metadata, promotion workflow | Training and artifact contents |
| live | Session activation, ingester lease records | Live event processing |
| config | Startup validation | Runtime tuning |

---

## 4. Job protocol (the central pattern)

Long-running and data-heavy work never runs inside a request. The control plane records a job and returns immediately; an engine picks it up.

**Job lifecycle**

```
            submit
  (none) ──────────► QUEUED ──claim──► RUNNING ──────► SUCCEEDED
                       │                 │  │
                       │                 │  └──fail──► FAILED ──retry──► QUEUED
                       │                 │               (until max attempts)
                       │                 └──lease expired──► QUEUED (reclaimed)
                       └──cancel──► CANCELLED
```

**Mechanics (PostgreSQL)**
- Jobs live in a `job` table: id, type, version, parameters (JSONB), idempotency key (unique per client), state, attempts, lease owner, lease expiry, result reference, error detail, timestamps.
- Engines claim work with `SELECT ... FOR UPDATE SKIP LOCKED`, so many engine instances can poll the same table without contention or double claims.
- A claim sets a lease expiry. The engine renews the lease while working (heartbeat). If an engine dies, the lease expires and another engine reclaims the job.
- Retries use exponential backoff with a cap and a maximum attempt count. Exhausted jobs move to FAILED with the last error recorded.
- Every state transition is written in a `job_event` table, giving a full audit trail for debugging and for the portfolio write-up.

**Idempotency**
- Clients send an `Idempotency-Key` header on job submission. Repeating the same request returns the original job, not a duplicate.
- Job handlers must be idempotent by design, because at-least-once execution is the normal case after lease expiry.

**Why not a message queue first?** A table with SKIP LOCKED gives durability, visibility, and transactional submission with no extra infrastructure. It is adequate for the expected job rate. The job contract is written so the transport can later move to Kafka or Redpanda without changing job types, parameters, or API behaviour. That transport swap is an ADR.

**Transactional outbox (later):** when a job submission must be atomic with other state changes, such as "create the prediction request and enqueue its job," the outbox pattern records both in one transaction and relays events afterwards. Introduce it only when a real case requires it.

---

## 5. API design

Base path `/api/v1`. Resource-oriented, JSON, with OpenAPI generated from the annotated resources so the frontend contract is machine-readable.

### 5.1 Resource map

| Group | Endpoints (representative) | Notes |
|---|---|---|
| Catalog | `GET /seasons`, `GET /seasons/{year}`, `GET /seasons/{year}/races`, `GET /races/{raceId}`, `GET /races/{raceId}/sessions`, `GET /races/{raceId}/sessions/{type}/results`, `GET /drivers/{driverId}`, `GET /constructors/{constructorId}`, `GET /circuits/{circuitId}` | Historical, cacheable for long periods |
| Standings | `GET /seasons/{year}/standings/drivers`, `GET /seasons/{year}/standings/constructors` | Derived from results; returns the computation version |
| Insights | `GET /insights/...` (pace, tyre degradation, pit loss, head-to-head) | Read-only over derived tables; parameters validated and bounded |
| Predictions | `GET /races/{raceId}/predictions`, `GET /races/{raceId}/predictions/{predictionId}` | Each response includes the model version and the information cut-off time |
| Models | `GET /models`, `GET /models/{version}`, `GET /models/{version}/evaluation` | Registry read. Promotion is admin-only |
| Live | `GET /live/sessions` (active), `GET /live/sessions/{sessionId}/stream` (SSE) | Stream carries typed events and a sequence number for resume |
| Jobs | `POST /jobs`, `GET /jobs/{jobId}`, `GET /jobs?state=...`, `POST /jobs/{jobId}/cancel` | Admin or internal use; submission requires `Idempotency-Key` |
| Admin | `POST /models/{version}/promotion`, `POST /live/sessions/{sessionId}/ingester` | Protected; requires a credential (see 5.5) |

Operational endpoints (`/healthcheck`, `/metrics`, `/ping`) live only on the admin port, never on the public port.

### 5.2 Response conventions

- **Pagination:** cursor-based, with an opaque `next` token. Offset pagination is not used for telemetry-scale collections.
- **Envelope:** collection responses return `{ "data": [...], "page": { "next": "..." } }`. Single resources return the object directly. This keeps the common case simple and leaves room for metadata.
- **Versioned computations:** any derived value (standings, insights, predictions) includes the version of the code or model that produced it. Clients can cite results exactly.
- **Timestamps:** ISO 8601 in UTC, always with an explicit offset.
- **Errors:** RFC 9457 problem details (`application/problem+json`), with a stable `type` URI per error class and a correlation ID.

### 5.3 Caching

- Historical catalog responses are immutable after a race completes, so they send long `Cache-Control` lifetimes and an `ETag` derived from the version.
- Derived responses (insights, standings) use an ETag tied to the computation version, so clients revalidate cheaply with `If-None-Match` and get `304` when nothing changed.
- Live and prediction-in-progress responses are `no-store` or very short-lived.
- Server-side, Caffeine caches hot catalog reads per instance. Shared caching waits until measured need.

### 5.4 Request handling rules

- All query parameters are validated against explicit bounds (for example, season year within the supported range, page size capped).
- Unknown query parameters are rejected rather than ignored, so typos surface early.
- Request bodies are validated at the boundary. Domain invariants are enforced again in the domain, never trusted from the API layer alone.
- Mutating endpoints are idempotent or require an idempotency key.

### 5.5 Authentication and authorization

- Read endpoints are public. The data is public, and anonymous read access keeps the frontend and the portfolio demo simple.
- Admin and job-submission endpoints require a bearer token. For the portfolio, a token validated against a configured key is enough. Production would use OAuth2/OIDC through an identity provider, with scopes such as `jobs:write` and `models:promote`.
- Secrets are never in the YAML checked into Git. They come from environment variables or a secret store.

---

## 6. Live session control

The control plane decides which live sessions are being ingested and who is allowed to stream them. It does not process telemetry.

- An active-session record is created when a session is scheduled to start, or manually by an admin.
- Ingester assignment uses a lease in PostgreSQL. Exactly one ingester holds the lease for a session at a time. The lease is renewed by heartbeat, and expiry triggers reassignment.
- The SSE stream endpoint reads from the data plane's live event store, not from the ingester directly. Clients resume with the last sequence number they received (`Last-Event-ID`).
- Per-subscriber queues are bounded. A slow subscriber is disconnected rather than allowed to grow memory without limit. This protects the instance and is covered by tests.

---

## 7. Cross-cutting patterns

| Pattern | Where it applies | Why |
|---|---|---|
| Ports and adapters | Persistence and every engine boundary | Keeps domain and control plane free of storage and provider details |
| Command and query separation | Job submission (command) versus catalog and insight reads (queries) | Reads scale independently; writes stay small and auditable |
| Idempotency keys | Job submission, promotion, lease operations | Safe retries from clients and engines |
| Lease with heartbeat | Job claims, live ingester assignment | Detects dead workers without a central coordinator |
| Bounded queues and backpressure | SSE subscribers, job submission rate | Prevents memory growth and cascading failure |
| Bulkheads | Separate thread pools or executors for live streaming, job API, and catalog reads | A slow analytics query cannot starve live streams |
| Circuit breaker | Calls from the control plane to any engine-owned service | Fails fast during engine outages |
| Graceful shutdown | Managed objects stop accepting work, drain in-flight requests, release leases | Clean deploys in Kubernetes, no orphaned jobs |
| Versioned outputs | Every derived result carries its producing version | Reproducibility and honest comparison over time |
| Problem details | All error responses | Consistent, machine-readable failures |

---

## 8. Feature outline by phase

This maps the control-plane features to the roadmap in DESIGN.md.

| Phase | Control-plane features | Exit criterion |
|---|---|---|
| 1 | Catalog read API, pagination, caching headers, problem details, admin port with health checks and metrics | Catalog endpoints pass resource tests against Testcontainers PostgreSQL |
| 2 | Standings and insight endpoints, parameter validation, ETag revalidation, job table and submission API | Insight endpoints return versioned results from fixture data |
| 3 | Live session records, ingester leases, SSE stream with resume, bounded subscriber queues | Replayed session streams the expected events to a test client, and resume works after a disconnect |
| 4 | Job claim and lease protocol with SKIP LOCKED, retries, job event audit, cancellation | Killing an engine mid-job results in the job being reclaimed and completed exactly once in effect |
| 5 | Prediction read API, model registry endpoints, promotion workflow with idempotency | A promotion can be repeated safely; prediction responses show model version and cut-off time |
| 6 | OpenAPI publication, CORS for the frontend origin, API contract tests | Frontend builds against the generated client without hand-written types |
| 7 | Auth for admin and job endpoints, rate limiting on public endpoints, Kubernetes probes, load-test-driven tuning | Load test report committed; probes wired to readiness logic |

---

## 9. Testing approach for the control plane

- **Resource tests** with Dropwizard's test extension: request in, response out, validation and error shapes checked.
- **Persistence integration tests** with Testcontainers PostgreSQL, including job claim races: several threads claiming from one queue must never receive the same job.
- **Lease tests:** simulated expiry and reclaim, verifying single effective execution.
- **Contract tests:** the OpenAPI document is generated and compared against a committed copy, so an unintended API change fails the build.
- **Load tests** for SSE fan-out and job submission, with results committed as benchmark reports.

---

## 10. Open decisions for the control plane

| ID | Decision | Options | Recommendation |
|---|---|---|---|
| CP-1 | Job transport | PostgreSQL table with SKIP LOCKED; Kafka or Redpanda | Table first; transport swap later behind the same contract |
| CP-2 | Virtual threads | Adopt now; defer until verified on our versions | Verify first, then decide, recorded as an ADR |
| CP-3 | Dependency injection | Manual composition root; Guice or HK2 | Manual, for transparency |
| CP-4 | API contract tooling | Generate OpenAPI from annotations; hand-maintained spec | Generate, and commit the output for contract tests |
| CP-5 | Admin authentication | Static token; OAuth2/OIDC | Static token for the portfolio, with OIDC described in the design |
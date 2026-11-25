# 02 · Product Requirements (PRD)

> Status: Approved · Phase: Requirements · Last updated: 2026-11-25

## 1. Goal

Give every tenant of a cloud platform a **reliable, observable, secure** way to
deliver business events to external HTTP consumers — while guaranteeing that
no tenant can degrade service for another.

## 2. Non-goals (v1)

- Inbound webhooks (receiving from third parties).
- Non-HTTP transports (email, SMS, gRPC).
- Payload transformation / templating.
- Strict global ordering of events.

## 3. Functional requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-1 | A platform admin can create, list, suspend tenants and set their plan limits. | Must |
| FR-2 | A tenant can create, list and revoke API keys. Keys are shown once and stored hashed. | Must |
| FR-3 | A tenant can register HTTPS endpoints, subscribe them to event types (wildcards such as `claim.*`), disable them and rotate their signing secret. | Must |
| FR-4 | A tenant can publish an event (`type`, JSON `payload`) and receive `202 Accepted`. Publishing is atomic: the event and its fan-out are committed together or not at all. | Must |
| FR-5 | Publishing with an `Idempotency-Key` returns the original event instead of creating a duplicate. | Must |
| FR-6 | Every delivery is signed per the Standard Webhooks scheme (`webhook-id`, `webhook-timestamp`, `webhook-signature`). | Must |
| FR-7 | Failed deliveries are retried with exponential back-off + jitter. Permanent failures (`4xx` except `408/409/425/429`) stop immediately. | Must |
| FR-8 | Deliveries that exhaust retries move to a dead-letter state and can be replayed individually or in bulk. | Must |
| FR-9 | Every attempt is recorded (status, latency, error, response excerpt) and queryable. | Must |
| FR-10 | Ingestion is rate-limited per tenant (token bucket). Exceeding the limit returns `429` with `Retry-After` and `RateLimit-*` headers. | Must |
| FR-11 | Delivery concurrency is capped per tenant; endpoints that keep failing are short-circuited. | Must |
| FR-12 | A tenant can view delivery statistics (success rate, latency percentiles, volume) over a time window. | Should |
| FR-13 | A web dashboard exposes FR-2, 3, 8, 9 and 12. | Should |
| FR-14 | A developer can send a test event to an endpoint from the dashboard. | Could |

## 4. Non-functional requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-1 | Reliability | No acknowledged event is ever lost (at-least-once delivery). |
| NFR-2 | Isolation | A tenant whose endpoints all time out must not raise p95 delivery latency of other tenants by more than 10 %. |
| NFR-3 | Scalability | API and workers are stateless and horizontally scalable; workers scale on queue depth. |
| NFR-4 | Latency | p95 ingestion latency < 50 ms; p95 time-to-first-attempt < 2 s under nominal load. |
| NFR-5 | Security | Keys hashed (SHA-256), signing secrets encrypted at rest (AES-256-GCM), SSRF protection on endpoint URLs, no redirects followed. |
| NFR-6 | Observability | RED metrics per tenant, distributed traces from ingestion to delivery, structured JSON logs with trace IDs. |
| NFR-7 | Operability | Health/readiness probes, graceful shutdown, zero-downtime rolling deploys, schema migrations versioned. |
| NFR-8 | Portability | Runs locally with one `docker compose up`; deploys to Kubernetes via Helm; AWS infra provisioned via Terraform. |
| NFR-9 | Quality | ≥ 80 % line coverage on domain logic; integration tests against real Postgres, Redis and SQS (LocalStack). |

## 5. Success metrics

- Eventual delivery rate ≥ 99.9 % for endpoints that recover within the retry window.
- Zero cross-tenant latency regressions in the noisy-neighbour load test.
- New integration onboarded (key → endpoint → first delivery) in < 5 minutes.

## 6. Release plan

| Milestone | Scope |
|---|---|
| M1 — Foundation | Docs, project skeleton, CI |
| M2 — Control plane | Tenants, API keys, endpoints (FR-1..3) |
| M3 — Ingestion | Transactional publish, idempotency (FR-4, 5) |
| M4 — Delivery | Signing, retries, DLQ, attempts, replay (FR-6..9) |
| M5 — Fairness | Rate limiting, bulkheads, circuit breakers (FR-10, 11) |
| M6 — Observability | Metrics, tracing, stats API, Grafana (FR-12, NFR-6) |
| M7 — Dashboard | Next.js console (FR-13, 14) |
| M8 — Ship | Docker, Helm, Terraform, deployment guide (NFR-8) |

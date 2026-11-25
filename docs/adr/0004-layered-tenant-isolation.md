# ADR-0004: Layered tenant isolation

- Status: Accepted
- Date: 2026-11-25

## Context
The defining risk of a shared delivery service is the noisy neighbour: one
tenant with a huge backlog or a dead endpoint consumes all workers.

## Decision
Isolation is enforced at four independent layers:

| Layer | Mechanism | Protects against |
|---|---|---|
| Ingest | Redis token bucket per tenant (atomic Lua) | Burst publishing |
| Dispatch | `row_number() OVER (PARTITION BY tenant_id)` cap per tick | Large backlogs monopolising dispatch |
| Queue | SQS fair queues, `MessageGroupId = tenantId` | Queue dwell-time inflation |
| Delivery | Per-tenant bulkhead + per-endpoint circuit breaker (Resilience4j) | Slow / dead endpoints holding workers |

Bulkheads are **per replica** (max concurrency / replica count) rather than a
distributed semaphore in Redis.

## Consequences
- + No single mechanism is a single point of failure for fairness.
- + Per-replica bulkheads need no network round-trip on the hot path.
- − Effective tenant concurrency is approximate during scale events; acceptable
  because the goal is protection, not exact quota accounting.

# ADR-0002: Postgres transactional outbox + SQS for dispatch

- Status: Accepted
- Date: 2026-11-25

## Context
Ingestion must never acknowledge an event that could later be lost (NFR-1).
Writing to the database and then publishing to a broker is a dual write: a
crash between the two loses or orphans data. We also need delayed retries of
up to hours and per-tenant fairness.

Options considered:
1. **Kafka** — high throughput, but ordering per partition causes head-of-line
   blocking when one endpoint is slow; delayed retries need extra topics; costly
   to operate (MSK) for a shared service of this size.
2. **SQS only** — `DelaySeconds` caps at 15 minutes; no queryable history.
3. **Postgres only** (`SKIP LOCKED` workers) — simple, but no elastic buffer and
   no queue-depth signal for autoscaling.
4. **Postgres outbox + SQS** — Postgres is the source of truth and scheduler
   (`next_attempt_at`); SQS is the work buffer between dispatch and delivery.

## Decision
Option 4. The API writes the event and its deliveries in a single transaction.
Workers dispatch due rows to an SQS standard queue (fair queues keyed by
tenant) and consume from it.

## Consequences
- + No dual-write; crash-safe by construction.
- + Arbitrary retry schedules; full audit trail in SQL.
- + SQS `ApproximateNumberOfMessagesVisible` drives KEDA autoscaling.
- − Delivery is at-least-once; mitigated by a conditional claim and the stable
  `webhook-id` header for receiver-side dedupe.
- − Postgres write load grows with attempts; partitioning `delivery_attempt` by
  month is the documented next step.

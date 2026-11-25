# ADR-0006: ElasticMQ for local and test SQS

- Status: Accepted
- Date: 2026-11-25

## Context
Local development and integration tests need an SQS-compatible broker. The
LocalStack image now refuses to start without a commercial auth token, which
would make `docker compose up` and CI depend on a paid account.

## Decision
Use **ElasticMQ** (`softwaremill/elasticmq-native`), an open-source,
SQS-compatible server, for local development and Testcontainers. Production uses
Amazon SQS. The worker talks to both through the same AWS SDK client; only
`herald.queue.endpoint` differs.

## Consequences
- + Free, starts in ~30 ms, no credentials.
- + Accepts `MessageGroupId` on standard queues, so the fair-queue code path runs
  in tests (ElasticMQ does not reproduce SQS fair-queue *scheduling*; isolation
  is additionally enforced by Herald's own dispatch caps and bulkheads).
- − Not a full AWS emulator; other AWS services (Secrets Manager) are configured
  via environment variables locally.

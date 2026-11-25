-- Data plane: published events, their per-endpoint deliveries (the outbox) and every attempt.

CREATE TABLE event (
    id              uuid        PRIMARY KEY,
    tenant_id       uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    type            text        NOT NULL,
    payload         jsonb       NOT NULL,
    idempotency_key text,
    trace_parent    text,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX event_idempotency_idx ON event (tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX event_tenant_idx ON event (tenant_id, id DESC);
CREATE INDEX event_tenant_type_idx ON event (tenant_id, type, id DESC);

CREATE TABLE delivery (
    id                uuid        PRIMARY KEY,
    tenant_id         uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    event_id          uuid        NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    endpoint_id       uuid        NOT NULL REFERENCES endpoint (id) ON DELETE CASCADE,
    status            text        NOT NULL DEFAULT 'PENDING'
                                  CHECK (status IN ('PENDING', 'QUEUED', 'DELIVERING', 'SUCCEEDED', 'DEAD')),
    attempt_count     integer     NOT NULL DEFAULT 0, -- every attempt ever made (numbering)
    attempts_in_cycle integer     NOT NULL DEFAULT 0, -- attempts since creation or last replay (retry budget)
    next_attempt_at   timestamptz NOT NULL DEFAULT now(),
    last_status_code  integer,
    last_error        text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    completed_at      timestamptz,
    UNIQUE (event_id, endpoint_id)
);

-- The dispatcher's hot path: only PENDING rows are indexed, so the index stays small.
CREATE INDEX delivery_due_idx ON delivery (next_attempt_at) WHERE status = 'PENDING';
-- The sweeper's path: rows a crashed worker may have left behind.
CREATE INDEX delivery_inflight_idx ON delivery (updated_at) WHERE status IN ('QUEUED', 'DELIVERING');
CREATE INDEX delivery_tenant_idx ON delivery (tenant_id, id DESC);
CREATE INDEX delivery_tenant_status_idx ON delivery (tenant_id, status, id DESC);
CREATE INDEX delivery_event_idx ON delivery (event_id);
CREATE INDEX delivery_endpoint_idx ON delivery (endpoint_id, id DESC);

CREATE TABLE delivery_attempt (
    id               uuid        PRIMARY KEY,
    delivery_id      uuid        NOT NULL REFERENCES delivery (id) ON DELETE CASCADE,
    tenant_id        uuid        NOT NULL,
    attempt_number   integer     NOT NULL,
    outcome          text        NOT NULL CHECK (outcome IN ('SUCCEEDED', 'RETRYING', 'FAILED')),
    status_code      integer,
    error            text,
    response_excerpt text,
    started_at       timestamptz NOT NULL,
    duration_ms      integer     NOT NULL,
    UNIQUE (delivery_id, attempt_number)
);

CREATE INDEX delivery_attempt_tenant_started_idx ON delivery_attempt (tenant_id, started_at);

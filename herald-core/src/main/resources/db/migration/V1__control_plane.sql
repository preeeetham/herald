-- Control plane: tenants, their API keys and their webhook endpoints.

CREATE TABLE tenant (
    id                    uuid        PRIMARY KEY,
    slug                  text        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,62}$'),
    name                  text        NOT NULL,
    status                text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    rate_limit_per_second integer     NOT NULL CHECK (rate_limit_per_second > 0),
    rate_limit_burst      integer     NOT NULL CHECK (rate_limit_burst >= rate_limit_per_second),
    max_concurrency       integer     NOT NULL CHECK (max_concurrency > 0),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE api_key (
    id           uuid        PRIMARY KEY,
    tenant_id    uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    name         text        NOT NULL,
    prefix       text        NOT NULL UNIQUE,
    key_hash     text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz,
    revoked_at   timestamptz
);

CREATE INDEX api_key_tenant_idx ON api_key (tenant_id);

CREATE TABLE endpoint (
    id                         uuid        PRIMARY KEY,
    tenant_id                  uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    url                        text        NOT NULL,
    description                text,
    event_types                text[]      NOT NULL CHECK (cardinality(event_types) > 0),
    status                     text        NOT NULL DEFAULT 'ENABLED' CHECK (status IN ('ENABLED', 'DISABLED')),
    secret_ciphertext          bytea       NOT NULL,
    previous_secret_ciphertext bytea,
    previous_secret_expires_at timestamptz,
    created_at                 timestamptz NOT NULL DEFAULT now(),
    updated_at                 timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX endpoint_tenant_status_idx ON endpoint (tenant_id, status);

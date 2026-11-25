# ADR-0003: Spring JdbcClient over JPA

- Status: Accepted
- Date: 2026-11-25

## Context
The hot paths rely on PostgreSQL-specific SQL: `FOR UPDATE SKIP LOCKED`, window
functions, `jsonb`, array overlap (`&&`) for subscription matching, and
conditional state-transition updates. JPA hides SQL and makes these awkward,
while lazy loading and dirty checking add surprising behaviour.

## Decision
Use Spring's `JdbcClient` with explicit SQL in small repository classes. Schema
is owned by Flyway migrations.

## Consequences
- + Every query is visible, reviewable and index-checkable.
- + No N+1 surprises; predictable transactions.
- − More boilerplate row mapping (kept small with records).

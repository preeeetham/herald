# ADR-0001: Record architecture decisions

- Status: Accepted
- Date: 2026-11-25

## Context
Herald is built in phases. Decisions made early (storage, transport, security
model) shape everything after them, and reviewers need to see *why*, not just
*what*.

## Decision
We record every significant decision as a lightweight ADR (Michael Nygard
format) in `docs/adr`, numbered sequentially and never rewritten — superseded
ADRs are marked as such and linked to their replacement.

## Consequences
A short paper trail per decision; new contributors can reconstruct the
reasoning without archaeology in commit history.

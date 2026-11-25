# Herald

**Multi-tenant, reliable event delivery for cloud insurance platforms.**

Herald lets every tenant of a shared cloud platform publish business events
(`policy.bound`, `claim.created`, `payment.scheduled`, …) once and have them
delivered — signed, retried, observable — to every downstream partner, without
one tenant ever degrading service for another.

> 🚧 Under active development. See the [roadmap](docs/02-requirements.md#6-release-plan).

## Documentation

| Phase | Document |
|---|---|
| Discovery | [Problem research](docs/01-research.md) |
| Requirements | [PRD](docs/02-requirements.md) |
| Design | [System design](docs/03-architecture.md) · [ADRs](docs/adr) |

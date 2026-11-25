# Contributing

## Workflow
1. Open or pick an issue; link it to a requirement in `docs/02-requirements.md`.
2. Branch from `main`: `feat/<scope>`, `fix/<scope>`, `docs/<scope>`.
3. Write tests first for domain logic; add an ADR for any significant decision.
4. Commit using [Conventional Commits](https://www.conventionalcommits.org/)
   (`feat(worker): …`, `fix(api): …`, `docs: …`).
5. Open a PR using the template; CI must be green before merge.

## Local development
```bash
./mvnw verify                # build + all tests (needs Docker for Testcontainers)
docker compose -f deploy/docker-compose.yml up --build
```

## Code style
- Java 25, 4-space indent, final classes for value types, records for DTOs.
- No framework code in `herald-core`.
- SQL lives in repositories; schema changes only via new Flyway migrations.

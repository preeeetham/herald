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

## Secrets policy
- **Never commit secrets.** Real values (admin token, encryption key, DB
  passwords, AWS credentials) come from environment variables, Kubernetes
  Secrets or AWS Secrets Manager. Committed config only holds placeholders.
- Enable the pre-push hook once per clone — it runs
  [gitleaks](https://github.com/gitleaks/gitleaks) on every commit you push and
  blocks the push on a finding:
  ```bash
  brew install gitleaks
  git config core.hooksPath .githooks
  ```
- CI re-scans the full history on every push and PR.
- Test fixtures that intentionally contain public test vectors must carry an
  inline `gitleaks:allow` comment.

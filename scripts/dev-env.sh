#!/usr/bin/env bash
# Generates deploy/.env with fresh random secrets for local development.
set -euo pipefail
cd "$(dirname "$0")/.."
target=deploy/.env
if [[ -f $target && "${1:-}" != "--force" ]]; then
  echo "$target already exists (use --force to regenerate)"; exit 0
fi
umask 077
cat > "$target" <<ENV
HERALD_ADMIN_TOKEN=$(openssl rand -hex 24)
HERALD_ENCRYPTION_KEY=$(openssl rand -base64 32)
POSTGRES_PASSWORD=$(openssl rand -hex 16)
ENV
echo "Wrote $target"

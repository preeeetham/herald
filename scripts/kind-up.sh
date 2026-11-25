#!/usr/bin/env bash
# Creates a local Kubernetes cluster with kind and deploys Herald with its Helm chart.
set -euo pipefail
cd "$(dirname "$0")/.."
CLUSTER=${CLUSTER:-herald}

[[ -f deploy/.env ]] || scripts/dev-env.sh
set -a; source deploy/.env; set +a

kind get clusters | grep -qx "$CLUSTER" || kind create cluster --name "$CLUSTER" --wait 120s
kubectl config use-context "kind-$CLUSTER" >/dev/null

# Side-load dependency images from the host (much faster than pulling inside the node).
# Archives are used because `kind load docker-image` fails on multi-arch images.
platform="linux/$(docker version --format '{{.Server.Arch}}')"
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
for image in postgres:17-alpine redis:8-alpine softwaremill/elasticmq-native:1.6.14 mccutchen/go-httpbin:latest; do
  docker pull -q --platform "$platform" "$image" >/dev/null
  docker save --platform "$platform" -o "$tmp/image.tar" "$image"
  kind load image-archive "$tmp/image.tar" --name "$CLUSTER" >/dev/null
done

echo "Building images…"
docker build -q -t herald/herald-api:local --build-arg MODULE=herald-api . >/dev/null
docker build -q -t herald/herald-worker:local --build-arg MODULE=herald-worker . >/dev/null
docker build -q -t herald/herald-dashboard:local dashboard >/dev/null
for image in api worker dashboard; do kind load docker-image "herald/herald-$image:local" --name "$CLUSTER"; done

kubectl create secret generic herald-secrets \
  --from-literal=HERALD_ADMIN_TOKEN="$HERALD_ADMIN_TOKEN" \
  --from-literal=HERALD_ENCRYPTION_KEY="$HERALD_ENCRYPTION_KEY" \
  --from-literal=HERALD_DB_PASSWORD="$POSTGRES_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl apply -f deploy/k8s-local/dependencies.yaml >/dev/null
kubectl rollout status deploy/postgres --timeout=180s >/dev/null

helm upgrade --install herald deploy/helm/herald -f deploy/helm/herald/values-kind.yaml --wait --timeout 6m
kubectl get pods

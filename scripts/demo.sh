#!/usr/bin/env bash
# Seeds a demo tenant on a running local stack and streams realistic insurance events.
#   scripts/demo.sh [events]   (default 40)
# Endpoints point at the local go-httpbin receiver so every delivery path is exercised:
# healthy, flaky (retries), slow (timeouts / bulkhead) and permanently broken (DLQ).
set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/.env
API=${HERALD_API:-http://localhost:8080}
EVENTS=${1:-40}
RECEIVER=http://receiver:8080

json() { python3 -c "import sys,json; print(json.load(sys.stdin)$1)"; }

slug="acme-insurance-$(openssl rand -hex 2)"
onboard=$(curl -sf -X POST "$API/admin/v1/tenants" -H "Authorization: Bearer $HERALD_ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"slug\":\"$slug\",\"name\":\"Acme Mutual Insurance\",\"rateLimitPerSecond\":20,\"rateLimitBurst\":40,\"maxConcurrency\":8}")
KEY=$(echo "$onboard" | json "['apiKey']['key']")

endpoint() { # url description types-json
  curl -sf -X POST "$API/v1/endpoints" -H "Authorization: Bearer $KEY" -H 'Content-Type: application/json' \
    -d "{\"url\":\"$1\",\"description\":\"$2\",\"eventTypes\":$3}" >/dev/null
}
endpoint "$RECEIVER/status/200"             "Repair-shop network"   '["claim.*"]'
endpoint "$RECEIVER/status/200:0.6,503:0.4" "Payment gateway (flaky)" '["payment.*","policy.bound"]'
endpoint "$RECEIVER/delay/2"                "Fraud-scoring vendor (slow)" '["claim.created"]'
endpoint "$RECEIVER/status/410"             "Legacy reinsurer (decommissioned)" '["policy.*"]'
endpoint "$RECEIVER/anything"               "Customer comms" '["*"]'

types=(policy.bound claim.created claim.assigned claim.reserve.changed payment.scheduled policy.endorsement.issued)
for i in $(seq 1 "$EVENTS"); do
  type=${types[$((RANDOM % ${#types[@]}))]}
  n=$((100000 + RANDOM))
  curl -s -o /dev/null -X POST "$API/v1/events" -H "Authorization: Bearer $KEY" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: demo-$slug-$i" \
    -d "{\"type\":\"$type\",\"payload\":{\"policyNumber\":\"POL-$n\",\"claimNumber\":\"CLM-$n\",\"insured\":{\"name\":\"Customer $n\",\"state\":\"KA\"},\"lineOfBusiness\":\"personal_auto\",\"amount\":{\"value\":$((RANDOM % 9000 + 500)),\"currency\":\"INR\"}}}"
  sleep 0.1
done

cat <<MSG

Seeded tenant '$slug' and published $EVENTS events.

  Tenant API key : $KEY
  Swagger UI     : $API/swagger-ui.html
  Grafana        : http://localhost:3001
  Jaeger         : http://localhost:16686

MSG

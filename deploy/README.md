# Running Herald locally

```bash
scripts/dev-env.sh                                   # generates deploy/.env with random secrets (git-ignored)
docker compose -f deploy/docker-compose.yml up --build -d
scripts/demo.sh 40                                   # seeds a tenant + endpoints and publishes 40 events
```

| Service | URL | Notes |
|---|---|---|
| API + Swagger UI | http://localhost:8080/swagger-ui.html | Bearer = tenant API key or admin token |
| Dashboard | http://localhost:3000 | sign in with the API key printed by `demo.sh` |
| Grafana | http://localhost:3001 | "Herald — Delivery Overview" dashboard |
| Jaeger | http://localhost:16686 | one trace per event: publish → deliver → receiver |
| Prometheus | http://localhost:9090 | |
| Mock receiver | http://localhost:8089 | go-httpbin: `/status/503`, `/delay/3`, … |

The demo registers five endpoints that exercise every delivery path:

| Endpoint | Behaviour | Expected result |
|---|---|---|
| Repair-shop network | `200` | delivered on first attempt |
| Payment gateway | `200` 60 % / `503` 40 % | retried with back-off, then delivered |
| Fraud vendor | 2 s latency | delivered; holds bulkhead permits |
| Legacy reinsurer | `410 Gone` | dead-lettered immediately (permanent) |
| Customer comms | echo | delivered |

The SQS broker is [ElasticMQ](https://github.com/softwaremill/elasticmq) (see ADR-0006).

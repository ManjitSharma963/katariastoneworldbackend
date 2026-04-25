# Observability & Reliability Runbook

## Request Correlation

- Every incoming request gets `X-Request-Id` (generated if missing).
- Response echoes the same header.
- Logs include `requestId=<id>` via MDC.

Use this id to trace a user request across controller/service/error logs.

## Health & Metrics Endpoints

Enabled actuator endpoints:

- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

## Suggested Alerts

Start with these production alerts:

1. **Availability**
   - `health` not `UP` for 2+ minutes.
2. **Error rate**
   - HTTP 5xx ratio > 2% over 5 minutes.
3. **Latency**
   - p95 API latency > 1.5s for 10 minutes.
4. **Database pool pressure**
   - active Hikari connections > 85% of max for 5 minutes.
5. **JVM memory**
   - heap usage > 85% sustained for 10 minutes.

## Baseline Performance Checks (weekly)

Track a simple baseline on key APIs:

- `POST /api/bills` (create bill)
- `GET /api/bills/sales` (sales listing)
- `GET /api/v1/balance/summary`
- `GET /api/budget/daily/summary`

Record:

- throughput (req/s)
- p50 / p95 / p99 latency
- error %
- DB query timings (slow query log)

Store weekly numbers in a shared sheet so regressions are obvious.


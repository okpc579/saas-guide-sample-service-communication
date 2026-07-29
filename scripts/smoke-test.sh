#!/usr/bin/env bash
set -euo pipefail
docker compose up --build -d
trap 'docker compose down' EXIT
for attempt in {1..60}; do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null; then
    break
  fi
  if (( attempt == 60 )); then
    echo "application-service did not become healthy within 60 seconds" >&2
    docker compose logs application-service eligibility-service >&2
    exit 1
  fi
  sleep 1
done
curl -fsS -X POST http://localhost:8080/api/applications -H 'Content-Type: application/json' -H 'X-Tenant-Id: tenant-a' -d '{"applicantId":"APPLICANT-001"}' | grep -q ACCEPTED

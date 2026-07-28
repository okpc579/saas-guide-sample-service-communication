#!/usr/bin/env bash
set -euo pipefail
docker compose up --build -d
trap 'docker compose down' EXIT
until curl -fsS http://localhost:8080/actuator/health >/dev/null; do sleep 1; done
curl -fsS -X POST http://localhost:8080/api/applications -H 'Content-Type: application/json' -H 'X-Tenant-Id: tenant-a' -d '{"applicantId":"APPLICANT-001"}' | grep -q ACCEPTED

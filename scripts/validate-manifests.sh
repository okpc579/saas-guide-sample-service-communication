#!/usr/bin/env bash
set -euo pipefail

manifest=deploy/istio/eligibility-timeout-virtual-service.yaml
grep -q 'kind: VirtualService' "$manifest"
grep -q 'timeout: 2s' "$manifest"
grep -q 'host: eligibility-service' "$manifest"
grep -q 'number: 8080' "$manifest"
echo "Validated $manifest"

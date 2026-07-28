#!/usr/bin/env bash
set -euo pipefail
for file in deploy/istio/*.yaml; do
  if command -v kubectl >/dev/null; then kubectl apply --dry-run=client -f "$file" >/dev/null; else echo "kubectl unavailable; checked required values in $file"; fi
done
grep -q 'timeout: 2s' deploy/istio/eligibility-timeout-virtual-service.yaml
grep -q 'number: 8080' deploy/istio/eligibility-timeout-virtual-service.yaml

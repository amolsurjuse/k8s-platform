#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENVIRONMENT="${1:?Usage: $0 dev|prod}"

case "${ENVIRONMENT}" in
  dev)
    CONTEXT="k3d-electrahub-dev"
    MANIFEST="${ROOT_DIR}/cloudflare/k3d-tunnels/dev/cloudflared-config.yaml"
    ;;
  prod)
    CONTEXT="k3d-electrahub-prod"
    MANIFEST="${ROOT_DIR}/cloudflare/k3d-tunnels/prod/cloudflared-config.yaml"
    ;;
  *)
    echo "Unknown environment '${ENVIRONMENT}'. Use dev or prod." >&2
    exit 1
    ;;
esac

kubectl --context "${CONTEXT}" apply -f "${MANIFEST}"
kubectl --context "${CONTEXT}" -n cloudflare rollout restart deployment/cloudflared
kubectl --context "${CONTEXT}" -n cloudflare rollout status deployment/cloudflared --timeout=120s

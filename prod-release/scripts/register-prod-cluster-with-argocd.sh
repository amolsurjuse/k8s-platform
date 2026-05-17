#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${1:-}"

if [[ -z "${ENV_FILE}" || ! -f "${ENV_FILE}" ]]; then
  echo "Usage: $0 ./prod-release/env/prod-laptop.env" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "${ENV_FILE}"

: "${ELECTRA_KUBECONTEXT:=k3d-electrahub-prod}"
: "${ELECTRA_ARGO_SERVER:=localhost:8080}"

command -v argocd >/dev/null 2>&1 || {
  echo "Missing required command: argocd" >&2
  exit 1
}

command -v kubectl >/dev/null 2>&1 || {
  echo "Missing required command: kubectl" >&2
  exit 1
}

echo "Registering Kubernetes context '${ELECTRA_KUBECONTEXT}' with Argo CD '${ELECTRA_ARGO_SERVER}'."
echo "Make sure you are already logged in: argocd login ${ELECTRA_ARGO_SERVER}"

argocd cluster add "${ELECTRA_KUBECONTEXT}" --yes

echo "Cluster registered with Argo CD."


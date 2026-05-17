#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${1:-}"

if [[ -z "${ENV_FILE}" || ! -f "${ENV_FILE}" ]]; then
  echo "Usage: $0 ./prod-release/env/prod-laptop.env" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "${ENV_FILE}"

: "${ELECTRA_CLUSTER_RUNTIME:=k3d}"
: "${ELECTRA_CLUSTER_NAME:=electrahub-prod}"
: "${ELECTRA_API_SERVER_PORT:=6445}"
: "${ELECTRA_NAMESPACE:=prod}"
: "${ELECTRA_ARGO_NAMESPACE:=argocd}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

require_command docker
require_command kubectl
require_command helm

if [[ "${ELECTRA_CLUSTER_RUNTIME}" != "k3d" ]]; then
  echo "Only ELECTRA_CLUSTER_RUNTIME=k3d is currently scripted." >&2
  exit 1
fi

require_command k3d

if ! k3d cluster list "${ELECTRA_CLUSTER_NAME}" >/dev/null 2>&1; then
  k3d cluster create "${ELECTRA_CLUSTER_NAME}" \
    --api-port "${ELECTRA_API_SERVER_PORT}" \
    --agents 1 \
    --port "80:80@loadbalancer" \
    --port "443:443@loadbalancer" \
    --wait
fi

kubectl config use-context "k3d-${ELECTRA_CLUSTER_NAME}"
kubectl create namespace "${ELECTRA_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace "${ELECTRA_ARGO_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

helm repo add argo https://argoproj.github.io/argo-helm >/dev/null
helm repo update argo >/dev/null

helm upgrade --install argocd argo/argo-cd \
  --namespace "${ELECTRA_ARGO_NAMESPACE}" \
  --set server.service.type=ClusterIP \
  --wait

echo "Prod-like cluster is ready."
echo "Context: k3d-${ELECTRA_CLUSTER_NAME}"
echo "Namespace: ${ELECTRA_NAMESPACE}"
echo "Argo CD namespace: ${ELECTRA_ARGO_NAMESPACE}"


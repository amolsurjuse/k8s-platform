#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_FILE="${ROOT_DIR}/dynakube-dev.yaml.tmpl"

DYNATRACE_NAMESPACE="${DYNATRACE_NAMESPACE:-dynatrace}"
TARGET_NAMESPACE="${TARGET_NAMESPACE:-dev}"
OPERATOR_VERSION="${OPERATOR_VERSION:-v1.9.0}"

required_vars=("DT_API_URL" "DT_API_TOKEN" "DT_INGEST_TOKEN")
for name in "${required_vars[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "Missing required environment variable: ${name}" >&2
    exit 1
  fi
done

if [[ "${DT_API_URL}" != */api ]]; then
  echo "DT_API_URL must end with /api (current: ${DT_API_URL})" >&2
  exit 1
fi

if [[ ! -f "${TEMPLATE_FILE}" ]]; then
  echo "Missing template file: ${TEMPLATE_FILE}" >&2
  exit 1
fi

echo "[1/8] Ensure Dynatrace namespace exists"
kubectl create namespace "${DYNATRACE_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

echo "[2/8] Install/upgrade Dynatrace Operator (${OPERATOR_VERSION})"
kubectl apply -f "https://github.com/Dynatrace/dynatrace-operator/releases/download/${OPERATOR_VERSION}/kubernetes-csi.yaml"

echo "[3/8] Wait for Dynatrace operator/webhook readiness"
kubectl -n "${DYNATRACE_NAMESPACE}" rollout status deploy/dynatrace-operator --timeout=300s
kubectl -n "${DYNATRACE_NAMESPACE}" rollout status deploy/dynatrace-webhook --timeout=300s

echo "[4/8] Create/refresh Dynatrace tokens secret"
kubectl -n "${DYNATRACE_NAMESPACE}" create secret generic dynakube \
  --from-literal="apiToken=${DT_API_TOKEN}" \
  --from-literal="dataIngestToken=${DT_INGEST_TOKEN}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[5/8] Apply DynaKube configuration for ${TARGET_NAMESPACE}"
tmp_file="$(mktemp)"
trap 'rm -f "${tmp_file}"' EXIT
sed "s|__DT_API_URL__|${DT_API_URL}|g" "${TEMPLATE_FILE}" > "${tmp_file}"
kubectl apply -f "${tmp_file}"

echo "[6/8] Label target namespace for monitored workload injection"
kubectl label namespace "${TARGET_NAMESPACE}" dt-monitoring=true --overwrite

echo "[7/8] Restart app deployments in ${TARGET_NAMESPACE} for injection"
deployments=(
  api-gateway
  auth-service
  billing-service
  charger-management-service
  driver-portal-ui
  electra-hub-org-page
  ocpi-service
  ocpp-service
  ocpp-simulator
  ocpp-simulator-ui
  payment-service
  pricing-service
  session-service
  station-management-service
  subscription-service
  user-service
  web-socket-connector
)

for deploy in "${deployments[@]}"; do
  if kubectl -n "${TARGET_NAMESPACE}" get deploy "${deploy}" >/dev/null 2>&1; then
    kubectl -n "${TARGET_NAMESPACE}" rollout restart "deploy/${deploy}"
  else
    echo "Skipping missing deployment: ${deploy}"
  fi
done

echo "[8/8] Wait for rollout completion"
for deploy in "${deployments[@]}"; do
  if kubectl -n "${TARGET_NAMESPACE}" get deploy "${deploy}" >/dev/null 2>&1; then
    kubectl -n "${TARGET_NAMESPACE}" rollout status "deploy/${deploy}" --timeout=600s
  fi
done

echo
echo "Dynatrace bootstrap complete."
echo "Quick checks:"
echo "  kubectl -n ${DYNATRACE_NAMESPACE} get dynakube,pods"
echo "  kubectl -n ${TARGET_NAMESPACE} get pods"

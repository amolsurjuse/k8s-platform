#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_FILE="${ROOT_DIR}/dynakube-prod.yaml.tmpl"
DYNATRACE_NAMESPACE="${DYNATRACE_NAMESPACE:-dynatrace}"
TARGET_NAMESPACE="${TARGET_NAMESPACE:-prod}"
OPERATOR_VERSION="${OPERATOR_VERSION:-v1.9.0}"

for name in DT_API_URL DT_API_TOKEN DT_INGEST_TOKEN; do
  [[ -n "${!name:-}" ]] || { echo "Missing required environment variable: ${name}" >&2; exit 1; }
done
[[ "${DT_API_URL}" == */api ]] || { echo "DT_API_URL must end with /api" >&2; exit 1; }

kubectl create namespace "${DYNATRACE_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f "https://github.com/Dynatrace/dynatrace-operator/releases/download/${OPERATOR_VERSION}/kubernetes-csi.yaml"
kubectl -n "${DYNATRACE_NAMESPACE}" wait pod --for=condition=ready \
  --selector=app.kubernetes.io/name=dynatrace-operator,app.kubernetes.io/component=webhook --timeout=300s
kubectl -n "${DYNATRACE_NAMESPACE}" create secret generic dynakube \
  --from-literal="apiToken=${DT_API_TOKEN}" \
  --from-literal="dataIngestToken=${DT_INGEST_TOKEN}" \
  --dry-run=client -o yaml | kubectl apply -f -

tmp_file="$(mktemp)"
trap 'rm -f "${tmp_file}"' EXIT
sed "s|__DT_API_URL__|${DT_API_URL}|g" "${TEMPLATE_FILE}" > "${tmp_file}"
kubectl apply -f "${tmp_file}"
kubectl label namespace "${TARGET_NAMESPACE}" dt-monitoring=true --overwrite
kubectl -n "${DYNATRACE_NAMESPACE}" rollout status deploy/dynatrace-operator --timeout=300s
kubectl -n "${DYNATRACE_NAMESPACE}" get dynakube,pods

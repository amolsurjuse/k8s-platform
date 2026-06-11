#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/load-electrahub-demo-data.sql"
NAMESPACE="${NAMESPACE:-dev}"
POSTGRES_POD="${POSTGRES_POD:-postgresql-0}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
DEMO_USER_ID="${DEMO_USER_ID:-0322b0cc-5419-41b1-bd99-c1be65ab8004}"

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl is required but was not found on PATH" >&2
  exit 127
fi

if [[ ! -f "${SQL_FILE}" ]]; then
  echo "SQL file not found: ${SQL_FILE}" >&2
  exit 1
fi

echo "Loading ElectraHub demo data into namespace ${NAMESPACE}, pod ${POSTGRES_POD}"
kubectl exec -i -n "${NAMESPACE}" "${POSTGRES_POD}" -- \
  psql -U "${POSTGRES_USER}" -v ON_ERROR_STOP=1 -v demo_user_id="${DEMO_USER_ID}" -f - < "${SQL_FILE}"

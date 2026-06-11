#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${1:-${ROOT_DIR}/.local/cloudflare-dev-laptop.env}"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

: "${CLOUDFLARED_CONFIG_FILE:=.local/cloudflare/dev-laptop/config.yml}"
: "${INGRESS_ORIGIN:=${INGRESS_HTTP_ORIGIN:-https://127.0.0.1:8443}}"
: "${ARGOCD_ORIGIN:=${ARGOCD_HTTP_ORIGIN:-https://127.0.0.1:9443}}"
: "${ELECTRA_EXISTING_INGRESS_DOMAIN:=electrahub.com}"

CONFIG_PATH="${CLOUDFLARED_CONFIG_FILE}"
if [[ "${CONFIG_PATH}" != /* ]]; then
  CONFIG_PATH="${ROOT_DIR}/${CONFIG_PATH}"
fi

if ! command -v cloudflared >/dev/null 2>&1; then
  cat >&2 <<'EOF'
cloudflared is not installed.

macOS install:
  brew install cloudflared

Then:
  cloudflared tunnel login
  cloudflared tunnel create electrahub-dev-laptop
EOF
  exit 1
fi

if [[ ! -f "${CONFIG_PATH}" ]]; then
  echo "Missing config: ${CONFIG_PATH}" >&2
  echo "Run: ./scripts/cloudflare-dev-laptop/render-config.sh ${ENV_FILE}" >&2
  exit 1
fi

check_origin() {
  local url="$1"
  local host_header="${2:-}"
  local code
  if [[ -n "${host_header}" ]]; then
    code="$(curl -ksS -o /dev/null -w '%{http_code}' --max-time 3 -H "Host: ${host_header}" "${url}" 2>/dev/null || true)"
  else
    code="$(curl -ksS -o /dev/null -w '%{http_code}' --max-time 3 "${url}" 2>/dev/null || true)"
  fi
  if [[ ! "${code}" =~ ^[1-4][0-9][0-9]$ ]]; then
    echo "WARN: origin did not respond: ${url}" >&2
    if [[ -n "${host_header}" ]]; then
      echo "      Host header tested: ${host_header}" >&2
    fi
    echo "      Make sure ./scripts/port-forward.sh --close-in-use ingress argocd is running." >&2
  fi
}

check_origin "${INGRESS_ORIGIN}" "dev.${ELECTRA_EXISTING_INGRESS_DOMAIN}"
check_origin "${ARGOCD_ORIGIN}"

echo "Starting cloudflared with ${CONFIG_PATH}"
exec cloudflared tunnel --config "${CONFIG_PATH}" run

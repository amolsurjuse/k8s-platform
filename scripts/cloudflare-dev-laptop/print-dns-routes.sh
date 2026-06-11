#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${1:-${ROOT_DIR}/.local/cloudflare-dev-laptop.env}"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

: "${CLOUDFLARE_TUNNEL_NAME:=electrahub-dev-laptop}"
: "${ELECTRA_PUBLIC_DOMAIN:=electrahub.net}"

HOSTS=(
  "${ELECTRA_PUBLIC_DOMAIN}"
  "www.${ELECTRA_PUBLIC_DOMAIN}"
  "dev.${ELECTRA_PUBLIC_DOMAIN}"
  "api-dev.${ELECTRA_PUBLIC_DOMAIN}"
  "admin-dev.${ELECTRA_PUBLIC_DOMAIN}"
  "admin-portal-dev.${ELECTRA_PUBLIC_DOMAIN}"
  "driver-dev.${ELECTRA_PUBLIC_DOMAIN}"
  "driver-portal-dev.${ELECTRA_PUBLIC_DOMAIN}"
  "simulator-dev.${ELECTRA_PUBLIC_DOMAIN}"
  "ocpp-simulator-dev.${ELECTRA_PUBLIC_DOMAIN}"
  "argocd-dev.${ELECTRA_PUBLIC_DOMAIN}"
)

cat <<EOF
# Run these once after creating the Cloudflare tunnel:
EOF

for host in "${HOSTS[@]}"; do
  printf 'cloudflared tunnel route dns %q %q\n' "${CLOUDFLARE_TUNNEL_NAME}" "${host}"
done

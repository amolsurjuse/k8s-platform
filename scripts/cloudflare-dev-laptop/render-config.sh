#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${1:-${ROOT_DIR}/.local/cloudflare-dev-laptop.env}"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

: "${CLOUDFLARE_TUNNEL_ID:?Set CLOUDFLARE_TUNNEL_ID in ${ENV_FILE}}"
: "${CLOUDFLARE_CREDENTIALS_FILE:=${HOME}/.cloudflared/${CLOUDFLARE_TUNNEL_ID}.json}"
: "${ELECTRA_PUBLIC_DOMAIN:=electrahub.net}"
: "${ELECTRA_EXISTING_INGRESS_DOMAIN:=electrahub.com}"
: "${INGRESS_ORIGIN:=${INGRESS_HTTP_ORIGIN:-https://127.0.0.1:8443}}"
: "${ARGOCD_ORIGIN:=${ARGOCD_HTTP_ORIGIN:-https://127.0.0.1:9443}}"
: "${CLOUDFLARED_CONFIG_FILE:=.local/cloudflare/dev-laptop/config.yml}"

CONFIG_PATH="${CLOUDFLARED_CONFIG_FILE}"
if [[ "${CONFIG_PATH}" != /* ]]; then
  CONFIG_PATH="${ROOT_DIR}/${CONFIG_PATH}"
fi

mkdir -p "$(dirname "${CONFIG_PATH}")"

cat > "${CONFIG_PATH}" <<EOF
tunnel: ${CLOUDFLARE_TUNNEL_ID}
credentials-file: ${CLOUDFLARE_CREDENTIALS_FILE}

ingress:
  - hostname: ${ELECTRA_PUBLIC_DOMAIN}
    service: ${INGRESS_ORIGIN}
    originRequest:
      httpHostHeader: ${ELECTRA_PUBLIC_DOMAIN}
      noTLSVerify: true

  - hostname: www.${ELECTRA_PUBLIC_DOMAIN}
    service: ${INGRESS_ORIGIN}
    originRequest:
      httpHostHeader: www.${ELECTRA_PUBLIC_DOMAIN}
      noTLSVerify: true

  - hostname: dev.${ELECTRA_PUBLIC_DOMAIN}
    service: ${INGRESS_ORIGIN}
    originRequest:
      httpHostHeader: dev.${ELECTRA_EXISTING_INGRESS_DOMAIN}
      noTLSVerify: true

  - hostname: api-dev.${ELECTRA_PUBLIC_DOMAIN}
    service: ${INGRESS_ORIGIN}
    originRequest:
      httpHostHeader: dev.${ELECTRA_EXISTING_INGRESS_DOMAIN}
      noTLSVerify: true

  - hostname: admin-dev.${ELECTRA_PUBLIC_DOMAIN}
    service: ${INGRESS_ORIGIN}
    originRequest:
      httpHostHeader: admin-portal-dev.${ELECTRA_EXISTING_INGRESS_DOMAIN}
      noTLSVerify: true

  - hostname: admin-portal-dev.${ELECTRA_PUBLIC_DOMAIN}
    service: ${INGRESS_ORIGIN}
    originRequest:
      httpHostHeader: admin-portal-dev.${ELECTRA_EXISTING_INGRESS_DOMAIN}
      noTLSVerify: true

  - hostname: driver-dev.${ELECTRA_PUBLIC_DOMAIN}
    service: ${INGRESS_ORIGIN}
    originRequest:
      httpHostHeader: driver-portal-dev.${ELECTRA_EXISTING_INGRESS_DOMAIN}
      noTLSVerify: true

  - hostname: driver-portal-dev.${ELECTRA_PUBLIC_DOMAIN}
    service: ${INGRESS_ORIGIN}
    originRequest:
      httpHostHeader: driver-portal-dev.${ELECTRA_EXISTING_INGRESS_DOMAIN}
      noTLSVerify: true

  - hostname: simulator-dev.${ELECTRA_PUBLIC_DOMAIN}
    service: ${INGRESS_ORIGIN}
    originRequest:
      httpHostHeader: ocpp-simulator-dev.${ELECTRA_EXISTING_INGRESS_DOMAIN}
      noTLSVerify: true

  - hostname: ocpp-simulator-dev.${ELECTRA_PUBLIC_DOMAIN}
    service: ${INGRESS_ORIGIN}
    originRequest:
      httpHostHeader: ocpp-simulator-dev.${ELECTRA_EXISTING_INGRESS_DOMAIN}
      noTLSVerify: true

  - hostname: argocd-dev.${ELECTRA_PUBLIC_DOMAIN}
    service: ${ARGOCD_ORIGIN}
    originRequest:
      noTLSVerify: true

  - service: http_status:404
EOF

echo "Rendered ${CONFIG_PATH}"
echo
echo "Next:"
echo "  ./scripts/cloudflare-dev-laptop/print-dns-routes.sh ${ENV_FILE}"
echo "  ./scripts/cloudflare-dev-laptop/start-tunnel.sh ${ENV_FILE}"

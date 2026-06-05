#!/usr/bin/env bash
set -euo pipefail

# Local port defaults (can be overridden via env vars):
# - postgres                -> 127.0.0.1:${POSTGRES_LOCAL_PORT:-58318}                   -> dev/postgresql:5432
# - redis                   -> 127.0.0.1:${REDIS_LOCAL_PORT:-6379}                       -> dev/redis:6379
# - elasticsearch           -> 127.0.0.1:${ELASTICSEARCH_LOCAL_PORT:-9200}               -> dev/elasticsearch:9200
# - station-management      -> 127.0.0.1:${STATION_MGMT_LOCAL_PORT:-8081}               -> dev/station-management-service:8081
# - ocpp-service            -> 127.0.0.1:${OCPP_SERVICE_LOCAL_PORT:-8082}               -> dev/ocpp-service:8082
# - session-service         -> 127.0.0.1:${SESSION_SERVICE_LOCAL_PORT:-8083}            -> dev/session-service:8083
# - ocpi-service            -> 127.0.0.1:${OCPI_SERVICE_LOCAL_PORT:-8084}               -> dev/ocpi-service:8084
# - billing-service         -> 127.0.0.1:${BILLING_SERVICE_LOCAL_PORT:-8085}            -> dev/billing-service:8085
# - pricing-service         -> 127.0.0.1:${PRICING_SERVICE_LOCAL_PORT:-8092}            -> dev/pricing-service:8092
# - ingress                 -> 127.0.0.1:${INGRESS_HTTP_LOCAL_PORT:-8080},${INGRESS_HTTPS_LOCAL_PORT:-8443} -> ingress-nginx/ingress-nginx-controller:80,443
# - argocd                  -> 127.0.0.1:${ARGOCD_HTTP_LOCAL_PORT:-8090},${ARGOCD_HTTPS_LOCAL_PORT:-9443}  -> argocd/argocd-server:80,443
CLOSE_IN_USE=false
RESTART_DELAY_SECONDS="${RESTART_DELAY_SECONDS:-3}"
MONITOR_INTERVAL_SECONDS="${MONITOR_INTERVAL_SECONDS:-1}"

KUBECTL=(kubectl)
if [[ -n "${KUBE_CONTEXT:-}" ]]; then
  KUBECTL+=(--context "${KUBE_CONTEXT}")
fi
if [[ -n "${KUBECONFIG_PATH:-}" ]]; then
  KUBECTL+=(--kubeconfig "${KUBECONFIG_PATH}")
fi

PIDS=()
PID_NAMES=()
PID_NAMESPACES=()
PID_SERVICES=()
PID_PORTS_CSV=()
PID_NEXT_RETRY_AT=()
PID_RESTARTS=()
STARTED_POSTGRES=false
STARTED_REDIS=false
STARTED_ELASTICSEARCH=false
STARTED_STATION_MGMT=false
STARTED_OCPP_SERVICE=false
STARTED_SESSION_SERVICE=false
STARTED_OCPI_SERVICE=false
STARTED_BILLING_SERVICE=false
STARTED_PRICING_SERVICE=false
STARTED_INGRESS=false
STARTED_ARGOCD=false
STARTED_INGRESS_HTTP=false
STARTED_INGRESS_HTTPS=false
STARTED_ARGOCD_HTTP=false
STARTED_ARGOCD_HTTPS=false

POSTGRES_LOCAL_PORT="${POSTGRES_LOCAL_PORT:-58318}"
REDIS_LOCAL_PORT="${REDIS_LOCAL_PORT:-6379}"
ELASTICSEARCH_LOCAL_PORT="${ELASTICSEARCH_LOCAL_PORT:-9200}"
STATION_MGMT_LOCAL_PORT="${STATION_MGMT_LOCAL_PORT:-8081}"
OCPP_SERVICE_LOCAL_PORT="${OCPP_SERVICE_LOCAL_PORT:-8082}"
SESSION_SERVICE_LOCAL_PORT="${SESSION_SERVICE_LOCAL_PORT:-8083}"
OCPI_SERVICE_LOCAL_PORT="${OCPI_SERVICE_LOCAL_PORT:-8084}"
BILLING_SERVICE_LOCAL_PORT="${BILLING_SERVICE_LOCAL_PORT:-8085}"
PRICING_SERVICE_LOCAL_PORT="${PRICING_SERVICE_LOCAL_PORT:-8092}"
INGRESS_HTTP_LOCAL_PORT="${INGRESS_HTTP_LOCAL_PORT:-8080}"
INGRESS_HTTPS_LOCAL_PORT="${INGRESS_HTTPS_LOCAL_PORT:-8443}"
ARGOCD_HTTP_LOCAL_PORT="${ARGOCD_HTTP_LOCAL_PORT:-8090}"
ARGOCD_HTTPS_LOCAL_PORT="${ARGOCD_HTTPS_LOCAL_PORT:-9443}"

usage() {
  cat <<'EOF'
Usage:
  ./port-forward.sh [--close-in-use] [all|dev|postgres|redis|elasticsearch|station-management|ocpp-service|session-service|ocpi-service|billing-service|pricing-service|argocd|ingress]...

Examples:
  ./port-forward.sh all
  ./port-forward.sh --close-in-use all
  ./port-forward.sh dev
  ./port-forward.sh postgres redis session-service
  ./port-forward.sh postgres argocd ingress

Targets:
  all                 Start all port-forwards
  dev                 Start all dev-namespace services (postgres, redis, elasticsearch, and all backend services)
  postgres            dev/postgresql:5432
  redis               dev/redis:6379
  elasticsearch       dev/elasticsearch:9200
  station-management  dev/station-management-service:8081
  ocpp-service        dev/ocpp-service:8082
  session-service     dev/session-service:8083
  ocpi-service        dev/ocpi-service:8084
  billing-service     dev/billing-service:8085
  pricing-service     dev/pricing-service:8092
  ingress             ingress-nginx/ingress-nginx-controller:80,443
  argocd              argocd/argocd-server:80,443

Optional env vars:
  KUBE_CONTEXT=<context-name>
  KUBECONFIG_PATH=/path/to/kubeconfig
  POSTGRES_LOCAL_PORT=58318
  REDIS_LOCAL_PORT=6379
  ELASTICSEARCH_LOCAL_PORT=9200
  STATION_MGMT_LOCAL_PORT=8081
  OCPP_SERVICE_LOCAL_PORT=8082
  SESSION_SERVICE_LOCAL_PORT=8083
  OCPI_SERVICE_LOCAL_PORT=8084
  BILLING_SERVICE_LOCAL_PORT=8085
  PRICING_SERVICE_LOCAL_PORT=8092
  INGRESS_HTTP_LOCAL_PORT=8080
  INGRESS_HTTPS_LOCAL_PORT=8443
  ARGOCD_HTTP_LOCAL_PORT=8090
  ARGOCD_HTTPS_LOCAL_PORT=9443
  RESTART_DELAY_SECONDS=3
  MONITOR_INTERVAL_SECONDS=1

Flags:
  --close-in-use   Close local LISTEN processes if a configured port is already in use.
                   Safety: only auto-closes processes with command name "kubectl".
                   If a different process owns the port, that service is skipped.
                   Note: skipped/failed forwards are retried automatically.
EOF
}

ensure_port_available() {
  local port="$1"
  local pids_raw
  pids_raw="$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true)"

  if [[ -z "${pids_raw}" ]]; then
    return 0
  fi

  local pids
  pids="$(echo "${pids_raw}" | tr '\n' ' ' | xargs 2>/dev/null || true)"

  if [[ "${CLOSE_IN_USE}" != true ]]; then
    echo "Port ${port} is already in use (PID(s): ${pids}). Skipping this service. Use --close-in-use to close it."
    return 1
  fi

  local non_kubectl=()
  local pid
  for pid in ${pids}; do
    local cmd
    cmd="$(ps -p "${pid}" -o comm= 2>/dev/null | xargs || true)"
    if [[ "${cmd}" != "kubectl" ]]; then
      non_kubectl+=("${pid}:${cmd:-unknown}")
    fi
  done

  if [[ ${#non_kubectl[@]} -gt 0 ]]; then
    echo "Port ${port} is in use by non-kubectl process(es): ${non_kubectl[*]}"
    echo "Skipping this service for safety. Use different local port env vars (see --help)."
    return 1
  fi

  echo "Port ${port} is in use (PID(s): ${pids}). Closing..."
  kill ${pids} 2>/dev/null || true
  sleep 1

  local remaining
  remaining="$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null | tr '\n' ' ' | xargs 2>/dev/null || true)"
  if [[ -n "${remaining}" ]]; then
    echo "Force killing PID(s) on port ${port}: ${remaining}"
    kill -9 ${remaining} 2>/dev/null || true
    sleep 1
  fi

  remaining="$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null | tr '\n' ' ' | xargs 2>/dev/null || true)"
  if [[ -n "${remaining}" ]]; then
    echo "Unable to free port ${port}. Skipping this service."
    return 1
  fi

  return 0
}

start_forward_process() {
  local idx="$1"
  local name="${PID_NAMES[$idx]}"
  local namespace="${PID_NAMESPACES[$idx]}"
  local service="${PID_SERVICES[$idx]}"
  local ports_csv="${PID_PORTS_CSV[$idx]}"
  local requested_ports=()
  local selected_ports=()
  local mapping

  IFS=',' read -r -a requested_ports <<< "${ports_csv}"
  for mapping in "${requested_ports[@]}"; do
    local local_port="${mapping%%:*}"
    if ! ensure_port_available "${local_port}"; then
      echo "Skipping ${name} mapping ${mapping} for now."
      continue
    fi
    selected_ports+=("${mapping}")
  done

  if [[ ${#selected_ports[@]} -eq 0 ]]; then
    local now
    now="$(date +%s)"
    PID_NEXT_RETRY_AT[$idx]="$((now + RESTART_DELAY_SECONDS))"
    echo "[${name}] No usable local ports right now; retrying in ${RESTART_DELAY_SECONDS}s."
    return 1
  fi

  echo "Starting ${name}: ${namespace}/svc/${service} (${selected_ports[*]})"
  (
    "${KUBECTL[@]}" -n "${namespace}" port-forward "svc/${service}" "${selected_ports[@]}" --address 127.0.0.1 2>&1 \
      | sed -e "s/^/[${name}] /"
  ) &
  PIDS[$idx]="$!"
  PID_NEXT_RETRY_AT[$idx]=0
  return 0
}

mark_target_started() {
  local name="$1"
  shift
  local ports=("$@")

  case "${name}" in
    postgres) STARTED_POSTGRES=true ;;
    redis) STARTED_REDIS=true ;;
    elasticsearch) STARTED_ELASTICSEARCH=true ;;
    station-management) STARTED_STATION_MGMT=true ;;
    ocpp-service) STARTED_OCPP_SERVICE=true ;;
    session-service) STARTED_SESSION_SERVICE=true ;;
    ocpi-service) STARTED_OCPI_SERVICE=true ;;
    billing-service) STARTED_BILLING_SERVICE=true ;;
    pricing-service) STARTED_PRICING_SERVICE=true ;;
    ingress)
      STARTED_INGRESS=true
      local m
      for m in "${ports[@]}"; do
        case "${m}" in
          "${INGRESS_HTTP_LOCAL_PORT}:80") STARTED_INGRESS_HTTP=true ;;
          "${INGRESS_HTTPS_LOCAL_PORT}:443") STARTED_INGRESS_HTTPS=true ;;
        esac
      done
      ;;
    argocd)
      STARTED_ARGOCD=true
      local m
      for m in "${ports[@]}"; do
        case "${m}" in
          "${ARGOCD_HTTP_LOCAL_PORT}:80") STARTED_ARGOCD_HTTP=true ;;
          "${ARGOCD_HTTPS_LOCAL_PORT}:443") STARTED_ARGOCD_HTTPS=true ;;
        esac
      done
      ;;
  esac
}

start_forward() {
  local name="$1"
  local namespace="$2"
  local service="$3"
  shift 3
  local ports=("$@")
  local ports_csv=""
  local idx

  ports_csv="$(IFS=','; echo "${ports[*]}")"
  idx="${#PID_NAMES[@]}"

  PID_NAMES+=("${name}")
  PID_NAMESPACES+=("${namespace}")
  PID_SERVICES+=("${service}")
  PID_PORTS_CSV+=("${ports_csv}")
  PID_NEXT_RETRY_AT+=(0)
  PID_RESTARTS+=(0)
  PIDS+=("")

  mark_target_started "${name}" "${ports[@]}"
  start_forward_process "${idx}" || true
}

cleanup() {
  echo
  echo "Stopping port-forwards..."
  for pid in "${PIDS[@]:-}"; do
    if [[ -z "${pid}" ]]; then
      continue
    fi
    if kill -0 "${pid}" 2>/dev/null; then
      kill "${pid}" 2>/dev/null || true
    fi
  done
}

supervise_forwards() {
  while true; do
    local now
    now="$(date +%s)"
    local i

    for i in "${!PID_NAMES[@]}"; do
      local pid="${PIDS[$i]:-}"
      local name="${PID_NAMES[$i]}"
      local next_retry="${PID_NEXT_RETRY_AT[$i]:-0}"

      if [[ -n "${pid}" ]]; then
        if kill -0 "${pid}" 2>/dev/null; then
          continue
        fi

        PIDS[$i]=""
        PID_RESTARTS[$i]="$(( ${PID_RESTARTS[$i]:-0} + 1 ))"
        PID_NEXT_RETRY_AT[$i]="$((now + RESTART_DELAY_SECONDS))"
        echo "[${name}] Port-forward exited. Restart #${PID_RESTARTS[$i]} in ${RESTART_DELAY_SECONDS}s."
        continue
      fi

      if (( now < next_retry )); then
        continue
      fi

      start_forward_process "${i}" || true
    done

    sleep "${MONITOR_INTERVAL_SECONDS}"
  done
}

TARGETS=()
if [[ $# -eq 0 ]]; then
  TARGETS=(all)
else
  for arg in "$@"; do
    case "${arg}" in
      --close-in-use)
        CLOSE_IN_USE=true
        ;;
      -h|--help|help)
        usage
        exit 0
        ;;
      *)
        TARGETS+=("${arg}")
        ;;
    esac
  done
  if [[ ${#TARGETS[@]} -eq 0 ]]; then
    TARGETS=(all)
  fi
fi

trap cleanup EXIT INT TERM

start_dev_services() {
  start_forward "postgres"           "dev" "postgresql"               "${POSTGRES_LOCAL_PORT}:5432"
  start_forward "redis"              "dev" "redis"                    "${REDIS_LOCAL_PORT}:6379"
  start_forward "elasticsearch"      "dev" "elasticsearch"            "${ELASTICSEARCH_LOCAL_PORT}:9200"
  start_forward "station-management" "dev" "station-management-service" "${STATION_MGMT_LOCAL_PORT}:8081"
  start_forward "ocpp-service"       "dev" "ocpp-service"             "${OCPP_SERVICE_LOCAL_PORT}:8082"
  start_forward "session-service"    "dev" "session-service"          "${SESSION_SERVICE_LOCAL_PORT}:8083"
  start_forward "ocpi-service"       "dev" "ocpi-service"             "${OCPI_SERVICE_LOCAL_PORT}:8084"
  start_forward "billing-service"    "dev" "billing-service"          "${BILLING_SERVICE_LOCAL_PORT}:8085"
  start_forward "pricing-service"    "dev" "pricing-service"          "${PRICING_SERVICE_LOCAL_PORT}:8092"
}

for target in "${TARGETS[@]}"; do
  case "${target}" in
    all)
      start_dev_services
      start_forward "ingress" "ingress-nginx" "ingress-nginx-controller" "${INGRESS_HTTP_LOCAL_PORT}:80" "${INGRESS_HTTPS_LOCAL_PORT}:443"
      start_forward "argocd" "argocd" "argocd-server" "${ARGOCD_HTTP_LOCAL_PORT}:80" "${ARGOCD_HTTPS_LOCAL_PORT}:443"
      ;;
    dev)
      start_dev_services
      ;;
    postgres)
      start_forward "postgres" "dev" "postgresql" "${POSTGRES_LOCAL_PORT}:5432"
      ;;
    redis)
      start_forward "redis" "dev" "redis" "${REDIS_LOCAL_PORT}:6379"
      ;;
    elasticsearch)
      start_forward "elasticsearch" "dev" "elasticsearch" "${ELASTICSEARCH_LOCAL_PORT}:9200"
      ;;
    station-management)
      start_forward "station-management" "dev" "station-management-service" "${STATION_MGMT_LOCAL_PORT}:8081"
      ;;
    ocpp-service)
      start_forward "ocpp-service" "dev" "ocpp-service" "${OCPP_SERVICE_LOCAL_PORT}:8082"
      ;;
    session-service)
      start_forward "session-service" "dev" "session-service" "${SESSION_SERVICE_LOCAL_PORT}:8083"
      ;;
    ocpi-service)
      start_forward "ocpi-service" "dev" "ocpi-service" "${OCPI_SERVICE_LOCAL_PORT}:8084"
      ;;
    billing-service)
      start_forward "billing-service" "dev" "billing-service" "${BILLING_SERVICE_LOCAL_PORT}:8085"
      ;;
    pricing-service)
      start_forward "pricing-service" "dev" "pricing-service" "${PRICING_SERVICE_LOCAL_PORT}:8092"
      ;;
    argocd)
      start_forward "argocd" "argocd" "argocd-server" "${ARGOCD_HTTP_LOCAL_PORT}:80" "${ARGOCD_HTTPS_LOCAL_PORT}:443"
      ;;
    ingress)
      start_forward "ingress" "ingress-nginx" "ingress-nginx-controller" "${INGRESS_HTTP_LOCAL_PORT}:80" "${INGRESS_HTTPS_LOCAL_PORT}:443"
      ;;
    *)
      echo "Unknown target: ${target}" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ ${#PID_NAMES[@]} -eq 0 ]]; then
  echo
  echo "No port-forward targets were selected."
  exit 0
fi

echo
echo "Endpoints:"
if [[ "${STARTED_POSTGRES}" == true ]]; then
  echo "  Postgres         : 127.0.0.1:${POSTGRES_LOCAL_PORT}"
fi
if [[ "${STARTED_REDIS}" == true ]]; then
  echo "  Redis                : 127.0.0.1:${REDIS_LOCAL_PORT}"
fi
if [[ "${STARTED_ELASTICSEARCH}" == true ]]; then
  echo "  Elasticsearch        : http://127.0.0.1:${ELASTICSEARCH_LOCAL_PORT}"
fi
if [[ "${STARTED_STATION_MGMT}" == true ]]; then
  echo "  Station Management   : http://127.0.0.1:${STATION_MGMT_LOCAL_PORT}"
fi
if [[ "${STARTED_OCPP_SERVICE}" == true ]]; then
  echo "  OCPP Service         : http://127.0.0.1:${OCPP_SERVICE_LOCAL_PORT}"
fi
if [[ "${STARTED_SESSION_SERVICE}" == true ]]; then
  echo "  Session Service      : http://127.0.0.1:${SESSION_SERVICE_LOCAL_PORT}"
fi
if [[ "${STARTED_OCPI_SERVICE}" == true ]]; then
  echo "  OCPI Service         : http://127.0.0.1:${OCPI_SERVICE_LOCAL_PORT}"
fi
if [[ "${STARTED_BILLING_SERVICE}" == true ]]; then
  echo "  Billing Service      : http://127.0.0.1:${BILLING_SERVICE_LOCAL_PORT}"
fi
if [[ "${STARTED_PRICING_SERVICE}" == true ]]; then
  echo "  Pricing Service      : http://127.0.0.1:${PRICING_SERVICE_LOCAL_PORT}"
fi
if [[ "${STARTED_INGRESS}" == true ]]; then
  if [[ "${STARTED_INGRESS_HTTP}" == true ]]; then
    echo "  Ingress HTTP : http://127.0.0.1:${INGRESS_HTTP_LOCAL_PORT}"
  fi
  if [[ "${STARTED_INGRESS_HTTPS}" == true ]]; then
    echo "  Ingress HTTPS: https://127.0.0.1:${INGRESS_HTTPS_LOCAL_PORT}"
  fi
fi
if [[ "${STARTED_ARGOCD}" == true ]]; then
  if [[ "${STARTED_ARGOCD_HTTP}" == true ]]; then
    echo "  Argo CD HTTP : http://127.0.0.1:${ARGOCD_HTTP_LOCAL_PORT}"
  fi
  if [[ "${STARTED_ARGOCD_HTTPS}" == true ]]; then
    echo "  Argo CD HTTPS: https://127.0.0.1:${ARGOCD_HTTPS_LOCAL_PORT}"
  fi
  if [[ "${STARTED_INGRESS_HTTPS}" == true && "${STARTED_ARGOCD_HTTPS}" == true ]]; then
    echo "  Note: ${INGRESS_HTTPS_LOCAL_PORT} is ingress-nginx. Use ${ARGOCD_HTTPS_LOCAL_PORT} for Argo CD UI."
  fi
fi
echo
echo "Port-forward supervisor is running. Press Ctrl+C to stop."
supervise_forwards

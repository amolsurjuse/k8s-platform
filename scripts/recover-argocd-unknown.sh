#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${ARGOCD_NAMESPACE:-argocd}"
APP_SELECTOR="${ARGOCD_APP_SELECTOR:-}"
WAIT_TIMEOUT="${ARGOCD_WAIT_TIMEOUT:-180s}"

info() {
  printf '[argocd-recover] %s\n' "$*"
}

warn() {
  printf '[argocd-recover] WARN: %s\n' "$*" >&2
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'ERROR: required command not found: %s\n' "$1" >&2
    exit 1
  fi
}

kubectl_argocd() {
  kubectl -n "$NAMESPACE" "$@"
}

wait_for_cluster() {
  info "checking Kubernetes API"
  kubectl version --request-timeout=10s >/dev/null
  kubectl get nodes
}

recover_repo_server() {
  info "checking Argo CD repo-server"
  local pods
  pods="$(kubectl_argocd get pods -l app.kubernetes.io/name=argocd-repo-server -o name)"
  if [ -z "$pods" ]; then
    warn "repo-server pod not found"
    return
  fi

  local needs_recreate="false"
  while IFS= read -r pod; do
    [ -z "$pod" ] && continue
    local phase ready init_waiting
    phase="$(kubectl_argocd get "$pod" -o jsonpath='{.status.phase}' 2>/dev/null || true)"
    ready="$(kubectl_argocd get "$pod" -o jsonpath='{.status.containerStatuses[?(@.name=="argocd-repo-server")].ready}' 2>/dev/null || true)"
    init_waiting="$(kubectl_argocd get "$pod" -o jsonpath='{.status.initContainerStatuses[?(@.name=="copyutil")].state.waiting.reason}' 2>/dev/null || true)"
    info "$pod phase=$phase ready=${ready:-unknown} copyutil=${init_waiting:-ok}"
    if [ "$phase" != "Running" ] || [ "$ready" != "true" ] || [ "$init_waiting" = "CrashLoopBackOff" ]; then
      needs_recreate="true"
    fi
  done <<< "$pods"

  if [ "$needs_recreate" = "true" ]; then
    info "recreating repo-server pod to clear stale /var/run/argocd EmptyDir state"
    kubectl_argocd delete pod -l app.kubernetes.io/name=argocd-repo-server --wait=false
    kubectl_argocd rollout status deployment/argocd-repo-server --timeout="$WAIT_TIMEOUT"
  else
    info "repo-server is healthy"
  fi
}

restart_controllers_if_unknown() {
  local unknown_count
  unknown_count="$(kubectl_argocd get applications -o jsonpath='{range .items[*]}{.status.sync.status}{"\n"}{end}' | awk '$1=="Unknown"{count++} END{print count+0}')"
  if [ "$unknown_count" -eq 0 ]; then
    info "no Unknown Argo CD applications detected"
    return
  fi

  warn "$unknown_count Argo CD applications are still Unknown; restarting reconciliation components"
  kubectl_argocd rollout restart statefulset/argocd-application-controller
  kubectl_argocd rollout restart deployment/argocd-applicationset-controller
  kubectl_argocd rollout restart deployment/argocd-server
  kubectl_argocd rollout status statefulset/argocd-application-controller --timeout="$WAIT_TIMEOUT"
  kubectl_argocd rollout status deployment/argocd-applicationset-controller --timeout="$WAIT_TIMEOUT"
  kubectl_argocd rollout status deployment/argocd-server --timeout="$WAIT_TIMEOUT"
}

refresh_apps() {
  info "requesting hard refresh for Argo CD applications"
  if [ -n "$APP_SELECTOR" ]; then
    kubectl_argocd annotate applications -l "$APP_SELECTOR" argocd.argoproj.io/refresh=hard --overwrite
  else
    kubectl_argocd annotate applications --all argocd.argoproj.io/refresh=hard --overwrite
  fi
}

print_summary() {
  info "current Argo CD application states"
  kubectl_argocd get applications -o wide
}

require_cmd kubectl
wait_for_cluster
recover_repo_server
restart_controllers_if_unknown
refresh_apps
sleep 5
print_summary

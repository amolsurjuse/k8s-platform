#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${1:-}"

if [[ -z "${ENV_FILE}" || ! -f "${ENV_FILE}" ]]; then
  echo "Usage: $0 local-k3d/env/local-k3d.env" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "${ENV_FILE}"

: "${ELECTRA_DEV_KUBECONTEXT:=k3d-electrahub-dev}"
: "${ELECTRA_DEV_NAMESPACE:=dev}"
: "${ELECTRA_DEV_ARGO_PROJECT:=electrahub-dev}"
: "${ELECTRA_PROD_KUBECONTEXT:=k3d-electrahub-prod}"
: "${ELECTRA_PROD_NAMESPACE:=prod}"
: "${ELECTRA_PROD_ARGO_PROJECT:=electrahub-prod}"
: "${ELECTRA_ARGO_NAMESPACE:=argocd}"
: "${ELECTRA_GIT_REPO_URL:=https://github.com/amolsurjuse/k8s-platform.git}"

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

require_command kubectl
require_command helm

install_argocd_for_cluster() {
  local context="$1"
  local namespace="$2"
  local project="$3"

  kubectl config use-context "${context}"
  kubectl create namespace "${namespace}" --dry-run=client -o yaml | kubectl apply -f -
  kubectl create namespace "${ELECTRA_ARGO_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

  helm repo add argo https://argoproj.github.io/argo-helm --force-update >/dev/null
  helm repo update argo >/dev/null

  helm upgrade --install argocd argo/argo-cd \
    --namespace "${ELECTRA_ARGO_NAMESPACE}" \
    --set server.service.type=ClusterIP \
    --set configs.params."server\.insecure"=true \
    --wait

  cat <<YAML | kubectl apply -f -
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata:
  name: ${project}
  namespace: ${ELECTRA_ARGO_NAMESPACE}
spec:
  sourceRepos:
    - ${ELECTRA_GIT_REPO_URL}
  destinations:
    - namespace: ${namespace}
      server: https://kubernetes.default.svc
  clusterResourceWhitelist:
    - group: '*'
      kind: '*'
YAML
}

install_argocd_for_cluster "${ELECTRA_DEV_KUBECONTEXT}" "${ELECTRA_DEV_NAMESPACE}" "${ELECTRA_DEV_ARGO_PROJECT}"
install_argocd_for_cluster "${ELECTRA_PROD_KUBECONTEXT}" "${ELECTRA_PROD_NAMESPACE}" "${ELECTRA_PROD_ARGO_PROJECT}"

echo "Local k3d environment is ready for GitOps."
echo "Dev:  context=${ELECTRA_DEV_KUBECONTEXT}, namespace=${ELECTRA_DEV_NAMESPACE}, project=${ELECTRA_DEV_ARGO_PROJECT}"
echo "Prod: context=${ELECTRA_PROD_KUBECONTEXT}, namespace=${ELECTRA_PROD_NAMESPACE}, project=${ELECTRA_PROD_ARGO_PROJECT}"

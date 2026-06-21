#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${1:-}"

if [[ -z "${ENV_FILE}" || ! -f "${ENV_FILE}" ]]; then
  echo "Usage: $0 ./prod-release/env/prod-laptop.env" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "${ENV_FILE}"

: "${ELECTRA_NAMESPACE:=prod}"
: "${ELECTRA_ARGO_NAMESPACE:=argocd}"
: "${ELECTRA_ARGO_PROJECT:=electrahub-prod}"
: "${ELECTRA_GIT_REPO_URL:=https://github.com/amolsurjuse/k8s-platform.git}"
: "${ELECTRA_GIT_TARGET_REVISION:=develop}"
: "${ELECTRA_GIT_PATH:=charts/common}"

command -v kubectl >/dev/null 2>&1 || {
  echo "Missing required command: kubectl" >&2
  exit 1
}

SERVICES=(
  api-gateway
  auth-service
  billing-service
  charger-management-service
  payment-service
  pricing-service
  session-service
  station-management-service
  subscription-service
  user-service
  web-socket-connector
  ocpi-service
  ocpp-service
  ocpp-simulator
  ocpp-simulator-ui
  admin-portal-ui
  driver-portal-ui
  electra-hub-org-page
)

kubectl create namespace "${ELECTRA_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace "${ELECTRA_ARGO_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

cat <<YAML | kubectl apply -f -
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata:
  name: ${ELECTRA_ARGO_PROJECT}
  namespace: ${ELECTRA_ARGO_NAMESPACE}
spec:
  sourceRepos:
    - ${ELECTRA_GIT_REPO_URL}
  destinations:
    - namespace: ${ELECTRA_NAMESPACE}
      server: https://kubernetes.default.svc
  clusterResourceWhitelist:
    - group: '*'
      kind: '*'
YAML

create_infra_application() {
  local name="$1"
  local chart_path="$2"
  local value_file="$3"
  local release_name="$4"

  cat <<YAML | kubectl apply -f -
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: ${release_name}
  namespace: ${ELECTRA_ARGO_NAMESPACE}
spec:
  project: ${ELECTRA_ARGO_PROJECT}
  destination:
    server: https://kubernetes.default.svc
    namespace: ${ELECTRA_NAMESPACE}
  source:
    repoURL: ${ELECTRA_GIT_REPO_URL}
    targetRevision: ${ELECTRA_GIT_TARGET_REVISION}
    path: ${chart_path}
    helm:
      releaseName: ${release_name}
      valueFiles:
        - ${value_file}
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
YAML
}

create_service_application() {
  local name="$1"
  local release_name="${name}-prod"

  cat <<YAML | kubectl apply -f -
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: ${release_name}
  namespace: ${ELECTRA_ARGO_NAMESPACE}
spec:
  project: ${ELECTRA_ARGO_PROJECT}
  destination:
    server: https://kubernetes.default.svc
    namespace: ${ELECTRA_NAMESPACE}
  source:
    repoURL: ${ELECTRA_GIT_REPO_URL}
    targetRevision: ${ELECTRA_GIT_TARGET_REVISION}
    path: ${ELECTRA_GIT_PATH}
    helm:
      releaseName: ${release_name}
      valueFiles:
        - ${name}/values.yaml
        - ../config/services/${name}/us/base.yaml
        - ../config/services/${name}/us/values/dev-values.yaml
        - ../config/services/${name}/us/version/dev-version.yaml
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
YAML
}

create_infra_application \
  "postgresql" \
  "infrastructure/postgresql" \
  "../../platform-config/infrastructure/postgresql/us/values/prod.yaml" \
  "postgresql-prod"

create_infra_application \
  "elasticsearch" \
  "infrastructure/elasticsearch" \
  "../../platform-config/infrastructure/elasticsearch/us/values/prod.yaml" \
  "elasticsearch-prod"

for service in "${SERVICES[@]}"; do
  create_service_application "${service}"
done

echo "Prod Argo CD applications submitted."
echo "Check with: kubectl -n ${ELECTRA_ARGO_NAMESPACE} get applications"

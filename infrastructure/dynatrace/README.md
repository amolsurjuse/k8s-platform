# Dynatrace Setup (dev namespace)

This folder contains the Kubernetes setup required to enable Dynatrace across all workloads deployed in the `dev` namespace.

## Services covered

- `api-gateway`
- `auth-service`
- `billing-service`
- `charger-management-service`
- `driver-portal-ui`
- `electra-hub-org-page`
- `ocpi-service`
- `ocpp-service`
- `ocpp-simulator`
- `ocpp-simulator-ui`
- `payment-service`
- `pricing-service`
- `session-service`
- `station-management-service`
- `subscription-service`
- `user-service`
- `web-socket-connector`

## Inputs required (do not commit)

Export the following variables in your shell:

```bash
export DT_API_URL="https://<your-environment-id>.live.dynatrace.com/api"
export DT_API_TOKEN="<dynatrace-api-token>"
export DT_INGEST_TOKEN="<dynatrace-data-ingest-token>"
```

Required token scopes:

- `apiToken`: Dynatrace Operator token (Kubernetes operator setup scope)
- `dataIngestToken`: `metrics.ingest`, `logs.ingest`, `openTelemetryTrace.ingest`

## Apply setup

```bash
cd /Users/amolsurjuse/development/projects/k8s-platform/infrastructure/dynatrace
./setup-dev.sh
```

## What the script does

1. Ensures namespace `dynatrace` exists.
2. Installs/upgrades Dynatrace Operator (`v1.9.0`, CSI bundle).
3. Creates/updates secret `dynakube` with the provided tokens.
4. Applies `DynaKube` from `dynakube-dev.yaml.tmpl`.
5. Labels namespace `dev` with `dt-monitoring=true`.
6. Restarts all platform deployments in `dev` so injection takes effect.
7. Waits for rollout completion.

## Validation commands

```bash
kubectl -n dynatrace get dynakube,pods
kubectl -n dynatrace describe dynakube dynakube
kubectl -n dev get pods
kubectl -n dev get deploy -o wide
```

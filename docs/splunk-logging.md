# ElectraHub Splunk Logging

This setup deploys Splunk Enterprise and a Vector DaemonSet collector per environment namespace.

## Architecture

```mermaid
flowchart LR
  A[ElectraHub service pods] -->|stdout/stderr| B[Kubernetes container logs]
  B --> C[Vector DaemonSet]
  C -->|Splunk HEC JSON events| D[Splunk /services/collector]
  D --> E[Splunk Search]
```

Services should continue writing logs to stdout/stderr. The collector enriches each event with Kubernetes metadata and parses `traceId` / `spanId` when present in the standard ElectraHub log pattern.

## Namespaces

- Dev Splunk: namespace `dev`, service `splunk`, HEC `http://splunk.dev.svc.cluster.local:8088/services/collector`
- Prod Splunk: namespace `prod`, service `splunk`, HEC `http://splunk.prod.svc.cluster.local:8088/services/collector`

Dev collectors only forward logs from namespace `dev`; prod collectors only forward namespace `prod`.

## Required Secrets

Create these before syncing the Argo CD apps. Use different values for dev and prod.

```powershell
$SplunkPassword = '<strong-admin-password>'
$HecToken = '<uuid-or-random-32-plus-char-token>'

wsl -d Ubuntu-24.04 -- kubectl -n dev create secret generic splunk-secret --from-literal=SPLUNK_PASSWORD=$SplunkPassword
wsl -d Ubuntu-24.04 -- kubectl -n dev create secret generic splunk-hec-secret --from-literal=SPLUNK_HEC_TOKEN=$HecToken

wsl -d Ubuntu-24.04 -- kubectl -n prod create secret generic splunk-secret --from-literal=SPLUNK_PASSWORD=$SplunkPassword
wsl -d Ubuntu-24.04 -- kubectl -n prod create secret generic splunk-hec-secret --from-literal=SPLUNK_HEC_TOKEN=$HecToken
```

If secrets already exist, update them with `kubectl apply` from a local manifest or delete/recreate during a maintenance window.

## Argo CD Apps

- `splunk-dev`
- `splunk-prod`
- `splunk-log-collector-dev`
- `splunk-log-collector-prod`

Dev apps are auto-sync enabled. Prod apps are manual-sync by design.

## Access Splunk

```powershell
wsl -d Ubuntu-24.04 -- kubectl -n dev port-forward svc/splunk 8000:8000
```

Open `http://localhost:8000` and log in as `admin` with the secret password.

For prod, port-forward from namespace `prod` when needed.

## Smoke Test

After Splunk and the collector are running:

```powershell
wsl -d Ubuntu-24.04 -- kubectl -n dev logs deploy/api-gateway --tail=5
wsl -d Ubuntu-24.04 -- kubectl -n dev logs daemonset/splunk-log-collector --tail=80
```

In Splunk search:

```spl
index=main environment=dev | head 20
index=main environment=prod service=session-service | stats count by level
index=main traceId=* | table _time environment service traceId spanId message
```

## Useful Fields

- `environment`: dev/prod
- `cluster`: cluster name from Helm values
- `namespace`: Kubernetes namespace
- `service`: app label or container name
- `app`: release/instance label
- `pod`: Kubernetes pod name
- `container`: container name
- `traceId`: parsed from ElectraHub log line when present
- `spanId`: parsed from ElectraHub log line when present
- `level`: parsed log level when present

## Operational Notes

- Keep HEC tokens and Splunk admin passwords out of Git.
- Rotate HEC tokens periodically and restart the collector after rotation.
- Splunk persistence is single-node PVC storage. Back it up if you care about historical logs.
- The current setup is suitable for low-volume dev/prod-on-k3d. For higher scale, move Splunk to managed Splunk Cloud or a dedicated VM and keep Vector in Kubernetes.

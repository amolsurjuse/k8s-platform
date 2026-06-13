# Windows Machine Migration Plan

This plan moves the ElectraHub local/prod-like stack to a new Windows machine. It covers the Kubernetes platform, Argo CD, TeamCity, PostgreSQL, Elasticsearch, RabbitMQ, Redis, service workloads, ingress, secrets, and validation.

Use this as the step-by-step execution plan. Keep the current machine running until every validation gate passes on the Windows machine.

## 1. Scope and Target State

Target machine:

- Windows 11 Pro or Enterprise.
- Docker Desktop with WSL 2 backend.
- PowerShell 7 preferred.
- Local Kubernetes cluster created by `k3d`.
- Argo CD running inside the new Windows-hosted cluster.
- ElectraHub applications deployed through this `k8s-platform` GitOps repo.
- Stateful systems restored before application cutover.
- TeamCity either migrated to Windows or pointed from an existing TeamCity server to the new cluster.

Applications in scope:

- `admin-portal-ui`
- `api-gateway`
- `auth-service`
- `billing-service`
- `charger-management-service`
- `driver-portal-ui`
- `electra-hub-org-page`
- `notification-service`
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

Platform components in scope:

- Argo CD
- ingress-nginx
- PostgreSQL
- Elasticsearch
- RabbitMQ
- Redis
- Cloudflare tunnel, if public access is required
- local certificates
- Kubernetes secrets and ConfigMaps
- TeamCity pipelines and credentials

## 2. Pre-Migration Inventory

Run these checks on the current machine before changing the new Windows machine.

1. Capture Git state.

```bash
git -C k8s-platform status --short --branch
git -C k8s-platform log -5 --oneline
```

Exit criteria:

- `k8s-platform` has the intended branch and commits.
- Any uncommitted changes are either committed or intentionally excluded.

2. Capture Kubernetes inventory.

```bash
kubectl get nodes -o wide
kubectl get ns
kubectl -n argocd get applications -o wide
kubectl -n dev get deploy,statefulset,svc,ingress,configmap -o wide
kubectl -n dev get pods -o wide
```

Exit criteria:

- Every running workload is listed.
- Current namespace names are confirmed, normally `argocd` and `dev`.

3. Capture image versions.

```bash
find k8s-platform/charts/config/services -name '*values.yaml' -print
find k8s-platform/version -type f -print 2>/dev/null
```

Exit criteria:

- Service image tags for the new environment are known.
- Any manually deployed images are identified.

4. Capture external endpoints.

Record:

- API gateway URL.
- Admin portal URL.
- Driver portal URL.
- OCPP WebSocket URL.
- OCPI base URL.
- Cloudflare tunnel hostname and tunnel ID, if used.
- TeamCity URL.
- Docker registry URL.

Exit criteria:

- DNS and client-facing URLs are documented.
- Anything that must change on Windows is listed.

## 3. Backup Current State

Do not commit plaintext secrets or database dumps to Git.

1. Create a local backup folder outside the repo.

```bash
mkdir -p ~/electrahub-migration-backup
```

2. Export Kubernetes manifests.

```bash
kubectl -n argocd get applications,appprojects -o yaml > ~/electrahub-migration-backup/argocd.yaml
kubectl -n dev get deploy,statefulset,svc,ingress,configmap -o yaml > ~/electrahub-migration-backup/dev-workloads.yaml
kubectl -n dev get secret -o yaml > ~/electrahub-migration-backup/dev-secrets.encrypted-or-local-only.yaml
```

Exit criteria:

- Workloads and Argo CD definitions are backed up.
- Secrets backup is stored securely and is not committed.

3. Back up PostgreSQL.

Prefer logical dumps per database.

```bash
kubectl -n dev get pods -l app.kubernetes.io/name=postgresql
kubectl -n dev exec -it <postgres-pod> -- pg_dumpall -U <postgres-user> > ~/electrahub-migration-backup/postgresql-all.sql
```

If separate service databases exist, also create one `pg_dump` per database.

Exit criteria:

- Dump completes without errors.
- Dump file size is non-zero.
- Restore command has been tested against a disposable database if time allows.

4. Back up Elasticsearch.

Use a snapshot repository if one is configured. If not, plan for reindexing from source systems or API replay.

```bash
kubectl -n dev port-forward svc/elasticsearch 9200:9200
curl http://localhost:9200/_cat/indices?v
curl http://localhost:9200/_cluster/health?pretty
```

Exit criteria:

- Index list is captured.
- Snapshot, reindex, or rebuild approach is selected for each index.
- Critical indices for charger search, pricing, sessions, analytics, and dashboards are identified.

5. Back up RabbitMQ definitions.

```bash
kubectl -n dev get pods -l app.kubernetes.io/name=rabbitmq
kubectl -n dev exec -it <rabbitmq-pod> -- rabbitmqctl list_vhosts
kubectl -n dev exec -it <rabbitmq-pod> -- rabbitmqctl export_definitions /tmp/rabbitmq-definitions.json
kubectl -n dev cp <rabbitmq-pod>:/tmp/rabbitmq-definitions.json ~/electrahub-migration-backup/rabbitmq-definitions.json
```

Exit criteria:

- Exchanges, queues, bindings, users, and vhosts are exported.
- Message backlog migration requirement is explicitly accepted or rejected.

6. Back up Redis if required.

```bash
kubectl -n dev get pods -l app=redis
kubectl -n dev exec -it <redis-pod> -- redis-cli SAVE
kubectl -n dev cp <redis-pod>:/data/dump.rdb ~/electrahub-migration-backup/redis-dump.rdb
```

Exit criteria:

- Redis persistence needs are clear.
- If Redis is only cache/session state, a cold start is approved.

7. Back up TeamCity.

Record:

- TeamCity projects and build configurations.
- VCS roots.
- build agents and agent requirements.
- secure parameters.
- Docker registry credentials.
- GitHub token location.

If migrating the TeamCity server itself, use the official TeamCity backup from the TeamCity UI or server data directory.

Exit criteria:

- TeamCity backup is available, or pipelines can be recreated from `scripts/teamcity/electrahub-services.json`.
- Required secure parameters are known and ready to re-enter on Windows.

## 4. Prepare the Windows Machine

1. Enable Windows features.

Run PowerShell as Administrator:

```powershell
wsl --install
```

Restart if Windows asks.

2. Install required tools.

Install:

- Docker Desktop
- Git for Windows
- PowerShell 7
- `kubectl`
- `helm`
- `k3d`
- `argocd`
- Python 3.9 or newer
- `jq`, optional but useful
- TeamCity server and agent, if TeamCity will run on this machine

Recommended with Chocolatey:

```powershell
choco install -y git powershell-core kubernetes-cli kubernetes-helm k3d argocd-cli python jq
```

Exit criteria:

```powershell
docker version
git --version
kubectl version --client
helm version
k3d version
argocd version --client
python --version
```

3. Configure Docker Desktop.

Recommended minimum:

- CPU: 6 cores
- Memory: 12 GB
- Disk: 80 GB
- WSL 2 backend enabled
- Linux containers enabled

Exit criteria:

- `docker run --rm hello-world` succeeds.
- Docker remains running after Windows sleep and wake testing, or sleep is disabled for the migration window.

4. Clone required repos.

```powershell
mkdir C:\dev
cd C:\dev
git clone <k8s-platform-git-url> k8s-platform
cd C:\dev\k8s-platform
git switch develop
```

Exit criteria:

- `k8s-platform` is on the intended branch.
- Windows has access to all private repos needed by TeamCity.

## 5. Bootstrap Kubernetes and Argo CD

1. Create the Windows environment file.

```powershell
cd C:\dev\k8s-platform
Copy-Item .\prod-release\env\prod-laptop.env.ps1.example .\prod-release\env\prod-laptop.env.ps1
notepad .\prod-release\env\prod-laptop.env.ps1
```

Set:

- cluster name
- namespace
- Git repo URL
- target revision
- ingress hostnames
- image registry references
- Argo CD settings

2. Allow script execution for the current terminal.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

3. Bootstrap the cluster.

```powershell
.\prod-release\scripts\bootstrap-prod-laptop.ps1 -EnvFile .\prod-release\env\prod-laptop.env.ps1
```

Exit criteria:

```powershell
kubectl get nodes
kubectl get ns
kubectl -n argocd get pods
```

- Kubernetes node is `Ready`.
- Argo CD pods are `Running`.

## 6. Restore Secrets and Config

1. Recreate secrets manually or from secure backup.

Required secret categories:

- database passwords
- JWT signing material
- internal API keys
- OAuth client IDs and secrets
- payment provider credentials
- Docker registry pull credentials
- TeamCity GitHub and registry tokens
- Cloudflare tunnel credentials
- TLS certificates

2. Apply non-secret config.

```powershell
kubectl apply -f <sanitized-configmaps-file>
```

Exit criteria:

- No plaintext secrets are added to Git.
- Services can reference expected secret names.
- `kubectl -n <namespace> get secret` shows required secret objects.

## 7. Restore Stateful Platform Components

Restore stateful systems before backend services.

1. Deploy PostgreSQL.

```powershell
.\prod-release\scripts\deploy-prod-release.ps1 -EnvFile .\prod-release\env\prod-laptop.env.ps1
kubectl -n prod get pods -l app.kubernetes.io/name=postgresql
```

2. Restore PostgreSQL data.

```powershell
kubectl -n prod cp C:\path\to\postgresql-all.sql <postgres-pod>:/tmp/postgresql-all.sql
kubectl -n prod exec -it <postgres-pod> -- psql -U <postgres-user> -f /tmp/postgresql-all.sql
```

Exit criteria:

- All expected databases exist.
- Liquibase history tables are present.
- Application users and roles are present.

3. Deploy Elasticsearch.

```powershell
kubectl -n prod get pods -l app.kubernetes.io/name=elasticsearch
kubectl -n prod port-forward svc/elasticsearch 9200:9200
curl.exe http://localhost:9200/_cluster/health?pretty
```

4. Restore or rebuild Elasticsearch indices.

Use one of:

- restore from Elasticsearch snapshot.
- reindex from source cluster.
- rebuild from PostgreSQL and service APIs.
- allow services to republish documents from their normal event streams.

Exit criteria:

- Cluster health is `yellow` or `green`.
- Required indices exist.
- Charger search, pricing reads, session reads, and dashboard queries return data.

5. Deploy RabbitMQ.

```powershell
kubectl -n prod get pods -l app.kubernetes.io/name=rabbitmq
```

Restore definitions if required:

```powershell
kubectl -n prod cp C:\path\to\rabbitmq-definitions.json <rabbitmq-pod>:/tmp/rabbitmq-definitions.json
kubectl -n prod exec -it <rabbitmq-pod> -- rabbitmqctl import_definitions /tmp/rabbitmq-definitions.json
```

Exit criteria:

- Required exchanges, queues, and bindings exist.
- Services can connect without authentication errors.

6. Deploy Redis if needed.

If Redis state must move:

```powershell
kubectl -n prod cp C:\path\to\redis-dump.rdb <redis-pod>:/data/dump.rdb
kubectl -n prod rollout restart statefulset/redis
```

Exit criteria:

- Redis pod starts cleanly.
- Services that depend on Redis can connect.

## 8. Deploy Applications

Deploy in this order.

1. Platform and ingress:

- `ingress-nginx`
- local certificates or cert-manager equivalent
- Cloudflare tunnel, if required

2. Foundation services:

- `auth-service`
- `user-service`
- `api-gateway`

3. Core business services:

- `station-management-service`
- `charger-management-service`
- `pricing-service`
- `billing-service`
- `payment-service`
- `subscription-service`
- `session-service`
- `notification-service`

4. Protocol and simulator services:

- `ocpp-service`
- `ocpi-service`
- `web-socket-connector`
- `ocpp-simulator`
- `ocpp-simulator-ui`

5. UI applications:

- `admin-portal-ui`
- `driver-portal-ui`
- `electra-hub-org-page`

Commands:

```powershell
.\prod-release\scripts\deploy-prod-release.ps1 -EnvFile .\prod-release\env\prod-laptop.env.ps1
kubectl -n argocd get applications
kubectl -n prod get pods -o wide
```

Exit criteria:

- Argo CD applications are `Synced`.
- Argo CD applications are `Healthy`.
- All pods are `Running` or `Completed`.
- No pod has repeated restarts.

## 9. Migrate or Rebuild TeamCity

Choose one TeamCity model.

### Option A: Move TeamCity to Windows

1. Install TeamCity server on Windows.
2. Restore the TeamCity server backup.
3. Install at least one Windows or Docker-capable build agent.
4. Configure Docker access for the agent.
5. Re-enter secure parameters:

- `github.user`
- `github.token`
- Docker registry credentials
- deployment token or kubeconfig, if the pipeline updates Kubernetes directly

Exit criteria:

- TeamCity server is reachable.
- Agent is connected and authorized.
- A test build can run Docker commands.

### Option B: Recreate Pipelines on Existing TeamCity

Use the portable bootstrap script from this repo:

```powershell
cd C:\dev\k8s-platform
$env:TEAMCITY_URL = "http://<teamcity-host>:8111"
$env:TEAMCITY_TOKEN = "<token>"
.\scripts\teamcity\create_pipeline.ps1 -Config .\scripts\teamcity\electrahub-services.json
```

Exit criteria:

- Every ElectraHub service pipeline exists.
- VCS triggers point to the correct default branches.
- Build steps can push images.
- Version update steps can commit back to `k8s-platform`.

### Option C: Existing TeamCity, New Windows Build Agent

1. Install TeamCity agent on Windows.
2. Register it with existing TeamCity.
3. Add agent requirements or pools for ElectraHub builds.
4. Verify Docker build and push.

Exit criteria:

- Builds run on the Windows agent.
- Images are pushed to the expected registry.
- `k8s-platform` image tag updates are committed.

## 10. Network, DNS, and Access Cutover

1. Validate local ingress.

```powershell
kubectl -n prod get ingress
kubectl -n prod get svc
```

2. Validate hostnames from Windows.

```powershell
curl.exe -k https://<api-host>/actuator/health
curl.exe -k https://<admin-host>
```

3. Move Cloudflare tunnel if used.

Required checks:

- tunnel credential file exists on Windows.
- tunnel config points to Windows ingress.
- DNS route targets the tunnel.
- old tunnel is stopped before final cutover.

Exit criteria:

- Public URLs resolve to the Windows machine.
- API, admin portal, driver portal, OCPP, and OCPI endpoints respond.

## 11. Validation Gates

Run validation in this order.

1. Platform health.

```powershell
kubectl get nodes
kubectl -n argocd get applications
kubectl -n prod get pods
kubectl -n prod get events --sort-by=.lastTimestamp
```

2. Data health.

- PostgreSQL schemas and row counts look correct.
- Elasticsearch critical indices exist.
- RabbitMQ queues and exchanges exist.
- Redis is reachable if enabled.

3. Authentication.

- Admin login works.
- Driver login works.
- OAuth login works if configured.
- Terms acceptance flow works if enabled.

4. Admin portal.

- users load.
- chargers load.
- connectors load.
- pricing loads.
- subscriptions load.
- audit logs load.
- dashboard charts load.

5. Driver portal and mobile flows.

- nearby chargers load.
- charger detail loads.
- active session loads.
- wallet/payment state loads.
- receipts load.
- notification preferences load.

6. Charging protocol flows.

- OCPP simulator connects.
- remote start works.
- meter values are ingested.
- remote stop works.
- session closes.
- billing record or receipt is created.
- OCPI charger/location/session endpoints respond.

7. TeamCity.

- build one backend service.
- build one frontend service.
- push an image.
- update a service image version in `k8s-platform`.
- Argo CD syncs the changed version.

Exit criteria:

- All validation gates pass.
- Any known issue has an owner and rollback decision.

## 12. Rollback Plan

Rollback triggers:

- critical service cannot start.
- PostgreSQL restore is incomplete.
- Elasticsearch cannot serve required reads.
- ingress or public routing is unstable.
- authentication fails.
- payment/session/charging flows fail.

Rollback steps:

1. Stop Windows cutover traffic.
2. Point DNS or Cloudflare route back to the current machine.
3. Stop TeamCity triggers that deploy to the Windows environment.
4. Keep Windows cluster running for investigation unless it is corrupt.
5. Restore from backups only after preserving logs and events.

Useful commands:

```powershell
kubectl -n prod get pods
kubectl -n prod describe pod <pod>
kubectl -n prod logs <pod> --tail=200
kubectl -n argocd get applications
```

## 13. Post-Migration Cleanup

Complete these tasks after validation.

1. Update docs with final Windows hostnames and ports.
2. Rotate any secrets copied during migration.
3. Disable stale tunnels on the old machine.
4. Disable old TeamCity agents or move them to a separate pool.
5. Archive migration backups in secure storage.
6. Confirm no database dumps, kubeconfigs, or secret files were committed.
7. Create a follow-up issue for replacing any raw manifests with Argo CD applications, especially Redis if still deployed from `redis/`.

## 14. Final Sign-Off Checklist

- Windows Docker Desktop is stable.
- Kubernetes node is ready.
- Argo CD is healthy.
- PostgreSQL restored.
- Elasticsearch restored or rebuilt.
- RabbitMQ restored or recreated.
- Redis decision completed.
- All backend services healthy.
- All UI services healthy.
- Public ingress works.
- TeamCity can build and publish.
- End-to-end charging flow passes.
- Rollback path remains available until at least one full working day after cutover.

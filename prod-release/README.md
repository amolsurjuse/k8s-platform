# ElectraHub Prod Laptop Release

This folder contains the runbook and scripts for promoting the current dev-tested ElectraHub stack to a separate laptop running its own Docker-backed Kubernetes cluster.

The new laptop should be treated as a Prod environment: separate Docker instance, separate Kubernetes cluster, separate data volumes, separate secrets, and its own externally reachable URLs.

## Target Model

- Current laptop: development and validation environment.
- New laptop: Prod release environment.
- Kubernetes runtime on new laptop: `k3d` by default, because it runs cleanly on Docker and is easy to recreate.
- GitOps controller: Argo CD can either run on the current laptop and deploy to the new laptop cluster, or run directly inside the new laptop cluster.

## Can One Argo CD Manage Another Machine's Cluster?

Yes. Argo CD can run on one Kubernetes cluster and deploy workloads to another Kubernetes cluster, as long as:

- Argo CD can reach the target cluster Kubernetes API server over the network.
- The target cluster has a stable API endpoint, for example `https://prod-laptop-ip:6443`.
- The target cluster certificate and kubeconfig credentials are registered with Argo CD.
- Firewalls, VPN, local network routing, and laptop sleep settings allow persistent connectivity.
- The target cluster service account used by Argo CD has enough RBAC permissions.

For a local-laptop Prod environment, the most reliable option is usually to run Argo CD inside the prod laptop cluster. Central Argo works too, but it depends on network reachability between laptops and a stable target API endpoint.

## Folder Layout

- `env/prod-laptop.env.example` - environment variables used by scripts.
- `env/prod-laptop.env.ps1.example` - PowerShell environment variables for Windows.
- `scripts/bootstrap-prod-laptop.sh` - creates the Docker-backed Kubernetes cluster and installs core platform tooling.
- `scripts/bootstrap-prod-laptop.ps1` - Windows PowerShell version of the bootstrap script.
- `scripts/register-prod-cluster-with-argocd.sh` - registers the prod laptop cluster with an existing Argo CD instance.
- `scripts/register-prod-cluster-with-argocd.ps1` - Windows PowerShell version of the Argo CD registration script.
- `scripts/deploy-prod-release.sh` - applies Prod Argo CD applications from this repo.
- `scripts/deploy-prod-release.ps1` - Windows PowerShell version of the deployment script.
- `docs/migration-runbook.md` - operational migration checklist.
- `docs/windows-machine-migration-plan.md` - step-by-step plan for moving Kubernetes, TeamCity, PostgreSQL, Elasticsearch, RabbitMQ, Redis, secrets, ingress, and applications to a Windows machine.
- `docs/repository-inventory.md` - list of local repositories, Git URLs, branches, and latest local commits for migration planning.

## Prerequisites

Install these tools on the target laptop and make sure they are available on `PATH`:

- Docker Desktop with the Linux container engine running.
- `kubectl`.
- `helm`.
- `k3d`.
- `argocd` only if registering this cluster with an Argo CD instance running somewhere else.

On Windows, run the scripts from PowerShell 7 or Windows PowerShell. Docker Desktop must be started before running the bootstrap step.

## Recommended First Pass - macOS/Linux

1. Copy `env/prod-laptop.env.example` to `env/prod-laptop.env`.
2. Fill in hostnames, cluster name, repo URL, target revision, and optional Argo settings.
3. On the new laptop, run:

```bash
./prod-release/scripts/bootstrap-prod-laptop.sh ./prod-release/env/prod-laptop.env
```

4. If Argo CD runs on the current laptop and should deploy to the new laptop, run:

```bash
./prod-release/scripts/register-prod-cluster-with-argocd.sh ./prod-release/env/prod-laptop.env
```

5. Deploy the release applications:

```bash
./prod-release/scripts/deploy-prod-release.sh ./prod-release/env/prod-laptop.env
```

## Recommended First Pass - Windows PowerShell

1. Copy `env\prod-laptop.env.ps1.example` to `env\prod-laptop.env.ps1`.
2. Fill in hostnames, cluster name, repo URL, target revision, and optional Argo settings.
3. On the new laptop, run:

```powershell
.\prod-release\scripts\bootstrap-prod-laptop.ps1 -EnvFile .\prod-release\env\prod-laptop.env.ps1
```

4. If Argo CD runs on the current laptop and should deploy to the new laptop, run:

```powershell
.\prod-release\scripts\register-prod-cluster-with-argocd.ps1 -EnvFile .\prod-release\env\prod-laptop.env.ps1
```

5. Deploy the release applications:

```powershell
.\prod-release\scripts\deploy-prod-release.ps1 -EnvFile .\prod-release\env\prod-laptop.env.ps1
```

If PowerShell blocks local scripts, allow the current process to run them:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

## Release Order

Deploy stateful and platform pieces first:

1. `postgresql`
2. `elasticsearch`
3. `redis` if session/cache persistence is required in this cluster
4. `ingress-nginx`
5. `api-gateway`
6. core services: `auth-service`, `user-service`, `payment-service`, `pricing-service`, `charger-management-service`, `session-service`
7. OCPP/OCPI services and simulator
8. portal UIs

`deploy-prod-release.sh` and `deploy-prod-release.ps1` create Argo CD applications for Postgres, Elasticsearch, and the service stack. Redis is currently represented by raw manifests under `redis/`; move it into a Helm/Argo app before using this as a hard prod deployment.

## Validation

After deployment:

```bash
kubectl get nodes
kubectl get pods -n prod
kubectl -n argocd get applications
```

Windows PowerShell uses the same validation commands:

```powershell
kubectl get nodes
kubectl get pods -n prod
kubectl -n argocd get applications
```

Then validate:

- Login through driver portal.
- `GET /payment/api/v1/payment/state`.
- `GET /session/api/v1/sessions/active`.
- `POST /charger/graphql` with `ocpiChargers`.
- OCPP simulator connects and receives remote start/stop flows.
- Receipts and active sessions behave as expected.

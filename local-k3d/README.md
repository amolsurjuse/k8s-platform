# ElectraHub Local k3d Environment

This folder supports the local Windows PC setup that runs separate k3d clusters for dev and prod behind Cloudflare Tunnel.

## Target Model

```text
Windows 11
  |
WSL2 Ubuntu + Docker Desktop
  |
  +-- k3d-electrahub-dev
  |     +-- namespace: dev
  |     +-- Argo CD project: electrahub-dev
  |     +-- tunnel: electrahub-dev-tunnel
  |
  +-- k3d-electrahub-prod
        +-- namespace: prod
        +-- Argo CD project: electrahub-prod
        +-- tunnel: electrahub-prod-tunnel
```

## Argo CD Placement

Run Argo CD inside both clusters.

This is not strictly required, but it keeps dev and prod isolated:

- Dev Argo CD manages only the dev cluster.
- Prod Argo CD manages only the prod cluster.
- Each cluster can be rebuilt independently.
- A bad dev sync cannot directly affect prod.

For a small local setup this costs some memory, but it is operationally cleaner than one shared Argo CD managing both clusters.

## Hostname Convention

Use one-label Cloudflare hostnames where possible:

```text
dev.electrahub.net
api-dev.electrahub.net
admin-portal-dev.electrahub.net
driver-portal-dev.electrahub.net
ocpp-simulator-dev.electrahub.net
argocd-dev.electrahub.net

electrahub.net
www.electrahub.net
api.electrahub.net
argocd.electrahub.net
```

The one-label dev hostnames are intentional because standard Cloudflare Universal SSL covers `*.electrahub.net`, while nested names such as `api.dev.electrahub.net` may require an advanced certificate.

## Files

- `env/local-k3d.env.example` - Bash environment values.
- `env/local-k3d.env.ps1.example` - PowerShell environment values.
- `scripts/bootstrap-local-k3d.sh` - creates namespaces, installs Argo CD, creates AppProjects, and prints next steps.

## First Pass

From WSL Ubuntu:

```bash
cd /mnt/c/development/project/k8s-platform
cp local-k3d/env/local-k3d.env.example local-k3d/env/local-k3d.env
./local-k3d/scripts/bootstrap-local-k3d.sh local-k3d/env/local-k3d.env
```

Then deploy foundation services in this order:

1. PostgreSQL
2. Redis
3. RabbitMQ if needed by the current app set
4. API gateway
5. Auth and core services
6. Portal UIs
7. Optional heavier services such as Elasticsearch

Do not deploy the full service list on the first pass unless Docker Desktop has enough memory assigned.

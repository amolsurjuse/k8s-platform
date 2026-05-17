# Migration Runbook: Current Laptop to Prod-like Laptop

## 1. Prepare the Prod-like Laptop

Install:

- Docker Desktop
- `kubectl`
- `helm`
- `k3d`
- `argocd`
- `jq`

Allocate enough Docker resources:

- CPU: 6 or more
- Memory: 12 GB or more
- Disk: 80 GB or more

## 2. Bootstrap Kubernetes and Argo CD

On the prod-like laptop:

```bash
cp prod-release/env/prod-laptop.env.example prod-release/env/prod-laptop.env
./prod-release/scripts/bootstrap-prod-laptop.sh ./prod-release/env/prod-laptop.env
```

This creates a separate Kubernetes cluster and installs Argo CD.

## 3. Decide Argo CD Topology

Option A: Argo CD runs on the prod-like laptop.

- Most reliable for a laptop-based prod-like environment.
- No cross-laptop Kubernetes API dependency.
- Recommended for early release testing.

Option B: Argo CD runs on the current laptop and manages the prod-like laptop cluster.

- Works when current laptop can reach the prod-like laptop Kubernetes API.
- Requires `argocd cluster add <prod-context>`.
- Requires stable networking, certificates, and RBAC.

## 4. Backup Current Dev Data

Recommended stateful backups:

```bash
kubectl -n dev get deploy,statefulset,svc,ingress,configmap -o yaml > dev-workloads.yaml
kubectl -n argocd get applications,appprojects -o yaml > argocd-dev.yaml
```

For databases, prefer service-specific backup scripts. At minimum:

- PostgreSQL: `pg_dump` per database.
- Redis: RDB/AOF snapshot if session/cache data must move.
- Elasticsearch: snapshot or reindex from source APIs.

Do not commit plaintext secrets to Git.

## 5. Restore Stateful Systems First

Deploy or restore in this order:

1. PostgreSQL
2. Elasticsearch
3. Redis, if required by the selected runtime profile
4. ConfigMaps and Secrets
5. Backend services
6. Simulator and UI services
7. Ingress and certificates

## 6. Deploy Services

```bash
./prod-release/scripts/deploy-prod-release.sh ./prod-release/env/prod-laptop.env
kubectl -n argocd get applications
kubectl -n prod get pods
```

## 7. Validation Checklist

Run these checks before calling the prod-like laptop healthy:

- `api-gateway` ingress responds.
- `auth-service` login works.
- `payment-service` wallet state loads.
- `session-service` active sessions load.
- `charger-management-service` GraphQL `ocpiChargers` works.
- Driver Portal iOS can load nearby chargers.
- OCPP simulator connects to `ocpp-service`.
- Remote start/stop works through simulator.
- Session receipt calculation works.

## 8. Rollback

Keep the current laptop dev environment untouched until prod-like validation passes.

Rollback options:

- Point clients back to the current laptop URLs.
- Revert the GitOps image version files.
- Restore database snapshots.
- Recreate the prod-like cluster with `k3d cluster delete` and bootstrap again.

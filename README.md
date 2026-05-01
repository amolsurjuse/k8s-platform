# k8s-platform

GitOps repository for ElectraHub platform deployments using Argo CD + Helm.

## Repository Purpose

- Argo CD applications for services and infrastructure
- Shared Helm charts (`charts/common`, infra charts)
- Environment-specific values (`charts/config/services/...`)
- Version pinning per environment (`version/*.yaml`)

## Common Workflow

1. Update service/infrastructure values under `charts/config/...`.
2. Commit to `develop`.
3. Argo CD auto-sync (or manual sync) applies changes to target namespace.

## Useful Paths

- `argocd/applications/` - Argo CD Application manifests
- `charts/common/` - shared deploy chart
- `charts/config/services/` - per-service env config
- `platform-config/infrastructure/` - infra value sets

## Quick Validation

```bash
kubectl -n argocd get applications
kubectl -n dev get pods
```

## Updated

- README reviewed and refreshed on `2026-03-21`.

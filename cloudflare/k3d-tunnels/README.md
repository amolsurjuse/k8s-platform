# Cloudflare Tunnels For k3d

These manifests keep the in-cluster `cloudflared` routes for the local k3d dev and prod clusters.

They do not store Cloudflare credentials. Each cluster must already have the `cloudflare` namespace, the tunnel credential secret, and the `cloudflared` deployment.

## Apply

```bash
./cloudflare/k3d-tunnels/apply.sh dev
./cloudflare/k3d-tunnels/apply.sh prod
```

The script applies the environment ConfigMap and restarts `deployment/cloudflared` in the `cloudflare` namespace.

## Public Hostnames

Dev tunnel:

- `dev.electrahub.net`
- `api-dev.electrahub.net`
- `api.dev.electrahub.net`
- `admin-dev.electrahub.net`
- `admin-portal-dev.electrahub.net`
- `driver-dev.electrahub.net`
- `driver-portal-dev.electrahub.net`
- `simulator-dev.electrahub.net`
- `ocpp-simulator-dev.electrahub.net`
- `grafana.dev.electrahub.net`
- `argocd-dev.electrahub.net`
- `argocd.dev.electrahub.net`

Prod tunnel:

- `electrahub.net`
- `www.electrahub.net`
- `api.electrahub.net`
- `admin-portal.electrahub.net`
- `driver-portal.electrahub.net`
- `ocpp-simulator.electrahub.net`
- `grafana.electrahub.net`
- `argocd.electrahub.net`

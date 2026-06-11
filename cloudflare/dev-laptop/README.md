# Cloudflare Tunnel For This Laptop

This folder supports the current laptop/minikube dev setup. It does not store credentials.

The tunnel runs locally on the laptop and forwards Cloudflare hostnames to the existing `kubectl port-forward` origins:

- `127.0.0.1:8443` -> ingress-nginx HTTPS
- `127.0.0.1:9443` -> Argo CD HTTPS

## Quick Start

```bash
cd /Users/amolsurjuse/development/projects/k8s-platform
cp cloudflare/dev-laptop/env.example .local/cloudflare-dev-laptop.env
```

Edit `.local/cloudflare-dev-laptop.env` with your tunnel ID and credentials file.

Start origins:

```bash
./scripts/port-forward.sh --close-in-use ingress argocd
```

Render config:

```bash
./scripts/cloudflare-dev-laptop/render-config.sh .local/cloudflare-dev-laptop.env
```

Create DNS routes once:

```bash
./scripts/cloudflare-dev-laptop/print-dns-routes.sh .local/cloudflare-dev-laptop.env
```

Start the tunnel:

```bash
./scripts/cloudflare-dev-laptop/start-tunnel.sh .local/cloudflare-dev-laptop.env
```

## Public Hostnames

Defaults:

- `electrahub.net`
- `www.electrahub.net`
- `dev.electrahub.net`
- `api-dev.electrahub.net`
- `admin-dev.electrahub.net`
- `admin-portal-dev.electrahub.net`
- `driver-dev.electrahub.net`
- `driver-portal-dev.electrahub.net`
- `simulator-dev.electrahub.net`
- `ocpp-simulator-dev.electrahub.net`
- `argocd-dev.electrahub.net`

The one-label hostname pattern is intentional: standard Cloudflare Universal SSL usually covers `*.electrahub.net`, but not nested names like `api.dev.electrahub.net`.

The generated config uses `httpHostHeader` so the current `.com` ingress hosts keep working while Cloudflare exposes `.net`.

See the full design note:

```text
/Users/amolsurjuse/development/projects/electrahub-design-docs/design/cloudflare-dev-laptop-tunnel/plan.md
```

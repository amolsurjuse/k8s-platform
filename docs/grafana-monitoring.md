# ElectraHub Grafana Monitoring

## Access

Production Grafana is exposed through Cloudflare Tunnel and ingress:

- https://grafana.electrahub.net

The admin username/password are stored in the `monitoring` namespace:

```powershell
wsl -d Ubuntu-24.04 -- kubectl -n monitoring get secret grafana-admin-secret -o jsonpath="{.data.GF_SECURITY_ADMIN_USER}"
wsl -d Ubuntu-24.04 -- kubectl -n monitoring get secret grafana-admin-secret -o jsonpath="{.data.GF_SECURITY_ADMIN_PASSWORD}"
```

Decode each value from base64 before using it.

## Components

- `prometheus` runs in namespace `monitoring` and scrapes ElectraHub backend services.
- `grafana` runs in namespace `monitoring` and is provisioned with the `ElectraHub Prometheus` datasource.
- Starter dashboard: `ElectraHub / ElectraHub Service Health`.

## Registered backend scrape targets

Prometheus is configured to scrape `/actuator/prometheus` for:

- api-gateway
- auth-service
- user-service
- session-service
- payment-service
- billing-service
- charger-management-service
- station-management-service
- ocpp-service
- ocpi-service
- pricing-service
- subscription-service
- notification-service
- ai-support-service

## Validation

Port-forward Prometheus and check target health:

```powershell
wsl -d Ubuntu-24.04 -- kubectl -n monitoring port-forward svc/prometheus 19090:9090
```

Then open:

- http://localhost:19090/targets

Or query:

```promql
up{environment="prod"}
```

## Known follow-up

Some services currently return `401`, `403`, `404`, or `500` on `/actuator/prometheus`. The platform registration is complete, but those services need app-level actuator/security/Prometheus-registry fixes before all targets become green.
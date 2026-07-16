# Production Observability

Production uses complementary, non-duplicative observability paths:

- Prometheus scrapes application metrics and Grafana visualizes them.
- Vector sends production container logs to Splunk and Elasticsearch.
- Kibana provides log search over the `electrahub-kubernetes-*` indexes. The index lifecycle deletes logs after seven days.
- Dynatrace supplies Kubernetes topology, Java application monitoring, traces, and metrics. Its production bootstrap intentionally excludes log export because Splunk and Elasticsearch already retain logs.

## Dynatrace activation

The Dynatrace configuration is production-only. Provide a Dynatrace SaaS environment URL ending in `/api`, an Operator token, and a data-ingest token with the required Dynatrace scopes. Do not commit those values.

```bash
export DT_API_URL="https://<environment>.live.dynatrace.com/api"
export DT_API_TOKEN="<operator-token>"
export DT_INGEST_TOKEN="<data-ingest-token>"
./infrastructure/dynatrace/setup-prod.sh
```

This labels only the `prod` namespace for Dynatrace injection.

## Validation

```bash
kubectl -n monitoring exec deploy/prometheus -- wget -qO- http://localhost:9090/api/v1/targets
kubectl -n prod get pods -l app.kubernetes.io/name=kibana
kubectl -n dynatrace get dynakube,pods
```

# Private Sparky MCP deployment

This chart is deployed by `argocd/applications/support-mcp-service-prod.yaml` into
the `prod` namespace of `k3d-electrahub-prod`. Publish the MCP artifact to
`amolsurjuse/ai-support-service`, set its distinct `sparky-mcp-20261005-<source>`
tag and immutable digest in `values.yaml`, then render before promotion. Rendering
fails if the digest is missing. The AI host remains a separately published image.

The only tool listener is the ClusterIP service on port 8095. NetworkPolicy allows
that port from the existing AI host's `app.kubernetes.io/instance=ai-support-service`
pods in the same namespace. Management port 8097 has a separate ClusterIP service
and permits only Prometheus in `monitoring`; the production Prometheus values
include that target. There is no public ingress or NodePort.

The pod uses the existing shared `access-context-prod-secret` identity key. Tool
requests still require signed current identity, an original bearer and the
application's System Admin/Support and session-scope checks. Internal placement
does not grant access to customer records.

The collector's service account can only get/list Services, Deployments,
StatefulSets and EndpointSlices in this release namespace. It cannot read Secrets,
pod logs or cluster-wide resources. It reconciles every 60 seconds; snapshots have
a 180-second TTL. Its 1Gi `local-path` PVC contains bounded sanitized infrastructure
memory, and the one-replica `Recreate` strategy preserves one writer.

Cluster networking was observed on October 5, 2026: Kubernetes service
`10.43.0.1:443`, EndpointSlice destination `172.19.0.4:6443`. NetworkPolicy permits
those exact destinations plus cluster DNS and the gateway. Re-check the API
EndpointSlice when changing/recreating k3d, and update the narrow CIDR accordingly.

Release ordering is domain/session read APIs and user-service RBAC migration,
gateway restriction, private MCP, AI host with `AI_SUPPORT_MCP_ENABLED=true`, then
the portal. The session values map its payment diagnostics credential to the
payment workload's existing `electrahub-internal-service-token` secret. Do not
replace it with an unrelated gateway identity credential.

After release, verify pod health, authorized and denied tool access, fresh cluster
snapshots, persisted memory after restart, and the Prometheus target plus emitted
HTTP histogram samples. Low-cardinality `service`, `env`, `region` and `version`
tags are configured; no customer IDs are metric labels. No SLA or production
baseline is asserted by this release.

To disable MCP transport, revert the AI host's MCP flag and roll out that host.
The new role restrictions remain active in the direct scoped report. Disable the
collector separately if cluster reads need to stop. Preserve the volume during a
rollback; it contains no customer/session cache.

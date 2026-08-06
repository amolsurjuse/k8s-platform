# AI multi-tenancy operations

## Trust boundary

The API gateway derives `tenantId`, `userId`, and roles from the signed access token and signs the internal access context sent to `ai-support-service`. The AI service rejects a caller-supplied tenant header when it does not match that signed context. Request payload attributes are never used to select a tenant.

All chat threads, tool calls, audit events, quota keys, and tenant knowledge selection use the trusted tenant identity.

## Tenant policy

Tenant overrides are supplied through `AI_TENANT_POLICIES_JSON`. An omitted tenant inherits the safe platform defaults and receives no tenant-specific knowledge.

```json
{
  "tenant-a": {
    "enabled": true,
    "requestsPerMinute": 30,
    "requestsPerDay": 2000,
    "tokensPerDay": 500000,
    "knowledge": [
      "Tenant A support desk is available around the clock."
    ],
    "allowedAdminTools": [
      "admin.analytics.overview",
      "admin.chargers.status-summary"
    ]
  }
}
```

Supported admin tool names are defined by `AdminToolRegistry`. An empty allow-list disables admin tools for that tenant. `"*"` enables the current registry. Mutating `admin.session.stop` must be named explicitly unless `"*"` is used, and it still requires the normal role check and one-time confirmation.

Policy JSON is parsed at startup. Invalid JSON or non-positive quotas fail startup rather than silently weakening isolation.

## Distributed quotas

Development and production use the environment-specific Redis service and secret. One atomic Redis script reserves, per tenant:

- requests in the current UTC minute;
- requests in the current UTC day;
- estimated input and bounded output tokens in the current UTC day.

The keys share a Redis Cluster hash tag for atomic execution. Production is fail-closed: if Redis usage controls are unavailable, new AI work returns `503`; an exceeded limit returns `429`. Quota decisions are written to the `AI_AUDIT` logger without prompt content.

Current platform defaults:

| Setting | Value |
|---|---:|
| Requests per minute | 60 |
| Requests per day | 5,000 |
| Tokens per day | 1,000,000 |

## Retrieval isolation

Common ElectraHub behavior remains in the application knowledge base. Tenant-specific guidance is selected only after trusted identity resolution and only the selected tenant's entries are added to the model prompt. The prompt formatter has no access to request-supplied tenant attributes.

## Release gate

Every AI service build runs the full Maven suite, including:

- application-context startup;
- signed identity mismatch rejection;
- tenant-isolated thread/event storage;
- exact tenant policy and knowledge selection;
- traversal-like tenant ID rejection;
- tenant admin-tool allow-list behavior;
- malformed policy startup failure;
- prompt cross-tenant leakage checks;
- quota token-estimation boundaries;
- deterministic answer and model quality guards;
- streaming provider behavior.

Deploy to development first. Verify Argo health, zero restarts, authenticated chat, `event=ai_quota ... outcome=allowed`, admin read tools, SSE `token` through `done`, and payment/charger regression reads. Promote the same immutable image tag to production and repeat those checks.

## Pending access decision

Scoped `LOCATION`, `NETWORK`, and `ENTERPRISE` administrators do not currently pass the gateway's customer terms gate for `/ai/**`. The prepared gateway/user policy change is intentionally unpublished until the security-boundary expansion is explicitly approved. `SYSTEM_ADMIN` access continues to work under the existing role set.

# CPMS production SLO, disaster recovery, and incident runbook

Date: 2026-08-12

## Provisional service objectives

These objectives provide actionable monitoring while business owners finalize
contractual SLOs. They are not a claim that a measured monthly objective has
already been achieved.

- Core API availability: 99.9% per calendar month, excluding approved maintenance.
- Core API server-error ratio: below 1% monthly; page at 5% for 10 minutes.
- Core API p95 server latency: below 2 seconds; warn after 10 minutes.
- Dashboard projection freshness: below 120 seconds; critical at 15 minutes.
- Proposed database recovery point objective: 15 minutes.
- Proposed critical-service recovery time objective: 4 hours.

## Incident response

1. Acknowledge the page, name an incident commander and scribe, and record UTC
   start time, affected services, tenants, countries, and charging impact.
2. Check Argo health, Kubernetes rollout/pod events, Prometheus targets, error
   ratio, latency, database saturation, Kafka/RabbitMQ lag, Redis, and ingress.
3. Freeze risky configuration changes. Preserve payment, CDR, signed-meter,
   OCPP command, and audit evidence; do not truncate queues or tables.
4. Prefer a GitOps rollback to the last healthy immutable image. Keep additive
   migrations unless a reviewed backward migration exists.
5. For unsafe site load-management behavior, retain the conservative fallback
   or safety profile before disabling actuation.
6. Communicate status at least every 30 minutes for a severity-one incident.
7. Close only after health, charging start/stop, payment, session settlement,
   dashboard, and OCPI smoke checks pass. Create a blameless review within two
   business days.

## Backup and restore drill

Run quarterly in an isolated namespace or cluster; never restore over production.

1. Record backup IDs, timestamps, encryption/key owner, retention, and checksums
   for PostgreSQL and other authoritative durable stores.
2. Provision an isolated target from the same GitOps revision and restore the
   latest full backup plus supported incrementals/WAL.
3. Rebuild derived Redis and Elasticsearch state from authoritative stores or
   event streams. Restore broker state only where replay cannot reconstruct it.
4. Validate schema versions, row counts, ledger balance, immutable CDR hashes,
   signed-meter evidence, charger inventory, and tenant isolation.
5. Run health and functional smoke checks without contacting real chargers or
   payment providers. Use sandbox endpoints and deny external egress by default.
6. Measure actual recovery point and recovery time. The drill passes only when
   both are within the approved RPO/RTO and every integrity check succeeds.
7. Destroy the isolated environment, retain sanitized evidence, and assign
   remediation owners and due dates for every failed check.

## Production acceptance gate

The monitoring configuration is deployable immediately. DR acceptance remains
conditional until a named owner completes a dated restore drill and records
actual RPO/RTO evidence in the production acceptance register.

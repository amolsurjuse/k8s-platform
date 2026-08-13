# Stage 9: OCPP Security Profile 3 and fleet PKI

## Purpose

Move production chargers from distinct Profile 2 credentials to mutually
authenticated TLS without causing a fleet-wide reconnect outage.

## Trust boundary

Cloudflare terminates charger TLS before traffic enters Kubernetes. Cloudflare
must validate the charger certificate and replace any client-supplied
`Client-Cert` header with the verified RFC 9440 certificate value. The OCPP
service then performs a second authorization decision by exact certificate
fingerprint, charger ID, validity, revocation state, key strength, and identity.

Nginx ingress client-certificate authentication is not used: it would validate
Cloudflare's connection rather than the charger's certificate.

## Rollout gates

1. Deploy OCPP image 44 with `OCPP_MTLS_MODE=AUDIT`. Profile 2 stays enforced.
2. Confirm the certificate schema migrated, both OCPP replicas are ready, and
   existing fleet ownership/reconnect/error KPIs are unchanged.
3. Issue one certificate per charger from a Cloudflare-managed client CA. Keep
   private keys on the charger or provisioning system; never store them in Git,
   Kubernetes configuration, or the CSMS database.
4. Configure Cloudflare to remove client-provided certificate headers, validate
   the certificate, and forward only the verified RFC 9440 value.
5. Bind each leaf SHA-256 fingerprint to its charge-point ID through the
   internal certificate API. Validate expiry, revocation, ID mismatch, and
   missing-certificate negative cases.
6. Canary at least one production-equivalent charger. Require successful
   reconnect, BootNotification, heartbeat, remote command, and session flow.
7. Rotate the canary certificate using an overlap window of no more than 24
   hours, then revoke the old certificate and prove reconnect denial.
8. Expand in controlled batches while watching handshake outcome counters,
   connected-fleet count, reconnect duration, session callback errors, CPU,
   memory, and pod restarts.
9. Change `OCPP_MTLS_MODE` to `ENFORCE` only after 100% of the intended fleet is
   provisioned and the rollback rehearsal has passed.

## Production acceptance

- Existing Profile 2 fleet remains connected during the audit deployment.
- A valid certificate works only for its bound charge-point ID.
- Missing, expired, revoked, weak, malformed, and mismatched certificates fail.
- Rotation completes without loss of charger availability.
- Revocation is effective on the next TLS/WebSocket connection attempt.
- Full-fleet reconnect meets the existing capacity SLO with zero lost sessions.
- Rollback to audit mode is documented and tested without reverting schema.

## Rollback

Set `OCPP_MTLS_MODE=AUDIT` and sync the OCPP application. Do not remove the
certificate records or Liquibase migration. If the edge rule is implicated,
disable only its Profile 3 enforcement action while preserving TLS and Profile
2 authentication.

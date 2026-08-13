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

## Production canary evidence (2026-08-13 UTC)

- OCPP image 44 (`sha256:05d573...2c48`) is healthy on two replicas on
  separate nodes with zero restarts; Profile 2 remains `ENFORCE` and Profile 3
  fleet mode remains `AUDIT`.
- WebSocket connector image 32 (`sha256:40bd58...5e9`) mounts the runtime-only
  certificate Secret. Chargers without an enrolled certificate continue using
  Profile 2, so the 1,028-connection fleet was not interrupted.
- Cloudflare requests and validates client certificates for
  `api.electrahub.net`. Transform rule order is security-sensitive: remove any
  incoming `Client-Cert` first, then set it from
  `cf.tls_client_auth.cert_rfc9440` only when the certificate is verified and
  not revoked.
- Active WAF rule `OCPP Profile 3 canary - EH-IN-CHG-0001` blocks an
  unverified connection only on that charger's WebSocket path. The negative
  probe returned HTTP 403 at Cloudflare.
- `EH-IN-CHG-0001` uses an ECDSA P-256, one-year leaf certificate whose SHA-256
  fingerprint is bound to the same charge-point ID in the CSMS. A forced
  reconnect succeeded, the OCPP metric recorded
  `outcome="accepted",protocol="ocpp1.6" = 1`, and heartbeats continued for
  more than two minutes after reconnect.
- A first connector canary exposed an overly strict certificate-directory
  fallback. It was rolled back immediately, corrected in image 32, and
  redeployed. The corrected behavior fails closed for partial certificate
  material while preserving Profile 2 for chargers with no certificate files.

## Fleet enforcement gate

The platform implementation and production canary are complete. Fleet-wide
`OCPP_MTLS_MODE=ENFORCE` is intentionally gated until every intended physical
charger has its own certificate and the batch reconnect, rotation, revocation,
and rollback exercises above have passed. Enabling it before that point would
disconnect otherwise healthy Profile 2 chargers.

## Rollback

Set `OCPP_MTLS_MODE=AUDIT` and sync the OCPP application. Do not remove the
certificate records or Liquibase migration. If the edge rule is implicated,
disable only its Profile 3 enforcement action while preserving TLS and Profile
2 authentication.

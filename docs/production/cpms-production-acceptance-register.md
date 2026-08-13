# CPMS production acceptance register

Date: 2026-08-12

This register separates deployed engineering controls from external certification,
fleet, electrical, provider, and legal acceptance. A stage is not marked accepted
when its required real-world evidence is unavailable.

| Stage | Production state | Acceptance decision | Remaining gate |
|---|---|---|---|
| 1-7 CPMS and dashboard foundations | Deployed and previously validated | Accepted for current scope | Continue normal regression and operational monitoring |
| 8 OCPP high availability | Two ready replicas on separate nodes; Redis routing enabled; 1,028 authorised identities reconnected | Conditional | Independent 5,000-10,000 charger load and drain test with latency/error SLO evidence |
| 9 OCPP transport security | WSS and Profile 2 enforcement plus Profile 3 PKI lifecycle/readiness controls deployed; 1,028 active identities, 1 certificate-enrolled | Conditional for Profile 3 | Enroll the remaining 1,027 physical chargers, rehearse rotation/revocation and reconnect, then approve enforcement |
| 10 Site load management | Allocator, fallback, event, safety, and command controls deployed; every production actuation gate disabled | Blocked, fail-closed | Inventory remediation, approved capacity, authoritative site meter, firmware proof, and controlled canary |
| 11 Signed meter verification | Immutable OCMF verification, key lifecycle, jurisdiction policy, and billing holds deployed; verification enabled and enforcement disabled | Conditional | EDL/approved transparency integration, key ceremony, evidence fleet, shadow comparison, and legal policy |
| 12 OCPI expansion | 2.3.0 Credentials, Locations, CDRs, and Tariffs sender discovery deployed; optional modules explicitly gated off | Conditional | Partner credentials/certification and reservation actuator proof before Payments, Bookings, eMSP, or Hub roles |
| 13 Payment hardening | Enterprise/network route pinning, immutable balanced ledger, settlement import, and dispute/chargeback operations deployed; routing shadowed and incremental authorisation disabled | Conditional | Adyen/2C2P SCA, stored-credential, webhook, settlement, and incremental-authorisation certification |
| 14 EU compliance engineering | AFIR ad-hoc foundation, NAP-capable OCPI data, and accessibility/security fixes deployed | Conditional | Legal AFIR/NAP review, formal EAA testing, GDPR records/DPIA, metrology, and jurisdiction sign-off |
| 15 SLO, monitoring, and disaster recovery | Core service availability, 5xx, latency, and dashboard freshness alerts plus an incident/restore runbook are deployed | Conditional | Complete an isolated restore drill and record measured RPO/RTO with named owners |

## Release rule

Conditional or blocked capabilities remain shadowed, disabled, or fail-closed. They
must not be promoted to enforcement solely because the application code exists.
Each remaining gate requires named ownership, dated evidence, rollback criteria,
and explicit production approval.

## Validation evidence — 2026-08-12

- OCPP commit `2846d3c` and image `48@sha256:fce68deea5edff47b00a2542f043a822f10230f4b0c0004e1ce7df91bc73be8b`
  passed all 87 tests and rolled out two ready replicas. Argo reached
  Synced/Healthy. The authenticated fleet KPI reported 1,028 active identities,
  one enrolled identity, 1,027 missing certificates, and enforcement not ready.
- Charger management image `50@sha256:8026870b94f2105f1912fda3554019d4a427e42a04699e030fe239e79db00b9c`
  remained Synced/Healthy with actuation, automation, events, fallback, safety,
  exact-site allowlist, and authoritative meter sources all disabled/empty.
- Session service image `166@sha256:44a61e735f59d148b889d40eb6c9c3fc6c6058079bde12dbcc169b5b15ab8511`
  ran four ready replicas with signed-meter verification enabled and billing
  enforcement disabled.
- OCPI live discovery advertised 2.2.1 and only the implemented 2.3.0
  Credentials, Locations, CDRs, and Tariffs sender interfaces. Optional modules
  and unsupported roles were absent.
- Payment service image `40` and payment gateway image `24` were Synced/Healthy;
  routing enforcement and incremental authorization remained disabled.
- Driver portal and API gateway were Synced/Healthy on the current GitOps
  revision; the driver portal retained its pinned accessibility-reviewed digest.
- Prometheus revision `d982d4cd` was Synced/Healthy and its live rules API loaded
  `CpmsCoreServiceUnavailable`, `CpmsCoreServiceHttp5xxBurnRateHigh`, and
  `CpmsCoreServiceLatencyHigh`.

# CPMS production acceptance register

Date: 2026-08-12

This register separates deployed engineering controls from external certification,
fleet, electrical, provider, and legal acceptance. A stage is not marked accepted
when its required real-world evidence is unavailable.

| Stage | Production state | Acceptance decision | Remaining gate |
|---|---|---|---|
| 1-7 CPMS and dashboard foundations | Deployed and previously validated | Accepted for current scope | Continue normal regression and operational monitoring |
| 8 OCPP high availability | Two ready replicas on separate nodes; Redis routing enabled; 1,028 authorised identities reconnected | Conditional | Independent 5,000-10,000 charger load and drain test with latency/error SLO evidence |
| 9 OCPP transport security | WSS and Security Profile 2 enforcement; 1,028 active current credentials | Accepted for Profile 2 | Fleet PKI, certificate lifecycle, and reconnect proof before Profile 3 |
| 10 Site load management | Controls deployed but production automation disabled | Blocked, fail-closed | Inventory remediation, approved capacity, site meter, policy, and controlled canary |
| 11 Signed meter verification | Verification enabled in shadow mode; billing enforcement disabled | Conditional | OCMF/EDL transparency integration, key ceremony, evidence fleet, and legal policy |
| 12 OCPI expansion | 2.3.0 core sender discovery deployed; optional modules explicitly gated off | Conditional | Partner credentials and certification before Payments, Bookings, eMSP, or Hub roles |
| 13 Payment hardening | Immutable balanced ledger proven; pilot route evaluation in shadow mode | Conditional | Provider certification, settlement imports, incremental authorisation, and chargeback operations |
| 14 EU compliance engineering | Ad-hoc foundation and accessibility/security fixes deployed | Conditional | AFIR/NAP, EAA accessibility, GDPR/DPIA, metrology, and legal sign-off |

## Release rule

Conditional or blocked capabilities remain shadowed, disabled, or fail-closed. They
must not be promoted to enforcement solely because the application code exists.
Each remaining gate requires named ownership, dated evidence, rollback criteria,
and explicit production approval.

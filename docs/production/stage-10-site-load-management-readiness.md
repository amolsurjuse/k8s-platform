# Stage 10: site load-management production readiness

Date: 2026-08-12

## Decision

Production automation remains fail-closed. The allocator, charger prioritisation,
fallback policy, version-aware commands, and safety controls are present in the
deployed charger-management service, but no production site policy is enabled.

## Blocking evidence

- `smart_charging_site_policy` has zero production rows.
- The candidate `electrahub/LOC-BOS-001` inventory is not safe for a canary: two
  chargers are associated with 36 connector rows, including repeated tariff
  connectors and missing OCPP EVSE/connector ordinals.
- No authoritative site meter stream or approved electrical capacity is available.

## Exit gates

1. Reconcile charger, EVSE, and connector inventory and assign valid OCPP ordinals.
2. Obtain an electrically approved site import limit and per-charger safety limits.
3. Connect and validate an authoritative site meter feed.
4. Create an approved site policy with deterministic priority and fallback rules.
5. Run a monitored non-billing canary, prove fail-safe recovery, then progressively
   enable production automation.

No capacity value is inferred and no actuator is enabled until these gates pass.

# Stage 11: signed-meter shadow verification

Production enables signed-meter verification while billing enforcement remains
disabled. Incoming OCMF evidence is preserved append-only and evaluated against
the versioned key registry. Missing, expired, revoked, malformed, or invalid
evidence is recorded for comparison but cannot block settlement in this stage.

Promotion to jurisdiction enforcement requires all of the following:

- a legally reviewed tenant and jurisdiction policy;
- a complete charger public-key inventory and rotation/revocation procedure;
- validated begin/end and pagination evidence from the target fleet;
- an approved transparency-software provider and replay export contract;
- a shadow comparison with no unexplained billing delta;
- an explicit canary and rollback owner.

No policy is seeded by this release, and `SESSION_SIGNED_METER_ENFORCEMENT_ENABLED`
stays `false`.

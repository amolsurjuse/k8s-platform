# Stage 14: EU compliance engineering checkpoint

Date: 2026-08-12

## Delivered engineering controls

- AFIR ad-hoc charging and price-presentation foundations remain deployed.
- The driver portal now exposes a keyboard skip link and keyboard-reachable
  password-visibility controls.
- Angular was upgraded to 21.2.19; `npm audit --omit=dev` reports zero known
  vulnerabilities at build time.
- The production image is pinned by digest as
  `amolsurjuse/driver-portal-frontend:41@sha256:3f233ebd42490af2d0eab23f702bb524b6b6878ea7d6cd4be8d74aae8c2e110f`.

## Compliance boundary

These controls are engineering evidence, not a declaration of legal compliance.
Production acceptance still requires legal review of AFIR price/payment and NAP
obligations, accessibility conformance testing, GDPR records and DPIA decisions,
metrology policy, and jurisdiction-specific approval.

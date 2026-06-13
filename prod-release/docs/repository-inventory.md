# Repository Inventory

This file lists the repositories present in `/Users/amolsurjuse/development/projects` for migration and recovery planning.

Generated: 2026-06-13

## Notes

- `driver-portal-ios` currently has no `origin` remote configured.
- `ocpi-simulator`, `payment-service`, and `web-socket-connector` were clean but are currently on feature branches, not `develop`.
- The workspace root repository remote is `driver-portal-frontend`; it also contains nested project directories and subrepository pointers.

## Repositories

| Repository | Current Branch | Git URL | Latest Local Commit |
| --- | --- | --- | --- |
| `workspace-root` | `develop` | `git@github.com:amolsurjuse/driver-portal-frontend.git` | `8742344 chore: record k8s migration plan revision` |
| `admin-poratl-ui` | `develop` | `git@github.com:amolsurjuse/admin-portal-ui.git` | `ecbda32 chore: save local workspace changes` |
| `agentic-workflow-orchestrator` | `develop` | `git@github.com:amolsurjuse/agentic-workflow-orchestrator.git` | `03cb2e5 chore: save local workspace changes` |
| `api-gateway` | `develop` | `git@github.com:amolsurjuse/api-gateway.git` | `c9e8ab4 chore: save local workspace changes` |
| `auth-service` | `develop` | `git@github.com:amolsurjuse/auth-service.git` | `1c9c1f7 fix(cors): allow cloudflare dev origins` |
| `billing-service` | `develop` | `git@github.com:amolsurjuse/billing-service.git` | `18d22f4 chore: save local workspace changes` |
| `charger-management-service` | `develop` | `git@github.com:amolsurjuse/charger-management-service.git` | `1b4da0f chore: save local workspace changes` |
| `driver-portal-ios` | `develop` | `(no origin)` | `7972e6b chore: save local workspace changes` |
| `electra-hub-org-page` | `develop` | `git@github.com:amolsurjuse/electra-hub-org-page.git` | `2b4b931 chore: save local workspace changes` |
| `k8s-platform` | `develop` | `git@github.com:amolsurjuse/k8s-platform.git` | `a2d61c9 docs: add Windows migration plan` |
| `kubernetes` | `develop` | `git@github.com:amolsurjuse/kubernetes.git` | `8b47dfc chore: auto-commit workspace changes (kubernetes)` |
| `notification-service` | `develop` | `git@github.com:amolsurjuse/notification-service.git` | `2e9d5b5 Fix contact rate limiter injection` |
| `ocpi-service` | `develop` | `git@github.com:amolsurjuse/ocpi-service.git` | `9761703 chore: save local workspace changes` |
| `ocpi-simulator` | `codex/fix-unplug-ocpp201-env-sync-txid` | `git@github.com:amolsurjuse/ocpi-simulator.git` | `058bfb7 Fixed styling issues` |
| `ocpi-simulator-ui` | `develop` | `git@github.com:amolsurjuse/ocpi-simulator-ui.git` | `5e1e778 chore: auto-commit workspace changes (ocpi-simulator-ui)` |
| `ocpp-service` | `develop` | `git@github.com:amolsurjuse/ocpp-service.git` | `da7eba2 chore: save local workspace changes` |
| `payment-service` | `codex/add-payment-gateway-design-20260514` | `git@github.com:amolsurjuse/payment-service.git` | `c1fa7e8 docs: add payment gateway abstraction design` |
| `pricing-service` | `develop` | `git@github.com:amolsurjuse/pricing-service.git` | `1e0d875 chore: save local workspace changes` |
| `session-service` | `develop` | `git@github.com:amolsurjuse/session-service.git` | `8781241 fix(session): serve active charging state from elasticsearch` |
| `station-management-service` | `develop` | `git@github.com:amolsurjuse/station-management-service.git` | `b9569c6 chore: save local workspace changes` |
| `subscription-service` | `develop` | `git@github.com:amolsurjuse/subscription-service.git` | `19abbec feat: add admin subscription grants and quota metadata` |
| `user-service` | `develop` | `git@github.com:amolsurjuse/user-service.git` | `bf0c28b chore: save local workspace changes` |
| `web-socket-connector` | `codex/driver-portal-ev-journey` | `git@github.com:amolsurjuse/web-socket-connector.git` | `d17ad82 chore: auto-commit workspace changes (web-socket-connector)` |

## Clone Order For A New Machine

1. Clone `k8s-platform` first because it contains the platform migration docs, GitOps manifests, TeamCity bootstrap scripts, and restore runbooks.
2. Clone backend services: `auth-service`, `user-service`, `api-gateway`, `station-management-service`, `charger-management-service`, `pricing-service`, `billing-service`, `payment-service`, `subscription-service`, `session-service`, and `notification-service`.
3. Clone protocol and simulator services: `ocpp-service`, `ocpi-service`, `web-socket-connector`, `ocpp-simulator`, and `ocpi-simulator-ui`.
4. Clone UI/static applications: `admin-poratl-ui`, `electra-hub-org-page`, and the workspace root `driver-portal-frontend` repo.
5. Restore `driver-portal-ios` from the migration Git bundle unless an origin remote is added before migration.

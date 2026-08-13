# Portable TeamCity Pipeline Bootstrap

Use this folder to create or repair ElectraHub TeamCity pipelines from any machine, including Windows.

The Python script uses only the standard library. It does not require Bash, curl, or jq on the machine where you run it.

## Java build-agent baseline

Java build agents use `Dockerfile.agent-jdk25`, which preserves the existing
TeamCity/Docker tooling and runs the agent itself on Temurin 25 with Maven
3.9.12. Generated Maven build steps use the same digest-pinned Maven 3.9.12 and
Temurin 25 container while compiling application bytecode at each repository's
configured `--release` level.

## Prerequisites

- Python 3.9 or newer
- Network access to TeamCity
- A TeamCity access token
- Secure TeamCity parameters already configured at the parent project level:
  - `github.user`
  - `github.token`
  - `docker.username`
  - `docker.password`

The generated pipelines use Docker Buildx by default and publish multi-arch images for:

- `linux/amd64`
- `linux/arm64`

## Windows

```powershell
cd k8s-platform
$env:TEAMCITY_URL = "http://localhost:8111"
$env:TEAMCITY_TOKEN = "<token>"
.\scripts\teamcity\create_pipeline.ps1 -Config .\scripts\teamcity\pipeline.example.json
```

Create every ElectraHub service pipeline:

```powershell
.\scripts\teamcity\create_pipeline.ps1 -Config .\scripts\teamcity\electrahub-services.json
```

Create the first local core pipelines needed to unblock k3d dev:

```powershell
.\scripts\teamcity\create_pipeline.ps1 -Config .\scripts\teamcity\electrahub-local-core-services.json
```

Create the ElectraHub charging regression pipeline:

```powershell
.\scripts\teamcity\create_pipeline.ps1 -Config .\scripts\teamcity\electrahub-local-regression.json
```

Create the concurrent US/India/Netherlands wallet and saved-card E2E pipeline:

```powershell
.\scripts\teamcity\create_pipeline.ps1 -Config .\scripts\teamcity\electrahub-local-three-region-charging-e2e.json
```

Create the read-only payment-gateway sandbox validation pipeline:

```powershell
.\scripts\teamcity\create_pipeline.ps1 -Config .\scripts\teamcity\electrahub-local-payment-gateway-regression.json
```

## macOS/Linux

```bash
cd k8s-platform
TEAMCITY_URL="http://localhost:8111" \
TEAMCITY_TOKEN="<token>" \
python3 scripts/teamcity/create_pipeline.py --config scripts/teamcity/pipeline.example.json
```

Create every ElectraHub service pipeline:

```bash
python3 scripts/teamcity/create_pipeline.py --config scripts/teamcity/electrahub-services.json
```

Create the first local core pipelines needed to unblock k3d dev:

```bash
python3 scripts/teamcity/create_pipeline.py --config scripts/teamcity/electrahub-local-core-services.json
```

Create the ElectraHub charging regression pipeline:

```bash
python3 scripts/teamcity/create_pipeline.py --config scripts/teamcity/electrahub-local-regression.json
```

Create the concurrent US/India/Netherlands wallet and saved-card E2E pipeline:

```bash
python3 scripts/teamcity/create_pipeline.py --config scripts/teamcity/electrahub-local-three-region-charging-e2e.json
```

Create the read-only payment-gateway sandbox validation pipeline:

```bash
python3 scripts/teamcity/create_pipeline.py --config scripts/teamcity/electrahub-local-payment-gateway-regression.json
```

Create or repair one service from the catalog:

```bash
python3 scripts/teamcity/create_pipeline.py \
  --config scripts/teamcity/electrahub-services.json \
  --service session-service
```

## Dry Run

```bash
python3 scripts/teamcity/create_pipeline.py --config scripts/teamcity/pipeline.example.json --dry-run
```

## Required Config Fields

- `serviceName`
- `gitUrl`
- `dockerImage`
- `deployVersionFile`

If IDs are not provided, the script derives stable TeamCity IDs from `serviceName`.

The generated pipelines follow the existing ElectraHub service patterns:

1. Maven, Go, or Node build
2. Docker build
3. Docker push
4. Update the k8s-platform dev image tag
5. VCS trigger on the default branch

The five-user steady-load pipeline is intentionally different:

1. Checks out `k8s-platform`
2. Runs `scripts/jmeter/11-five-user-steady-charging-load.jmx` once through Dockerized JMeter (no ladder/burst loop)
3. Fails the build if any JMeter assertion fails
4. Publishes the raw `.jtl`, JMeter log, and HTML dashboard as TeamCity artifacts
5. Uses five users ramped over 60 seconds, no VCS trigger, and an explicit production target
6. Uses dynamic connector selection by default so smoke runs choose a currently available connector
7. Retries alternate live connectors when a selected connector becomes unavailable during the run
8. Requires a Password-typed `env.ELECTRAHUB_LOAD_CLEANUP_ADMIN_TOKEN` containing a `SYSTEM_ADMIN` JWT with at least 10 minutes remaining, pins the runner and image, and removes generated test accounts

The separate three-region pipeline invokes only `12-three-region-wallet-card-e2e.jmx`. Its three
thread groups run concurrently: one US/USD user, one India/INR user, and one Netherlands/EUR user,
each pinned to a distinct regional network. Each user completes wallet charging and saved-card
charging sequentially before cleanup. The run fails on missing regional inventory/routing, stale
`PREPARING`, absent target-session SSE state, missing meter/cost progress, stop failure, incomplete
payment settlement, or an invalid receipt.

The payment-gateway file creates two separate build configurations in the Payment Gateway Service
project. Both check Stripe, Razorpay, private 2C2P, Mollie, and Adyen independently, require each
sandbox connection, merchant, and route to be active, and call the read-only provider probe. They
never create a payment and never call the lifecycle-changing connection `validate` action.

- `JMeter Sandbox Provider Health (Dev)` targets `https://api-dev.electrahub.net` and runs after a
  successful `ElectraHub_PaymentGatewayService_Build`. It is an environment health check, not proof
  that the just-built image has completed its asynchronous GitOps rollout.
- `JMeter Sandbox Provider Validation (Prod)` targets `https://api.electrahub.net` and is manual, so
  production validation is queued only after the intended production promotion/rollout is complete.

Both accept only gateway connections whose provider environment is `SANDBOX`. A temporary external
provider outage therefore affects the separate health/validation result without rewriting the
service build result.

Before each configuration's first run, replace these Password-typed build parameter placeholders in
TeamCity with a dedicated system-administrator test account for that environment:

- `env.PAYMENT_GATEWAY_TEST_ADMIN_EMAIL`
- `env.PAYMENT_GATEWAY_TEST_ADMIN_PASSWORD`

The bootstrap creates missing placeholders as TeamCity Password parameters. Provider credentials
remain in the payment-gateway Kubernetes secret and are never passed to JMeter. The build publishes
a `.jtl`, JMeter log, HTML report, and a redacted per-provider JSON summary. Request headers,
sampler data, and response bodies are disabled in JMeter result persistence.

Because this build receives a privileged test login, its VCS root exposes only the fixed
`develop` branch, the runner is pinned to `teamcity-minimal-agent`, its JMeter image is pinned by
SHA-256 digest, and the generated shell step embeds the reviewed plan/image/origin values instead
of accepting custom-build overrides. The Groovy workflow independently accepts only the exact dev
and production ElectraHub API origins, refuses Host overrides and redirects, and revokes its device
session.

For a local/dev charging run only, use a copied non-production configuration with:

- `regression.base.url=http://host.docker.internal:8081`
- `regression.host.header=api.dev.electrahub.net`

For a full manual 100-user run, use `03-full-e2e-charging-100-users.jmx` in a separate reviewed build
configuration. Do not override the five-user steady plan into a burst run. Typical 100-user values are:

- `regression.users=100`
- `regression.ramp.seconds=300`
- `regression.hold.seconds=900`
- `regression.sse.seconds=120`
- `regression.dynamic.connector.selection=false`
- `regression.connector.start.attempts=20`

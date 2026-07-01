# ElectraHub JMeter Suites

JMeter suites for driver-facing ElectraHub load and regression flows.

Default target is `dev`:

```text
https://api.dev.electrahub.net
```

Use prod only when explicitly validating production:

```powershell
-Jbase_url=https://api.electrahub.net
```

## Suites

| Suite | Purpose |
| --- | --- |
| `01-user-payment-setup.jmx` | Creates test users, adds a card, tops up wallet, validates payment state, and writes generated users to CSV. |
| `02-charging-session-load.jmx` | Uses an existing user CSV and connector CSV to login, discover chargers, start sessions, monitor SSE, stop, and validate receipt. |
| `03-full-e2e-charging-100-users.jmx` | Full flow in one run: create users, setup payment, discover stations, start charging, monitor SSE, dwell, stop, receipt. |
| `04-sparky-ai-chat-regression.jmx` | Validates Sparky AI message creation, SSE completion, prompt routing, and live backend diagnostics. |

## Recommended Safe Dev Run

Start small before 100 users:

```powershell
jmeter -n -t scripts/jmeter/03-full-e2e-charging-100-users.jmx `
  -l outputs/jmeter/full-smoke.jtl `
  -e -o outputs/jmeter/full-smoke-report `
  -Jusers=5 `
  -Jramp_seconds=30 `
  -Jhold_seconds=120 `
  -Jsse_seconds=60
```

Full 100-user dev run:

```powershell
jmeter -n -t scripts/jmeter/03-full-e2e-charging-100-users.jmx `
  -l outputs/jmeter/full-100.jtl `
  -e -o outputs/jmeter/full-100-report `
  -Jusers=100 `
  -Jramp_seconds=300 `
  -Jhold_seconds=900 `
  -Jsse_seconds=900
```

## Sparky AI Chat Regression

Validates all current Sparky prompt families:

- charging start unavailable / `503`
- connector already active
- session stuck in `PREPARING`
- charger online/offline heartbeat
- general charging help

It also verifies that responses include live backend facts such as wallet state, active sessions, charger/connector availability, OCPP connection status, and heartbeat age.

```powershell
jmeter -n -t scripts/jmeter/04-sparky-ai-chat-regression.jmx `
  -l outputs/jmeter/sparky-ai/results.jtl `
  -j outputs/jmeter/sparky-ai/jmeter.log `
  -Jbase_url=https://api.dev.electrahub.net `
  -Jlogin_email=sysadmin.dev@electrahub.com `
  -Jlogin_password=Admin@12345
```

Useful overrides:

```text
-Jsparky_charger_id=EH-SFO-CHG-001
-Jsparky_connector_id=CON-SFO-001
-Jsparky_location_id=US*EHB*LOC*SFO001
-Jsparky_session_id=<optional-active-session-id>
-Jsparky_require_charger_status=true
```

TeamCity uses the ElectraHub-owned Java 17 image below. Keep this image on Java 17+ because the legacy
`justb4/jmeter:latest` image currently runs Java 8 and can fail Cloudflare TLS with `handshake_failure`.

```powershell
docker build -t amolsurjuse/electrahub-jmeter:5.6.3-java17 -f scripts/jmeter/docker/Dockerfile scripts/jmeter/docker
docker push amolsurjuse/electrahub-jmeter:5.6.3-java17
```

For local validation with the legacy image, port-forward the gateway and use HTTP:

```powershell
wsl -d Ubuntu-24.04 -- kubectl --context k3d-electrahub-prod -n prod port-forward --address 0.0.0.0 svc/api-gateway 19090:8090

docker run --rm -v C:\development\project\k8s-platform:/work -w /work justb4/jmeter:latest `
  -n -t scripts/jmeter/04-sparky-ai-chat-regression.jmx `
  -l outputs/jmeter/sparky-ai-prod-portforward/results.jtl `
  -j outputs/jmeter/sparky-ai-prod-portforward/jmeter.log `
  -Jbase_url=http://host.docker.internal:19090 `
  -Jsparky_require_charger_status=true
```

## Production Run Guardrail

Production will create real accounts and real wallet/session records. Use a unique `run_id` and throttle ramp-up:

```powershell
jmeter -n -t scripts/jmeter/03-full-e2e-charging-100-users.jmx `
  -l outputs/jmeter/prod-full-100.jtl `
  -e -o outputs/jmeter/prod-full-100-report `
  -Jbase_url=https://api.electrahub.net `
  -Jrun_id=prod-20260621-001 `
  -Jusers=100 `
  -Jramp_seconds=600 `
  -Jhold_seconds=900 `
  -Jsse_seconds=900
```

## Properties

| Property | Default | Description |
| --- | --- | --- |
| `base_url` | `https://api.dev.electrahub.net` | Gateway base URL. |
| `users` | `100` | Number of JMeter users/threads. |
| `ramp_seconds` | `300` | Ramp-up period. |
| `hold_seconds` | `900` | Total session dwell time before stop. |
| `sse_seconds` | `900` | How long each user keeps SSE open after start. |
| `run_id` | timestamp | Unique test run id used in generated emails. |
| `test_password` | `LoadTest@12345` | Password for generated users. |
| `wallet_topup_amount` | `120.00` | Wallet top-up amount per user. |
| `users_output` | `scripts/jmeter/data/generated-users.csv` | Output CSV from setup suite. |

## SSE Validation

JMeter core does not provide a first-class SSE sampler. These suites use a JSR223 Groovy sampler that opens:

```text
GET /session/api/v1/sessions/active/stream
```

It validates:

- HTTP 200 stream open.
- At least one `SNAPSHOT` or `SESSION_UPDATED` event for the active session.
- Session update events are not replaced by heartbeat-only traffic.
- The stream can remain open for the configured `sse_seconds`.

## Connector Data

`data/connectors-100.csv` assigns one connector per thread. It is intentionally deterministic and uses OCPP charger identity plus separate OCPI location id:

```csv
chargerId,locationId,connectorId,connectorNumber,connectorType
EH-US-CHG-0001,US*EHB*LOC*USA001,CON-US-0001,1,CCS-2
```

If simulator inventory changes, update this CSV instead of editing JMX files.


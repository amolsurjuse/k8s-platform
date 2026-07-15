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
| `05-card-present-charging-flow.jmx` | Validates simulated terminal card tap, dummy payment authorization, OCPP transaction start, dwell, and stop. |
| `06-idle-fee-charging-flow.jmx` | Validates idle-fee pricing discovery, active-session idle fields, remote-stop unplug requirement, physical unplug completion, and receipt. |
| `07-subscription-discount-charging-flow.jmx` | Validates the default new-user 20% charging discount, quota-aware active-session pricing, and discounted receipt fields. |
| `08-charging-feature-regression-suite.jmx` | Existing full charging flow plus focused regression cases for idle fee, idle-cap wallet reservation, subscription discount, low-balance auto-stop, low-balance wallet decisions, and auto top-up recovery. |

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

## Card-Present Charging Regression

This suite starts from the simulator because a card-present tap is charger/terminal initiated, not driver-app initiated.

```powershell
jmeter -n -t scripts/jmeter/05-card-present-charging-flow.jmx `
  -l outputs/jmeter/card-present/results.jtl `
  -j outputs/jmeter/card-present/jmeter.log `
  -Jsimulator_url=https://ocpp-simulator-dev.electrahub.net `
  -Jcharger_id=EH-SFO-CHG-001 `
  -Jconnector_number=1 `
  -Jusers=1 `
  -Jhold_seconds=15
```

## Idle-Fee Charging Regression

This suite validates the driver-app idle-fee contract end to end:

- charger GraphQL returns an enabled idle fee and positive idle-fee rate
- session start snapshots the idle-fee policy
- simulator status `SuspendedEV` starts the idle period
- remote stop keeps the session active and marks `unplugRequiredToStop=true`
- simulator status `Available` represents physical unplug and completes the session
- receipt/history/dashboard APIs are available after completion

```powershell
jmeter -n -t scripts/jmeter/06-idle-fee-charging-flow.jmx `
  -l outputs/jmeter/idle-fee/results.jtl `
  -j outputs/jmeter/idle-fee/jmeter.log `
  -Jbase_url=https://api.dev.electrahub.net `
  -Jsimulator_url=https://ocpp-simulator-dev.electrahub.net `
  -Jcharger_country_code=US `
  -Jdynamic_connector_selection=true `
  -Jusers=1 `
  -Jramp_seconds=1 `
  -Jhold_seconds=30 `
  -Jsse_seconds=15
```

## Subscription Discount Regression

This suite validates the new-user promotion contract:

- fresh driver account receives the default `NEW_USER_20_OFF_500KWH_1Y` allocation lazily on first subscription preview
- active session exposes `regularCost`, `discountedCost`, `subscriptionDiscountAmount`, plan details, and quota fields
- cost is discounted in real time while quota remains
- receipt carries the same discount summary after stop

```powershell
jmeter -n -t scripts/jmeter/07-subscription-discount-charging-flow.jmx `
  -l outputs/jmeter/subscription-discount/results.jtl `
  -j outputs/jmeter/subscription-discount/jmeter.log `
  -Jbase_url=https://api.dev.electrahub.net `
  -Jcharger_country_code=US `
  -Jdynamic_connector_selection=true `
  -Jusers=1 `
  -Jramp_seconds=1 `
  -Jhold_seconds=15 `
  -Jsse_seconds=15
```

## Charging Feature Regression Suite

This is the default plan for `ElectraHub_Regression_JMeterChargingFlow`. It runs the normal driver charging flow and the focused feature checks needed for recent charging changes:

- idle-fee remote stop remains active until unplug and then generates receipt
- wallet start is rejected when the balance is below the idle-fee cap plus the base reserve
- subscription discount appears in active-session pricing and receipt
- high meter value triggers backend low-balance remote stop
- wallet balance check returns `LOW_BALANCE` when auto top-up is disabled
- wallet balance check applies auto top-up and returns `OK` when a valid card is configured

```powershell
jmeter -n -t scripts/jmeter/08-charging-feature-regression-suite.jmx `
  -l outputs/jmeter/charging-feature-regression/results.jtl `
  -j outputs/jmeter/charging-feature-regression/jmeter.log `
  -Jbase_url=https://api.dev.electrahub.net `
  -Jsimulator_url=https://ocpp-simulator-dev.electrahub.net `
  -Jcharger_country_code=US `
  -Jdynamic_connector_selection=true `
  -Jusers=5 `
  -Jramp_seconds=30 `
  -Jhold_seconds=120 `
  -Jsse_seconds=60
```

Useful feature knobs:

```text
-Jfeature_idle_users=1
-Jfeature_idle_wallet_reserve_users=1
-Jfeature_subscription_users=1
-Jfeature_low_balance_auto_stop_users=1
-Jfeature_low_balance_check_users=1
-Jfeature_auto_topup_users=1
-Jlow_balance_threshold=10.00
-Jlow_balance_meter_wh=99999999
-Jauto_topup_threshold=100.00
-Jauto_topup_amount=50.00
-Jidle_fee_wallet_insufficient_balance=54.99
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

## Systematic Load Ladder

Use the TeamCity `ElectraHub_Regression_JMeterLoadLadder` build to find the current setup's practical breaking point.
The ladder runs the same end-to-end charging journey at progressively higher concurrency and stops when a stage exceeds
the configured error threshold.

Default prod ladder:

```text
5 users,   60s ramp,  60s hold, 30s SSE
10 users, 120s ramp,  90s hold, 45s SSE
20 users, 180s ramp, 120s hold, 60s SSE
35 users, 240s ramp, 120s hold, 60s SSE
50 users, 300s ramp, 120s hold, 60s SSE
75 users, 450s ramp, 180s hold, 90s SSE
100 users, 600s ramp, 180s hold, 90s SSE
```

Stop condition:

```text
jmeterLoadMaxErrorPercent = 5
```

Artifacts:

```text
jmeter-load/load-summary.csv
jmeter-load/stage-<n>-u<users>/results.jtl
jmeter-load/stage-<n>-u<users>/jmeter.log
jmeter-load/stage-<n>-u<users>/report/index.html
```

Interpretation:

- The first `BREAKPOINT` stage is the current environment's approximate limit.
- Use the previous `PASS` stage as the safe operating point until the bottleneck is fixed.
- Check `responseMessage` in the failing stage JTL for the bottleneck step: login, payment, charger GraphQL, start, SSE, stop, receipt, or dashboard.
- If failures are mostly `409 Connector is not available`, the bottleneck is charger inventory/simulator capacity, not API throughput.
- If failures are `SocketTimeoutException` on start/stop, inspect session-service to OCPP command routing and simulator responsiveness.
- If failures are dashboard/receipt after successful stops, inspect billing CDR generation and analytics indexing.

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

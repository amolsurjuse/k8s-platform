# Portable TeamCity Pipeline Bootstrap

Use this folder to create or repair ElectraHub TeamCity pipelines from any machine, including Windows.

The Python script uses only the standard library. It does not require Bash, curl, or jq on the machine where you run it.

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

The regression pipeline is intentionally different:

1. Checks out `k8s-platform`
2. Runs `scripts/jmeter/03-full-e2e-charging-100-users.jmx` through Dockerized JMeter
3. Fails the build if any JMeter assertion fails
4. Publishes the raw `.jtl`, JMeter log, and HTML dashboard as TeamCity artifacts
5. Uses safe smoke defaults of 5 users, no VCS trigger, and `https://api.dev.electrahub.net`
6. Uses dynamic connector selection by default so smoke runs choose a currently available connector

If public dev Cloudflare is unhealthy, queue the build with:

- `regression.base.url=http://host.docker.internal:8081`
- `regression.host.header=api.dev.electrahub.net`

For a full manual 100-user run, edit the build parameters before queueing:

- `regression.users=100`
- `regression.ramp.seconds=300`
- `regression.hold.seconds=900`
- `regression.sse.seconds=120`
- `regression.dynamic.connector.selection=false`

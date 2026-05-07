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
  - Docker registry credentials expected by your TeamCity Docker runner

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

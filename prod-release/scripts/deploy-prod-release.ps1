[CmdletBinding()]
param(
  [string]$EnvFile = (Join-Path $PSScriptRoot "..\env\prod-laptop.env.ps1")
)

$ErrorActionPreference = "Stop"

function Get-Setting {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$Default
  )

  $value = [Environment]::GetEnvironmentVariable($Name, "Process")
  if ([string]::IsNullOrWhiteSpace($value)) {
    return $Default
  }
  return $value
}

function Require-Command {
  param([Parameter(Mandatory = $true)][string]$Name)

  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Missing required command: $Name. Install it and make sure it is available on PATH."
  }
}

function Invoke-Checked {
  param(
    [Parameter(Mandatory = $true)][string]$FilePath,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments
  )

  & $FilePath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
  }
}

function Apply-Yaml {
  param([Parameter(Mandatory = $true)][string]$Yaml)

  $tempFile = Join-Path ([System.IO.Path]::GetTempPath()) ("electrahub-" + [System.Guid]::NewGuid().ToString("N") + ".yaml")
  try {
    Set-Content -Path $tempFile -Value $Yaml -Encoding utf8
    Invoke-Checked "kubectl" "apply" "-f" $tempFile
  } finally {
    Remove-Item -Path $tempFile -Force -ErrorAction SilentlyContinue
  }
}

$resolvedEnvFile = Resolve-Path -Path $EnvFile -ErrorAction SilentlyContinue
if (-not $resolvedEnvFile) {
  throw "Environment file not found: $EnvFile. Copy env/prod-laptop.env.ps1.example to env/prod-laptop.env.ps1 first."
}

. $resolvedEnvFile.Path

$namespace = Get-Setting "ELECTRA_NAMESPACE" "prod"
$argoNamespace = Get-Setting "ELECTRA_ARGO_NAMESPACE" "argocd"
$argoProject = Get-Setting "ELECTRA_ARGO_PROJECT" "electrahub-prod"
$gitRepoUrl = Get-Setting "ELECTRA_GIT_REPO_URL" "https://github.com/amolsurjuse/k8s-platform.git"
$gitTargetRevision = Get-Setting "ELECTRA_GIT_TARGET_REVISION" "develop"
$gitPath = Get-Setting "ELECTRA_GIT_PATH" "charts/common"

Require-Command "kubectl"

$services = @(
  "api-gateway",
  "auth-service",
  "billing-service",
  "charger-management-service",
  "payment-service",
  "pricing-service",
  "session-service",
  "station-management-service",
  "subscription-service",
  "user-service",
  "web-socket-connector",
  "ocpi-service",
  "ocpp-service",
  "ocpp-simulator",
  "ocpp-simulator-ui",
  "admin-portal-ui",
  "driver-portal-ui",
  "electra-hub-org-page"
)

Apply-Yaml @"
apiVersion: v1
kind: Namespace
metadata:
  name: $namespace
"@

Apply-Yaml @"
apiVersion: v1
kind: Namespace
metadata:
  name: $argoNamespace
"@

Apply-Yaml @"
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata:
  name: $argoProject
  namespace: $argoNamespace
spec:
  sourceRepos:
    - $gitRepoUrl
  destinations:
    - namespace: $namespace
      server: https://kubernetes.default.svc
  clusterResourceWhitelist:
    - group: '*'
      kind: '*'
"@

function New-InfraApplication {
  param(
    [Parameter(Mandatory = $true)][string]$ChartPath,
    [Parameter(Mandatory = $true)][string]$ValueFile,
    [Parameter(Mandatory = $true)][string]$ReleaseName
  )

  Apply-Yaml @"
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: $ReleaseName
  namespace: $argoNamespace
spec:
  project: $argoProject
  destination:
    server: https://kubernetes.default.svc
    namespace: $namespace
  source:
    repoURL: $gitRepoUrl
    targetRevision: $gitTargetRevision
    path: $ChartPath
    helm:
      releaseName: $ReleaseName
      valueFiles:
        - $ValueFile
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
"@
}

function New-ServiceApplication {
  param([Parameter(Mandatory = $true)][string]$Name)

  $releaseName = "$Name-prod"
  Apply-Yaml @"
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: $releaseName
  namespace: $argoNamespace
spec:
  project: $argoProject
  destination:
    server: https://kubernetes.default.svc
    namespace: $namespace
  source:
    repoURL: $gitRepoUrl
    targetRevision: $gitTargetRevision
    path: $gitPath
    helm:
      releaseName: $releaseName
      valueFiles:
        - $Name/values.yaml
        - ../config/services/$Name/us/base.yaml
        - ../config/services/$Name/us/values/dev-values.yaml
        - ../config/services/$Name/us/version/dev-version.yaml
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
"@
}

New-InfraApplication `
  -ChartPath "infrastructure/postgresql" `
  -ValueFile "../../platform-config/infrastructure/postgresql/us/values/prod.yaml" `
  -ReleaseName "postgresql-prod"

New-InfraApplication `
  -ChartPath "infrastructure/elasticsearch" `
  -ValueFile "../../platform-config/infrastructure/elasticsearch/us/values/prod.yaml" `
  -ReleaseName "elasticsearch-prod"

foreach ($service in $services) {
  New-ServiceApplication -Name $service
}

Write-Host "Prod Argo CD applications submitted."
Write-Host "Check with: kubectl -n $argoNamespace get applications"

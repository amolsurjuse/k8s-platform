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

$clusterRuntime = Get-Setting "ELECTRA_CLUSTER_RUNTIME" "k3d"
$clusterName = Get-Setting "ELECTRA_CLUSTER_NAME" "electrahub-prod"
$apiServerPort = Get-Setting "ELECTRA_API_SERVER_PORT" "6445"
$namespace = Get-Setting "ELECTRA_NAMESPACE" "prod"
$argoNamespace = Get-Setting "ELECTRA_ARGO_NAMESPACE" "argocd"

Require-Command "docker"
Require-Command "kubectl"
Require-Command "helm"

if ($clusterRuntime -ne "k3d") {
  throw "Only ELECTRA_CLUSTER_RUNTIME=k3d is currently scripted."
}

Require-Command "k3d"

$clusterExists = $false
& k3d cluster list $clusterName *> $null
if ($LASTEXITCODE -eq 0) {
  $clusterExists = $true
}

if (-not $clusterExists) {
  Invoke-Checked "k3d" "cluster" "create" $clusterName `
    "--api-port" $apiServerPort `
    "--agents" "1" `
    "--port" "80:80@loadbalancer" `
    "--port" "443:443@loadbalancer" `
    "--wait"
}

Invoke-Checked "kubectl" "config" "use-context" "k3d-$clusterName"

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

Invoke-Checked "helm" "repo" "add" "argo" "https://argoproj.github.io/argo-helm" "--force-update"
Invoke-Checked "helm" "repo" "update" "argo"

Invoke-Checked "helm" "upgrade" "--install" "argocd" "argo/argo-cd" `
  "--namespace" $argoNamespace `
  "--set" "server.service.type=ClusterIP" `
  "--wait"

Write-Host "Prod-like cluster is ready."
Write-Host "Context: k3d-$clusterName"
Write-Host "Namespace: $namespace"
Write-Host "Argo CD namespace: $argoNamespace"

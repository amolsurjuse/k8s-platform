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

$resolvedEnvFile = Resolve-Path -Path $EnvFile -ErrorAction SilentlyContinue
if (-not $resolvedEnvFile) {
  throw "Environment file not found: $EnvFile. Copy env/prod-laptop.env.ps1.example to env/prod-laptop.env.ps1 first."
}

. $resolvedEnvFile.Path

$kubeContext = Get-Setting "ELECTRA_KUBECONTEXT" "k3d-electrahub-prod"
$argoServer = Get-Setting "ELECTRA_ARGO_SERVER" "localhost:8080"

Require-Command "argocd"
Require-Command "kubectl"

Write-Host "Registering Kubernetes context '$kubeContext' with Argo CD '$argoServer'."
Write-Host "Make sure you are already logged in: argocd login $argoServer"

Invoke-Checked "argocd" "cluster" "add" $kubeContext "--yes"

Write-Host "Cluster registered with Argo CD."

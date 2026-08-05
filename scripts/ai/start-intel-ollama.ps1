[CmdletBinding()]
param(
    [string]$Model = "electrahub-sparky:4b",
    [string]$KeepAlive = "4h",
    [int]$StartupTimeoutSeconds = 30
)

$ErrorActionPreference = "Stop"
$ollama = Get-Command ollama -ErrorAction Stop

# Ollama treats Intel Arc integrated graphics as an iGPU. Both flags are required
# for the experimental Vulkan backend to retain and use the Arc device.
[Environment]::SetEnvironmentVariable("OLLAMA_VULKAN", "1", "User")
[Environment]::SetEnvironmentVariable("OLLAMA_IGPU_ENABLE", "1", "User")
$env:OLLAMA_VULKAN = "1"
$env:OLLAMA_IGPU_ENABLE = "1"

$healthUrl = "http://127.0.0.1:11434/api/version"
try {
    Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2 | Out-Null
} catch {
    $logDirectory = Join-Path $env:LOCALAPPDATA "ElectraHub\Ollama"
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    Start-Process -FilePath $ollama.Source -ArgumentList "serve" -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDirectory "ollama.out.log") `
        -RedirectStandardError (Join-Path $logDirectory "ollama.err.log")
}

$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
do {
    try {
        Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2 | Out-Null
        break
    } catch {
        if ((Get-Date) -ge $deadline) {
            throw "Ollama did not become ready at $healthUrl within $StartupTimeoutSeconds seconds."
        }
        Start-Sleep -Milliseconds 500
    }
} while ($true)

$body = @{
    model = $Model
    prompt = ""
    stream = $false
    keep_alive = $KeepAlive
    options = @{ num_predict = 1 }
} | ConvertTo-Json -Depth 4

Invoke-RestMethod -Uri "http://127.0.0.1:11434/api/generate" -Method Post `
    -ContentType "application/json" -Body $body -TimeoutSec 60 | Out-Null

Write-Host "Intel Arc Ollama is ready; model '$Model' is warm for $KeepAlive."
& $ollama.Source ps

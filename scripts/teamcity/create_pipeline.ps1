param(
    [Parameter(Mandatory = $true)]
    [string]$Config,

    [string]$TeamCityUrl = $env:TEAMCITY_URL,
    [string]$TeamCityToken = $env:TEAMCITY_TOKEN,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

if (-not $TeamCityUrl) {
    $enteredUrl = Read-Host "TeamCity URL [http://localhost:8111]"
    if ($enteredUrl) {
        $TeamCityUrl = $enteredUrl
    } else {
        $TeamCityUrl = "http://localhost:8111"
    }
}

if (-not $TeamCityToken) {
    $secureToken = Read-Host "TeamCity access token" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
    try {
        $TeamCityToken = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$pythonScript = Join-Path $scriptDir "create_pipeline.py"

function Test-PythonCommand {
    param([string]$Command)

    $candidate = Get-Command $Command -ErrorAction SilentlyContinue
    if (-not $candidate) {
        return $null
    }

    try {
        & $candidate.Source --version *> $null
        if ($LASTEXITCODE -eq 0) {
            return $candidate
        }
    } catch {
        return $null
    }

    return $null
}

function Convert-ToWslPath {
    param([string]$Path)

    $resolved = Resolve-Path -LiteralPath $Path
    $full = $resolved.ProviderPath
    if ($full -match "^([A-Za-z]):\\(.*)$") {
        $drive = $Matches[1].ToLowerInvariant()
        $rest = $Matches[2] -replace "\\", "/"
        return "/mnt/$drive/$rest"
    }

    throw "Cannot convert path to WSL path: $Path"
}

$argsList = @($pythonScript, "--config", $Config)
if ($TeamCityUrl) {
    $argsList += @("--teamcity-url", $TeamCityUrl)
}
if ($TeamCityToken) {
    $argsList += @("--token", $TeamCityToken)
}
if ($DryRun) {
    $argsList += "--dry-run"
}

$python = Test-PythonCommand "python"
if (-not $python) {
    $python = Test-PythonCommand "py"
}

if ($python) {
    & $python.Source @argsList
    exit $LASTEXITCODE
}

$wsl = Get-Command wsl -ErrorAction SilentlyContinue
if (-not $wsl) {
    throw "Python 3 is required. Install Python, enable the Windows py launcher, or use WSL with python3."
}

$wslPythonScript = Convert-ToWslPath $pythonScript
$wslConfig = Convert-ToWslPath $Config
$wslArgs = @($wslPythonScript, "--config", $wslConfig)
if ($TeamCityUrl) {
    $wslArgs += @("--teamcity-url", $TeamCityUrl)
}
if ($TeamCityToken) {
    $wslArgs += @("--token", $TeamCityToken)
}
if ($DryRun) {
    $wslArgs += "--dry-run"
}

Write-Host "Windows Python was not available; using WSL python3."
& $wsl.Source -d Ubuntu-24.04 -- python3 @wslArgs
exit $LASTEXITCODE

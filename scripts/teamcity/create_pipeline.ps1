param(
    [Parameter(Mandatory = $true)]
    [string]$Config,

    [string]$TeamCityUrl = $env:TEAMCITY_URL,
    [string]$TeamCityToken = $env:TEAMCITY_TOKEN,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$pythonScript = Join-Path $scriptDir "create_pipeline.py"

$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    $python = Get-Command py -ErrorAction SilentlyContinue
}
if (-not $python) {
    throw "Python 3 is required. Install Python or use the bash/python command from README.md."
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

& $python.Source @argsList

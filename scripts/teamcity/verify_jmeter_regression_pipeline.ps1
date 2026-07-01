param(
    [string]$Config = ".\scripts\teamcity\electrahub-local-regression.json",
    [string]$TeamCityUrl = $env:TEAMCITY_URL,
    [string]$TeamCityToken = $env:TEAMCITY_TOKEN
)

$ErrorActionPreference = "Stop"

if (-not $TeamCityUrl) {
    $TeamCityUrl = "http://localhost:8111"
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

if (-not $TeamCityToken) {
    throw "TeamCity token is required."
}

$repoRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")
$configPath = Resolve-Path -LiteralPath (Join-Path $repoRoot $Config)
$createScript = Join-Path $PSScriptRoot "create_pipeline.ps1"
$expectedImage = "amolsurjuse/electrahub-jmeter:5.6.3-java17"
$buildTypeId = "ElectraHub_Regression_JMeterChargingFlow"

Write-Host "Refreshing TeamCity regression pipeline..."
& $createScript -Config $configPath -TeamCityUrl $TeamCityUrl -TeamCityToken $TeamCityToken
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$headers = @{
    Authorization = "Bearer $TeamCityToken"
    Accept = "application/json"
}

$parameterUrl = "$($TeamCityUrl.TrimEnd('/'))/app/rest/buildTypes/id:$buildTypeId/parameters/jmeter.image"
$imageParameter = Invoke-RestMethod -Method Get -Uri $parameterUrl -Headers $headers
$actualImage = [string]$imageParameter.value

Write-Host "Live TeamCity jmeter.image=$actualImage"
if ($actualImage -ne $expectedImage) {
    throw "TeamCity still points at '$actualImage'. Expected '$expectedImage'."
}

$stepsUrl = "$($TeamCityUrl.TrimEnd('/'))/app/rest/buildTypes/id:$buildTypeId/steps"
$steps = Invoke-RestMethod -Method Get -Uri $stepsUrl -Headers $headers
$step = @($steps.step) | Where-Object { $_.name -eq "JMeter Charging Regression" } | Select-Object -First 1
if (-not $step) {
    throw "JMeter Charging Regression step was not found."
}

$stepUrl = "$($TeamCityUrl.TrimEnd('/'))/app/rest/buildTypes/id:$buildTypeId/steps/$($step.id)"
$stepDetails = Invoke-RestMethod -Method Get -Uri $stepUrl -Headers $headers
$scriptContent = @($stepDetails.properties.property | Where-Object { $_.name -eq "script.content" } | Select-Object -First 1).value

if ($scriptContent -notlike "*JMeter image=%jmeter.image%*" -or $scriptContent -notlike "*java `"%jmeter.image%`" -version*") {
    throw "TeamCity step is missing the Java/JMeter preflight diagnostics. Re-run create_pipeline.ps1."
}

Write-Host "TeamCity JMeter regression pipeline is updated and ready."

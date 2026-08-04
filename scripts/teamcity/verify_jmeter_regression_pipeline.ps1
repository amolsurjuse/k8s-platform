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
$configJson = Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
$createScript = Join-Path $PSScriptRoot "create_pipeline.ps1"
$expectedImage = [string]$configJson.jmeterImage
$buildTypeId = [string]$configJson.buildTypeId
if (-not $expectedImage) {
    $expectedImage = "amolsurjuse/electrahub-jmeter:5.6.3-java17"
}
if (-not $buildTypeId) {
    $buildTypeId = "ElectraHub_Regression_JMeterChargingFlow"
}

Write-Host "Refreshing TeamCity regression pipeline..."
& $createScript -Config $configPath -TeamCityUrl $TeamCityUrl -TeamCityToken $TeamCityToken
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$headers = @{
    Authorization = "Bearer $TeamCityToken"
    Accept = "application/json"
}

function Get-BuildParameter {
    param([string]$Name)
    $url = "$($TeamCityUrl.TrimEnd('/'))/app/rest/buildTypes/id:$buildTypeId/parameters/$([Uri]::EscapeDataString($Name))"
    $parameter = Invoke-RestMethod -Method Get -Uri $url -Headers $headers
    return [string]$parameter.value
}

function Assert-BuildParameter {
    param(
        [string]$Name,
        [string]$Expected
    )
    if ($null -eq $Expected -or $Expected -eq "") {
        return
    }
    $actual = Get-BuildParameter -Name $Name
    Write-Host "Live TeamCity $Name=$actual"
    if ($actual -ne $Expected) {
        throw "TeamCity parameter '$Name' is '$actual'. Expected '$Expected'."
    }
}

Assert-BuildParameter -Name "jmeter.image" -Expected $expectedImage
Assert-BuildParameter -Name "jmeter.plan" -Expected ([string]$configJson.jmeterPlan)
Assert-BuildParameter -Name "regression.base.url" -Expected ([string]$configJson.regressionBaseUrl)
Assert-BuildParameter -Name "regression.users" -Expected ([string]$configJson.regressionUsers)
Assert-BuildParameter -Name "regression.ramp.seconds" -Expected ([string]$configJson.regressionRampSeconds)
Assert-BuildParameter -Name "regression.hold.seconds" -Expected ([string]$configJson.regressionHoldSeconds)
Assert-BuildParameter -Name "regression.sse.seconds" -Expected ([string]$configJson.regressionSseSeconds)
Assert-BuildParameter -Name "regression.connector.start.attempts" -Expected ([string]$configJson.regressionConnectorStartAttempts)
Assert-BuildParameter -Name "regression.request.timeout.ms" -Expected ([string]$configJson.regressionRequestTimeoutMs)
Assert-BuildParameter -Name "regression.session.command.timeout.ms" -Expected ([string]$configJson.regressionSessionCommandTimeoutMs)
Assert-BuildParameter -Name "jmeter.load.stages" -Expected ([string]$configJson.jmeterLoadStages)
Assert-BuildParameter -Name "jmeter.load.max.error.percent" -Expected ([string]$configJson.jmeterLoadMaxErrorPercent)

$stepsUrl = "$($TeamCityUrl.TrimEnd('/'))/app/rest/buildTypes/id:$buildTypeId/steps"
$steps = Invoke-RestMethod -Method Get -Uri $stepsUrl -Headers $headers
$expectedStepName = if ([string]$configJson.jmeterStepName) {
    [string]$configJson.jmeterStepName
} elseif ([string]$configJson.jmeterLoadStages) {
    "JMeter Load Ladder"
} else {
    "JMeter Charging Regression"
}
$jmeterSteps = @($steps.step) | Where-Object { $_.name -like "JMeter*" }
if ($jmeterSteps.Count -ne 1) {
    throw "Expected exactly one JMeter step, but found $($jmeterSteps.Count)."
}
$step = @($steps.step) | Where-Object { $_.name -eq $expectedStepName } | Select-Object -First 1
if (-not $step) {
    throw "$expectedStepName step was not found."
}

$stepUrl = "$($TeamCityUrl.TrimEnd('/'))/app/rest/buildTypes/id:$buildTypeId/steps/$($step.id)"
$stepDetails = Invoke-RestMethod -Method Get -Uri $stepUrl -Headers $headers
$scriptContent = @($stepDetails.properties.property | Where-Object { $_.name -eq "script.content" } | Select-Object -First 1).value

if (($scriptContent -notlike "*JMeter image=%jmeter.image%*" -and $scriptContent -notlike "*JMeter image=$expectedImage*") -or
    ($scriptContent -notlike "*java `"%jmeter.image%`" -version*" -and $scriptContent -notlike "*java `"$expectedImage`" -version*")) {
    throw "TeamCity step is missing the Java/JMeter preflight diagnostics. Re-run create_pipeline.ps1."
}

if ($scriptContent -notlike "*jmeter.plan must resolve to exactly one checked-in JMX file*" -or
    $scriptContent -notlike '*-t "$PLAN"*') {
    throw "TeamCity step is missing the single-plan runtime guard. Re-run create_pipeline.ps1."
}

if ([string]$configJson.jmeterLoadStages -and $scriptContent -notlike "*Load ladder stopped at stage*") {
    throw "TeamCity load ladder step is missing breakpoint detection. Re-run create_pipeline.ps1."
}

Write-Host "TeamCity JMeter pipeline '$buildTypeId' is updated and ready."

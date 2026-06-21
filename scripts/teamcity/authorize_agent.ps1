param(
    [string]$TeamCityUrl = $env:TEAMCITY_URL,
    [string]$TeamCityToken = $env:TEAMCITY_TOKEN,
    [string]$AgentName = "teamcity-minimal-agent"
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

$baseUrl = $TeamCityUrl.TrimEnd("/")
$headers = @{
    Authorization = "Bearer $TeamCityToken"
    Accept = "application/json"
}

Write-Host "Reading TeamCity agents from $baseUrl ..."
$locator = [uri]::EscapeDataString("authorized:any,connected:any,enabled:any")
$agents = Invoke-RestMethod -Method Get -Uri "$baseUrl/app/rest/agents?locator=$locator" -Headers $headers

if (-not $agents.agent) {
    Write-Host "No agents were returned by the broad agent query. Trying direct lookup for id=1 ..."
    try {
        $directAgent = Invoke-RestMethod -Method Get -Uri "$baseUrl/app/rest/agents/id:1" -Headers $headers
        if ($directAgent) {
            $agents = [pscustomobject]@{ agent = @($directAgent) }
        }
    } catch {
        Write-Host "Direct lookup failed: $($_.Exception.Message)"
    }
}

if (-not $agents.agent) {
    Write-Host "No agents were returned by TeamCity. The server log still shows the agent as id=1 and unauthorized."
    Write-Host "Open $baseUrl/agents.html?tab=unauthorizedAgents, or rerun this script with an administrator token."
    exit 1
}

$agents.agent | ForEach-Object {
    Write-Host ("Agent: id={0} name={1} authorized={2} connected={3} enabled={4}" -f $_.id, $_.name, $_.authorized, $_.connected, $_.enabled)
}

$agent = $agents.agent | Where-Object { $_.name -eq $AgentName } | Select-Object -First 1
if (-not $agent) {
    throw "Agent '$AgentName' was not returned by TeamCity. Check the agent container logs and TeamCity URL."
}

if ($agent.authorized -eq $true) {
    Write-Host "Agent '$AgentName' is already authorized."
} else {
    Write-Host "Authorizing agent '$AgentName' ..."
    Invoke-RestMethod `
        -Method Put `
        -Uri "$baseUrl/app/rest/agents/id:$($agent.id)/authorized" `
        -Headers @{ Authorization = "Bearer $TeamCityToken"; Accept = "text/plain" } `
        -ContentType "text/plain" `
        -Body "true" | Out-Null
    Write-Host "Authorized agent '$AgentName'."
}

Write-Host "Agent page: $baseUrl/agents.html"

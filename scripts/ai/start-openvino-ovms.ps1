[CmdletBinding()]
param(
    [ValidateSet("GPU", "NPU", "CPU")]
    [string]$Device = "GPU",
    [string]$Model = "OpenVINO/Qwen3-8B-int4-ov",
    [string]$ModelRepository = "$env:LOCALAPPDATA\ElectraHub\OVMS\models",
    [int]$Port = 8000
)

$ErrorActionPreference = "Stop"
$ovms = Get-Command ovms.exe -ErrorAction Stop
New-Item -ItemType Directory -Path $ModelRepository -Force | Out-Null

$arguments = @(
    "--model_repository_path", $ModelRepository,
    "--source_model", $Model,
    "--model_name", $Model,
    "--task", "text_generation",
    "--target_device", $Device,
    "--rest_port", $Port.ToString()
)

Write-Host "Starting OpenVINO Model Server model '$Model' on Intel $Device at port $Port."
Write-Host "The first run downloads the OpenVINO model and can take several minutes."
& $ovms.Source @arguments

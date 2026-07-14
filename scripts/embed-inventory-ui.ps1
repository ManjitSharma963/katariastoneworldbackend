# Build React inventory UI and embed into Spring Boot static resources.
# Run from backend repo root before deploying the API.

param(
    [string]$InventoryRoot = "D:\katariawork\kataristoneworldinventory",
    [string]$BackendRoot = $PSScriptRoot + "\.."
)

$ErrorActionPreference = "Stop"
$staticDir = Join-Path $BackendRoot "src\main\resources\static\inventory"

Write-Host "Building inventory UI from $InventoryRoot"
Push-Location $InventoryRoot
$env:CI = "true"
npm run build
Pop-Location

Write-Host "Copying build output to $staticDir"
if (Test-Path $staticDir) {
    Remove-Item -Recurse -Force $staticDir
}
New-Item -ItemType Directory -Force -Path $staticDir | Out-Null
Copy-Item -Recurse -Force (Join-Path $InventoryRoot "build\*") $staticDir

Write-Host "Done. UI will be served at /inventory/ after backend deploy."
Write-Host "Production URL: https://api.katariastoneworld.com/inventory/"

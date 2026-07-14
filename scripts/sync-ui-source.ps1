# Copy inventory UI source into backend/ui/ for Docker builds on Railway.
param(
    [string]$InventoryRoot = "D:\katariawork\kataristoneworldinventory",
    [string]$BackendRoot = $PSScriptRoot + "\.."
)

$ErrorActionPreference = "Stop"
$uiDir = Join-Path $BackendRoot "ui"
$items = @("package.json", "package-lock.json", "public", "src", ".env.production")

if (Test-Path $uiDir) {
    Remove-Item -Recurse -Force $uiDir
}
New-Item -ItemType Directory -Force -Path $uiDir | Out-Null

foreach ($item in $items) {
    $src = Join-Path $InventoryRoot $item
    $dst = Join-Path $uiDir $item
    Copy-Item -Recurse -Force $src $dst
}

Write-Host "Synced UI source to $uiDir"

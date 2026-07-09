# Load deploy/railway.env and start API against Railway MySQL (public proxy).
$envFile = Join-Path $PSScriptRoot "..\deploy\railway.env"
if (-not (Test-Path $envFile)) {
  Write-Error "Missing $envFile — copy deploy/railway.env.example and fill in Railway credentials."
  exit 1
}

Get-Content $envFile | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
  $name, $value = $_ -split '=', 2
  if ($name) { Set-Item -Path "env:$name" -Value $value }
}

$env:SPRING_PROFILES_ACTIVE = "railway"
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://hayabusa.proxy.rlwy.net:47702/railway?useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:SPRING_DATASOURCE_USERNAME = "root"
$env:SPRING_DATASOURCE_PASSWORD = $env:MYSQLPASSWORD

Write-Host "Starting API with profile=railway → Railway MySQL (public proxy)" -ForegroundColor Cyan
Set-Location (Join-Path $PSScriptRoot "..")
mvn spring-boot:run

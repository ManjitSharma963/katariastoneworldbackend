#Requires -Version 5.1
<#
.SYNOPSIS
  Backs up the database, then applies ALL_MIGRATIONS_CONSOLIDATED.mysql.sql in one shot.

.DESCRIPTION
  Uses mysqldump then mysql CLI. Requires both on PATH (e.g. C:\Program Files\MySQL\MySQL Server 8.0\bin).

.PARAMETER Database
  Target schema name (e.g. katariastoneworld).

.PARAMETER User
  MySQL user (e.g. root).

.PARAMETER Host
  Server host. Default 127.0.0.1

.PARAMETER SqlFile
  Defaults to ALL_MIGRATIONS_CONSOLIDATED.mysql.sql next to this script.

.EXAMPLE
  cd katariastoneworldbackend\src\main\resources\db
  .\run-consolidated-migration.ps1 -Database katariastoneworld -User root
#>
param(
  [Parameter(Mandatory = $true)]
  [string] $Database,

  [Parameter(Mandatory = $true)]
  [string] $User,

  [string] $Host = '127.0.0.1',

  [string] $SqlFile = ''
)

$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
if (-not $SqlFile) {
  $SqlFile = Join-Path $here 'ALL_MIGRATIONS_CONSOLIDATED.mysql.sql'
}
if (-not (Test-Path -LiteralPath $SqlFile)) {
  throw "SQL file not found: $SqlFile"
}

$mysql = Get-Command mysql -ErrorAction SilentlyContinue
$mysqldump = Get-Command mysqldump -ErrorAction SilentlyContinue
if (-not $mysql -or -not $mysqldump) {
  throw "mysql and mysqldump must be on PATH. Add MySQL bin to PATH, e.g. C:\Program Files\MySQL\MySQL Server 8.0\bin"
}
$mysqlExe = $mysql.Source
$mysqldumpExe = $mysqldump.Source

$secure = Read-Host -AsSecureString "MySQL password for user '$User'"
$BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
  $plain = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
} finally {
  [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($BSTR)
}

$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$backup = Join-Path $here "backup_${Database}_${stamp}.sql"
Write-Host "Creating backup: $backup"
& $mysqldumpExe -h $Host -u $User -p$plain --single-transaction --routines --triggers $Database | Set-Content -Path $backup -Encoding UTF8
if ($LASTEXITCODE -ne 0) { throw "mysqldump failed with exit code $LASTEXITCODE" }
Write-Host "Backup OK."

Write-Host "Applying migrations from: $SqlFile"
$sql = Get-Content -LiteralPath $SqlFile -Raw -Encoding UTF8
$sql | & $mysqlExe -h $Host -u $User -p$plain --default-character-set=utf8mb4 $Database
if ($LASTEXITCODE -ne 0) { throw "mysql failed with exit code $LASTEXITCODE. Restore from backup if needed: mysql -u $User -p $Database < $backup" }

Write-Host "Done. If you saw no errors, migrations completed."
Write-Host "Backup saved at: $backup"

# Go-live verification for Kataria production cutover.
# Usage:
#   .\scripts\go-live-verify.ps1
#   .\scripts\go-live-verify.ps1 -ApiBase "https://www.katariastoneworld.com/api" -Email "admin@example.com" -Password "secret"

param(
    [string]$ApiBase = "https://www.katariastoneworld.com/api",
    [string]$WebBase = "https://www.katariastoneworld.com/inventory",
    [string]$AllowedOrigin = "https://www.katariastoneworld.com",
    [string]$Email = $env:KATARIA_TEST_EMAIL,
    [string]$Password = $env:KATARIA_TEST_PASSWORD
)

$ErrorActionPreference = "Continue"
$passed = 0
$failed = 0
$skipped = 0

function Write-Check {
    param([string]$Name, [string]$Status, [string]$Detail = "")
    $icon = switch ($Status) {
        "PASS" { "[PASS]"; $script:passed++ }
        "FAIL" { "[FAIL]"; $script:failed++ }
        "SKIP" { "[SKIP]"; $script:skipped++ }
        default { "[????]" }
    }
    Write-Host "$icon $Name" -ForegroundColor $(if ($Status -eq "PASS") { "Green" } elseif ($Status -eq "FAIL") { "Red" } else { "Yellow" })
    if ($Detail) { Write-Host "       $Detail" }
}

Write-Host ""
Write-Host "Kataria go-live verification" -ForegroundColor Cyan
Write-Host "API:  $ApiBase"
Write-Host "Web:  $WebBase"
Write-Host ""

# 1. Health (/actuator/health is on the app root, not under /api)
try {
    $root = $ApiBase -replace "/api/?$", ""
    $health = Invoke-RestMethod -Uri "$root/actuator/health" -Method Get -TimeoutSec 20
    if ($health.status -eq "UP") {
        Write-Check "Actuator health returns UP" "PASS" "status=$($health.status)"
    } else {
        Write-Check "Actuator health returns UP" "FAIL" "status=$($health.status)"
    }
} catch {
    Write-Check "Actuator health returns UP" "FAIL" $_.Exception.Message
}

# 2. Login
$token = $null
if (-not $Email -or -not $Password) {
    Write-Check "POST /api/auth/login" "SKIP" "Set -Email/-Password or KATARIA_TEST_EMAIL / KATARIA_TEST_PASSWORD"
} else {
    try {
        $loginBody = @{ email = $Email; password = $Password } | ConvertTo-Json
        $login = Invoke-RestMethod -Uri "$ApiBase/auth/login" -Method Post -Body $loginBody -ContentType "application/json" -TimeoutSec 20
        if ($login.token) {
            $token = $login.token
            Write-Check "POST /api/auth/login" "PASS" "JWT received"
        } else {
            Write-Check "POST /api/auth/login" "FAIL" "No token in response"
        }
    } catch {
        Write-Check "POST /api/auth/login" "FAIL" $_.Exception.Message
    }
}

# 3. JWT-protected endpoints
$authHeaders = @{}
if ($token) { $authHeaders["Authorization"] = "Bearer $token" }

foreach ($ep in @(
    @{ Name = "GET /api/inventory"; Url = "$ApiBase/inventory" },
    @{ Name = "GET /api/bills"; Url = "$ApiBase/bills" },
    @{ Name = "GET /api/expenses"; Url = "$ApiBase/expenses" }
)) {
    if (-not $token) {
        Write-Check "$($ep.Name) with JWT" "SKIP" "No token from login"
        continue
    }
    try {
        $null = Invoke-RestMethod -Uri $ep.Url -Method Get -Headers $authHeaders -TimeoutSec 20
        Write-Check "$($ep.Name) with JWT" "PASS"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        Write-Check "$($ep.Name) with JWT" "FAIL" "HTTP $code — $($_.Exception.Message)"
    }
}

# 4. Web UI loads
try {
    $web = Invoke-WebRequest -Uri $WebBase -Method Get -TimeoutSec 20 -UseBasicParsing
    if ($web.StatusCode -ge 200 -and $web.StatusCode -lt 400) {
        Write-Check "Web UI loads at /inventory" "PASS" "HTTP $($web.StatusCode)"
    } else {
        Write-Check "Web UI loads at /inventory" "FAIL" "HTTP $($web.StatusCode)"
    }
} catch {
    Write-Check "Web UI loads at /inventory" "FAIL" $_.Exception.Message
}

# 5. CORS — allowed origin should succeed preflight
try {
    $cors = Invoke-WebRequest -Uri "$ApiBase/inventory" -Method Options -Headers @{
        "Origin" = $AllowedOrigin
        "Access-Control-Request-Method" = "GET"
    } -TimeoutSec 20 -UseBasicParsing
    $acao = $cors.Headers["Access-Control-Allow-Origin"]
    if ($acao -eq $AllowedOrigin -or $acao -eq "*") {
        Write-Check "CORS allows $AllowedOrigin" "PASS" "Access-Control-Allow-Origin: $acao"
    } else {
        Write-Check "CORS allows $AllowedOrigin" "FAIL" "Got: $acao"
    }
} catch {
    Write-Check "CORS allows $AllowedOrigin" "FAIL" $_.Exception.Message
}

# 6. CORS — random origin should NOT be allowed (when prod locked down)
try {
    $badOrigin = "https://evil.example.com"
    $corsBad = Invoke-WebRequest -Uri "$ApiBase/inventory" -Method Options -Headers @{
        "Origin" = $badOrigin
        "Access-Control-Request-Method" = "GET"
    } -TimeoutSec 20 -UseBasicParsing
    $badAcao = $corsBad.Headers["Access-Control-Allow-Origin"]
    if ($badAcao -eq $badOrigin) {
        Write-Check "CORS blocks untrusted origins" "FAIL" "Allowed: $badAcao"
    } else {
        Write-Check "CORS blocks untrusted origins" "PASS" "Untrusted origin not echoed"
    }
} catch {
    Write-Check "CORS blocks untrusted origins" "PASS" "Request rejected or no ACAO for evil origin"
}

# Manual items (cannot automate from this script)
Write-Host ""
Write-Host "Manual checks (complete in Railway / DNS / app stores):" -ForegroundColor Cyan
Write-Host "  [ ] MySQL populated and Flyway migrations applied"
Write-Host "  [ ] Secrets removed from git (grep for passwords in repo history)"
Write-Host "  [ ] Old VPS DNS updated or retired"
Write-Host "  [ ] Mobile app EXPO_PUBLIC_API_URL=$ApiBase"
Write-Host "  [ ] Web build REACT_APP_API_URL=$ApiBase"
Write-Host ""

Write-Host "Results: $passed passed, $failed failed, $skipped skipped" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
if ($failed -gt 0) { exit 1 }

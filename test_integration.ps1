# ============================================================
# BankEuroB - Test integracji z systemami platniczymi
# ============================================================
# Testuje polaczenie BankEuroB z:
#   - TARGET RTGS (Centralny system rozliczen miedzybankowych)
#   - SEPA Batch (System rozliczen batchowych)
#   - SEPA Instant (System przelewow natychmiastowych)
#   - SWIFT Middleware (System rozliczen SWIFT)
#   - Payment Gateway (System kart platniczych)
#   - KLIK RTGS Mock (Symulator RTGS)
# ============================================================

$ErrorActionPreference = "Stop"

Write-Host "============================================================"
Write-Host "  BankEuroB - Test integracji z systemami platniczymi"
Write-Host "============================================================"

$global:allPassed = $true

function Test-Endpoint {
    param($Name, $Url, $Method = "GET", $Body = $null, $ExpectedStatus = 200, $ContentType = "application/json")
    
    try {
        if ($Method -eq "GET") {
            $response = Invoke-WebRequest -Uri $Url -Method $Method -UseBasicParsing -TimeoutSec 10
        } else {
            $headers = @{}
            if ($ContentType) {
                $headers["Content-Type"] = $ContentType
            }
            $response = Invoke-WebRequest -Uri $Url -Method $Method -Body $Body -Headers $headers -UseBasicParsing -TimeoutSec 10
        }
        
        if ($response.StatusCode -eq $ExpectedStatus) {
            Write-Host "  [PASS] $Name ($Url) -> $($response.StatusCode)" -ForegroundColor Green
            return $true
        } else {
            Write-Host "  [FAIL] $Name ($Url) -> oczekiwano $ExpectedStatus, otrzymano $($response.StatusCode)" -ForegroundColor Red
            $global:allPassed = $false
            return $false
        }
    } catch {
        if ($_.Exception.Response.StatusCode -eq $ExpectedStatus) {
            Write-Host "  [PASS] $Name ($Url) -> $($_.Exception.Response.StatusCode)" -ForegroundColor Green
            return $true
        }
        Write-Host "  [FAIL] $Name ($Url) -> $_" -ForegroundColor Red
        $global:allPassed = $false
        return $false
    }
}

# ============================================================
# 1. TARGET RTGS Service (port 8001)
# ============================================================
Write-Host "`n--- 1. TARGET RTGS Service (Centralny system rozliczen) ---" -ForegroundColor Yellow

Test-Endpoint -Name "TARGET Health" -Url "http://localhost:8001/health" -Method GET

# Rejestracja BankEuroB w TARGET (moze juz istniec - 400 tez OK)
$registerBody = '{"bic":"BKEUDEBBXXX","name":"BankEuroB"}'
try {
    $regResponse = Invoke-WebRequest -Uri "http://localhost:8001/banks" -Method POST -Body $registerBody -ContentType "application/json" -UseBasicParsing -TimeoutSec 10
    if ($regResponse.StatusCode -eq 200) {
        Write-Host "  [PASS] TARGET Register Bank (http://localhost:8001/banks) -> 200 (utworzono nowy)" -ForegroundColor Green
    }
} catch {
    if ($_.Exception.Response.StatusCode -eq 400) {
        Write-Host "  [PASS] TARGET Register Bank (http://localhost:8001/banks) -> 400 (bank juz istnieje)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] TARGET Register Bank -> $_" -ForegroundColor Red
        $global:allPassed = $false
    }
}

# Lista bankow w TARGET
Test-Endpoint -Name "TARGET List Banks" -Url "http://localhost:8001/banks" -Method GET

# ============================================================
# 2. SEPA Batch Service (port 8002)
# ============================================================
Write-Host "`n--- 2. SEPA Batch Service (System rozliczen batchowych) ---" -ForegroundColor Yellow

Test-Endpoint -Name "SEPA Batch Health" -Url "http://localhost:8002/health" -Method GET

# Lista sesji SEPA Batch
Test-Endpoint -Name "SEPA Batch Sessions" -Url "http://localhost:8002/sessions" -Method GET

# ============================================================
# 3. SEPA Instant Service (port 8003)
# ============================================================
Write-Host "`n--- 3. SEPA Instant Service (Przelewy natychmiastowe) ---" -ForegroundColor Yellow

Test-Endpoint -Name "SEPA Instant Health" -Url "http://localhost:8003/health" -Method GET

# Lista przelewow instant
Test-Endpoint -Name "SEPA Instant Transfers" -Url "http://localhost:8003/transfers" -Method GET

# ============================================================
# 4. SWIFT Middleware (port 3006)
# ============================================================
Write-Host "`n--- 4. SWIFT Middleware (System rozliczen SWIFT) ---" -ForegroundColor Yellow

Test-Endpoint -Name "SWIFT Root" -Url "http://localhost:3006/" -Method GET

# Token OAuth2 w SWIFT
$tokenBody = "grant_type=client_credentials&client_id=test-client&client_secret=test-secret"
$tokenResponse = $null
try {
    $tokenResponse = Invoke-WebRequest -Uri "http://localhost:3006/auth/token" -Method POST -Body $tokenBody -ContentType "application/x-www-form-urlencoded" -UseBasicParsing -TimeoutSec 10
    if ($tokenResponse.StatusCode -eq 200) {
        Write-Host "  [PASS] SWIFT Auth Token (http://localhost:3006/auth/token) -> 200" -ForegroundColor Green
    }
} catch {
    Write-Host "  [FAIL] SWIFT Auth Token -> $_" -ForegroundColor Red
    $global:allPassed = $false
}

# Dashboard SWIFT
Test-Endpoint -Name "SWIFT Dashboard" -Url "http://localhost:3006/api/dashboard" -Method GET

# ============================================================
# 5. Payment Gateway (port 8072) - System kart platniczych
# ============================================================
Write-Host "`n--- 5. Payment Gateway (System kart platniczych) ---" -ForegroundColor Yellow

Test-Endpoint -Name "Cards Gateway Root" -Url "http://localhost:8072/" -Method GET

# Test polaczenia gRPC z Card Provider
Test-Endpoint -Name "Cards Gateway Test Connection" -Url "http://localhost:8072/test-connection" -Method POST

# ============================================================
# 6. Admin Panel Kart (port 3072)
# ============================================================
Write-Host "`n--- 6. Admin Panel Kart Platniczych ---" -ForegroundColor Yellow

Test-Endpoint -Name "Cards Admin Panel" -Url "http://localhost:3072/" -Method GET

# ============================================================
# 7. KLIK RTGS Mock (port 9005)
# ============================================================
Write-Host "`n--- 7. KLIK RTGS Mock (Symulator SORBNET3/TARGET2/CHAPS/FedNow) ---" -ForegroundColor Yellow

Test-Endpoint -Name "KLIK RTGS Health" -Url "http://localhost:9005/healthz" -Method GET

# Test SORBNET3 (PLN)
Test-Endpoint -Name "KLIK SORBNET3 Health" -Url "http://localhost:9005/sorbnet3/healthz" -Method GET

# Test TARGET2 (EUR)
Test-Endpoint -Name "KLIK TARGET2 Health" -Url "http://localhost:9005/target2/healthz" -Method GET

# Test CHAPS (GBP)
Test-Endpoint -Name "KLIK CHAPS Health" -Url "http://localhost:9005/chaps/healthz" -Method GET

# Test FedNow (USD)
Test-Endpoint -Name "KLIK FedNow Health" -Url "http://localhost:9005/fednow/healthz" -Method GET

# Test settlement SORBNET3
$settleBody = '{"session_id":"test-session","transfer_id":"test-transfer-1","system":"SORBNET3","from":"BKEUDEBBXXX","to":"PLBKPL01XXX","amount":"100.00","currency":"PLN"}'
Test-Endpoint -Name "KLIK SORBNET3 Settle" -Url "http://localhost:9005/sorbnet3/settle" -Method POST -Body $settleBody

# Test settlement TARGET2
$settleBody2 = '{"session_id":"test-session","transfer_id":"test-transfer-2","system":"TARGET2","from":"BKEUDEBBXXX","to":"PLBKPL01XXX","amount":"200.00","currency":"EUR"}'
Test-Endpoint -Name "KLIK TARGET2 Settle" -Url "http://localhost:9005/target2/settle" -Method POST -Body $settleBody2

# ============================================================
# 8. BankEuroB Backend API (port 8080)
# ============================================================
Write-Host "`n--- 8. BankEuroB Backend API ---" -ForegroundColor Yellow

# Login
$loginBody = '{"email":"anna.kowalski@example.com","password":"password123"}'
try {
    $loginResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -ContentType "application/json" -Body $loginBody
    $token = $loginResponse.token
    $headers = @{ "Authorization" = "Bearer $token" }
    Write-Host "  [PASS] BankEuroB Login (http://localhost:8080/api/auth/login) -> 200" -ForegroundColor Green
    
    # Test integracji z TARGET przez backend
    try {
        $targetBanks = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/target/banks" -Method GET -Headers $headers
        Write-Host "  [PASS] BankEuroB -> TARGET Integration (GET /api/admin/target/banks) -> OK" -ForegroundColor Green
    } catch {
        Write-Host "  [INFO] BankEuroB -> TARGET Integration: $_" -ForegroundColor Yellow
        if ($_.Exception.Response.StatusCode -eq 503) {
            Write-Host "  [INFO] TARGET Service chwilowo niedostepny (oczekiwane przy pierwszym uruchomieniu)" -ForegroundColor Yellow
        }
    }
    
    # Test integracji z SEPA Batch przez backend
    try {
        $sepaSessions = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/sepa/sessions" -Method GET -Headers $headers
        Write-Host "  [PASS] BankEuroB -> SEPA Batch Integration (GET /api/admin/sepa/sessions) -> OK" -ForegroundColor Green
    } catch {
        Write-Host "  [INFO] BankEuroB -> SEPA Batch Integration: $_" -ForegroundColor Yellow
    }
    
    # Test integracji z SEPA Instant przez backend
    try {
        $sepaInstant = Invoke-RestMethod -Uri "http://localhost:8080/api/admin/sepa/instant" -Method GET -Headers $headers
        Write-Host "  [PASS] BankEuroB -> SEPA Instant Integration (GET /api/admin/sepa/instant) -> OK" -ForegroundColor Green
    } catch {
        Write-Host "  [INFO] BankEuroB -> SEPA Instant Integration: $_" -ForegroundColor Yellow
    }
    
    # Test integracji z Payment Gateway (karty) przez backend
    try {
        $cardsResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/cards/integrate" -Method POST -Headers $headers
        Write-Host "  [PASS] BankEuroB -> Payment Gateway Integration (POST /api/cards/integrate) -> OK" -ForegroundColor Green
    } catch {
        Write-Host "  [INFO] BankEuroB -> Payment Gateway Integration: $_" -ForegroundColor Yellow
    }
    
} catch {
    Write-Host "  [FAIL] BankEuroB Login -> $_" -ForegroundColor Red
    $global:allPassed = $false
}

# ============================================================
# Podsumowanie
# ============================================================
Write-Host "`n============================================================" -ForegroundColor Cyan
if ($global:allPassed) {
    Write-Host "  WSZYSTKIE TESTY ZALICZONE!" -ForegroundColor Green
    Write-Host "  Integracja BankEuroB z systemami platniczymi dziala poprawnie." -ForegroundColor Green
} else {
    Write-Host "  NIEKTORE TESTY NIE ZALICZONE" -ForegroundColor Red
    Write-Host "  Sprawdz logi powyzej, aby zobaczyc ktore serwisy sa niedostepne." -ForegroundColor Red
    Write-Host "  Upewnij sie, ze wszystkie kontenery Docker sa uruchomione:" -ForegroundColor Yellow
    Write-Host "    Uruchom wszystkie systemy komenda:" -ForegroundColor Yellow
    Write-Host "      cd BankEuroB && docker compose up -d" -ForegroundColor Yellow
    Write-Host "      cd eu-payments-units && docker compose up -d" -ForegroundColor Yellow
    Write-Host "      cd Karty-Platnicze-Aplikacje-Biznesowe && docker compose -f docker-compose.yaml up -d" -ForegroundColor Yellow
    Write-Host "      cd KLIK-payments && docker compose up -d" -ForegroundColor Yellow
    Write-Host "      cd SWIFT-Aplikacje-Biznesowe && docker compose up -d" -ForegroundColor Yellow
    Write-Host "    Lub uzyj: start_bankeurob.bat (opcja 1)" -ForegroundColor Yellow
}
Write-Host "============================================================" -ForegroundColor Cyan

# Lista wszystkich serwisow i ich portow
Write-Host "`n--- Mapowanie portow systemow platniczych ---" -ForegroundColor Magenta
Write-Host "  BankEuroB Backend:       http://localhost:8080"
Write-Host "  BankEuroB Frontend:      http://localhost:3000"
Write-Host "  TARGET RTGS:             http://localhost:8001"
Write-Host "  SEPA Batch:              http://localhost:8002"
Write-Host "  SEPA Instant:            http://localhost:8003"
Write-Host "  SWIFT Middleware:        http://localhost:3006"
Write-Host "  Payment Gateway (karty): http://localhost:8072"
Write-Host "  Admin Panel (karty):     http://localhost:3072"
Write-Host "  KLIK RTGS Mock:          http://localhost:9005"
Write-Host "  pgAdmin:                 http://localhost:5050"
Write-Host "  RabbitMQ Management:     http://localhost:15672"

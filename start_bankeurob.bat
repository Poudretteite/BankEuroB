@echo off
title BankEuroB - Uruchamianie...
color 0A

echo.
echo  ============================================================
echo   BankEuroB - Automatyczny Start
echo  ============================================================
echo.

:: === Wybor trybu uruchomienia ===
echo Wybierz tryb uruchomienia:
echo   [1] Docker (wszystkie systemy platnicze)
echo   [2] Lokalny (tylko BankEuroB, bez systemow platniczych)
echo.
choice /c 12 /n /m "Wybierz 1 lub 2: " /t 10 /d 2
if errorlevel 2 goto local
if errorlevel 1 goto docker

:docker
echo.
echo  ============================================================
echo   Uruchamianie przez Docker z systemami platniczymi
echo  ============================================================
echo.

:: Sprawdz czy Docker jest uruchomiony
docker info >nul 2>&1
if errorlevel 1 (
    echo  [!] Docker Desktop nie jest uruchomiony!
    echo  Uruchom Docker Desktop i sprobuj ponownie.
    echo  Alternatywnie uruchom tryb lokalny (opcja 2).
    pause
    exit /b 1
)

echo [1/6] Budowanie i uruchamianie BankEuroB (rdzen)...
cd /d %~dp0
docker compose up -d --build
if errorlevel 1 (
    echo  [!] Blad podczas uruchamiania kontenerow BankEuroB
    pause
    exit /b 1
)
echo  [OK] BankEuroB uruchomiony

echo [2/6] Budowanie i uruchamianie EU Payments Units...
cd /d %~dp0..\eu-payments-units
docker compose up -d --build
if errorlevel 1 (
    echo  [!] Blad podczas uruchamiania EU Payments Units
    pause
    exit /b 1
)
echo  [OK] EU Payments Units uruchomione

echo [3/6] Budowanie i uruchamianie Karty Platnicze...
cd /d %~dp0..\Karty-Platnicze-Aplikacje-Biznesowe
docker compose -f docker-compose.yaml up -d --build
if errorlevel 1 (
    echo  [!] Blad podczas uruchamiania Kart Platniczych
    pause
    exit /b 1
)
echo  [OK] Karty Platnicze uruchomione

echo [4/6] Budowanie i uruchamianie KLIK Payments...
cd /d %~dp0..\KLIK-payments
docker compose up -d --build
if errorlevel 1 (
    echo  [!] Blad podczas uruchamiania KLIK Payments
    pause
    exit /b 1
)
echo  [OK] KLIK Payments uruchomione

echo [5/6] Budowanie i uruchamianie SWIFT...
cd /d %~dp0..\SWIFT-Aplikacje-Biznesowe
docker compose up -d --build
if errorlevel 1 (
    echo  [!] Blad podczas uruchamiania SWIFT
    pause
    exit /b 1
)
echo  [OK] SWIFT uruchomiony

echo [6/6] Oczekiwanie na gotowosc backendu...
cd /d %~dp0
timeout /t 30 /nobreak >nul

echo.
echo  ============================================================
echo   APLIKACJA GOTOWA!
echo  ============================================================
echo   BankEuroB Frontend:       http://localhost:3000/
echo   BankEuroB Backend:        http://localhost:8080/
echo  ------------------------------------------------------------
echo   SYSTEMY PLATNICZE:
echo   TARGET RTGS:              http://localhost:8001
echo   SEPA Batch:               http://localhost:8002
echo   SEPA Instant:             http://localhost:8003
echo   SWIFT Middleware:         http://localhost:3006
echo   Payment Gateway (karty):  http://localhost:8072
echo   Admin Panel (karty):      http://localhost:3072
echo   KLIK RTGS Mock:           http://localhost:9005
echo  ------------------------------------------------------------
echo   NARZEDZIA:
echo   pgAdmin:                  http://localhost:5050
echo   RabbitMQ Management:      http://localhost:15672
echo  ------------------------------------------------------------
echo   Konta testowe:
echo     anna.kowalski@example.com  /  password123
echo     jan.nowak@example.com      /  password123
echo     admin@bankeurob.eu         /  admin123
echo  ============================================================
echo.
echo Nacisnij dowolny klawisz aby uruchomic test integracji...
pause >nul

:: Uruchom test integracji
powershell -ExecutionPolicy Bypass -File "%~dp0test_integration.ps1"
echo.
echo Nacisnij dowolny klawisz aby zamknac...
pause >nul
goto end

:local
echo.
echo  ============================================================
echo   Uruchamianie lokalne (tylko BankEuroB)
echo  ============================================================
echo.

:: === Sprawdz czy PostgreSQL dziala ===
echo [1/4] Sprawdzanie bazy danych PostgreSQL...
sc query postgresql-x64-17 | find "RUNNING" >nul 2>&1
if errorlevel 1 (
    echo  [!] PostgreSQL nie dziala - uruchamiam...
    net start postgresql-x64-17
    timeout /t 3 /nobreak >nul
) else (
    echo  [OK] PostgreSQL dziala na porcie 5432
)

:: === Uruchamianie Backendu w nowym oknie ===
echo.
echo [2/4] Uruchamianie backendu (Spring Boot)...
start "BankEuroB Backend" cmd /k "cd /d %~dp0backend && color 0B && echo === BankEuroB Backend === && set SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/bankeurob && set SPRING_DATASOURCE_USERNAME=bankeurob_user && set SPRING_DATASOURCE_PASSWORD=BankEuroB_Secret2026! && set JWT_SECRET=BankEuroBJwtSuperSecretKey2026MustBe256BitsLong!! && set JWT_EXPIRATION_MS=86400000 && set SPRING_RABBITMQ_HOST=localhost && set SPRING_RABBITMQ_PORT=5672 && gradlew.bat bootRun"
echo  [OK] Backend uruchomiony w osobnym oknie

:: === Czekaj az backend sie uruchomi (max 90 sekund) ===
echo.
echo [3/4] Oczekiwanie na gotowosc backendu (max ~60 sekund)...
set /a tries=0
:wait_backend
timeout /t 5 /nobreak >nul
set /a tries+=1
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8080/api/auth/login' -Method POST -ContentType 'application/json' -Body '{\"email\":\"check\",\"password\":\"check\"}' -UseBasicParsing -ErrorAction Stop; exit 0 } catch { if ($_.Exception.Response.StatusCode -eq 401 -or $_.Exception.Response.StatusCode -eq 403 -or $_.Exception.Response.StatusCode -eq 400) { exit 0 } else { exit 1 } }" >nul 2>&1
if errorlevel 1 (
    if %tries% lss 18 (
        echo  ... [%tries%/18] Backend jeszcze sie laduje...
        goto wait_backend
    ) else (
        echo  [WARN] Backend nie odpowiedzial w ciagu 90s - kontynuuje mimo to
    )
) else (
    echo  [OK] Backend gotowy po %tries%x5 sekundach!
)

:: === Uruchamianie Frontendu w nowym oknie ===
echo.
echo [4/4] Uruchamianie frontendu (React + Vite)...
start "BankEuroB Frontend" cmd /k "cd /d %~dp0frontend && color 0D && echo === BankEuroB Frontend === && npm run dev"
echo  [OK] Frontend uruchomiony!

:: === Czekaj chwile na frontend ===
timeout /t 4 /nobreak >nul

:: === Podsumowanie ===
echo.
echo  ============================================================
echo   APLIKACJA GOTOWA!
echo  ============================================================
echo   Frontend:  http://localhost:5173/
echo   Backend:   http://localhost:8080/
echo  ------------------------------------------------------------
echo   UWAGA: Systemy platnicze nie sa uruchomione lokalnie.
echo   Aby uruchomic wszystkie systemy, uzyj trybu Docker.
echo  ------------------------------------------------------------
echo   Konta testowe:
echo     anna.kowalski@example.com  /  password123
echo     jan.nowak@example.com      /  password123
echo     admin@bankeurob.eu         /  admin123
echo  ============================================================
echo.

:: === Otwieranie przegladarki ===
start "" "http://localhost:5173/"

echo Nacisnij dowolny klawisz aby zamknac to okno startowe...
pause >nul

:end

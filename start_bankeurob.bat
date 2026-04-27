@echo off
title BankEuroB - Uruchamianie...
color 0A

echo.
echo  ============================================================
echo   BankEuroB - Automatyczny Start
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

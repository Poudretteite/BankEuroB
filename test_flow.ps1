$ErrorActionPreference = "Stop"

# 1. Child login attempt
Write-Host "1. Child login attempt..."
$childLoginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -ContentType "application/json" -Body '{"email":"junior@example.com","password":"password123"}'
$attemptId = $childLoginRes.loginAttemptId
Write-Host "Child login attempt created: $attemptId"

# 2. Parent login
Write-Host "2. Parent login..."
$parentLoginRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -ContentType "application/json" -Body '{"email":"anna.kowalski@example.com","password":"password123"}'
$parentToken = $parentLoginRes.token
$headers = @{ "Authorization" = "Bearer $parentToken" }
Write-Host "Parent logged in."

# 3. Parent gets pending logins
Write-Host "3. Parent fetching pending logins..."
$pendingLogins = Invoke-RestMethod -Uri "http://localhost:8080/api/junior/pending-logins" -Method GET -Headers $headers
$found = $pendingLogins | Where-Object { $_.id -eq $attemptId }
if ($found) {
    Write-Host "Pending login found for child!"
} else {
    Write-Host "Pending login NOT found!"
    exit 1
}

# 4. Parent approves login
Write-Host "4. Parent approving login..."
Invoke-RestMethod -Uri "http://localhost:8080/api/junior/approve-login/$attemptId`?approved=true" -Method POST -Headers $headers
Write-Host "Login approved."

# 5. Child checks login status
Write-Host "5. Child checking login status..."
$statusRes = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login-status/$attemptId" -Method GET
if ($statusRes.token) {
    Write-Host "Child successfully received token! Flow works perfectly."
} else {
    Write-Host "Child did not receive token."
    exit 1
}

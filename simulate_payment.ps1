$cardToken = Read-Host "Podaj token swojej karty (skopiuj z zakładki Karty)"
$amount = Read-Host "Podaj kwotę zakupów (np. 15.50)"

$body = @{
    cardToken = $cardToken
    amount = [double]$amount
    currency = "EUR"
    merchantId = "Sklep Żabka"
    authorizationCode = "AUTH123"
    transactionId = "TXN-$(Get-Date -UFormat %s)"
} | ConvertTo-Json

Write-Host "Wysyłam żądanie płatności z terminala w sklepie..." -ForegroundColor Cyan

$response = Invoke-WebRequest -Uri "http://localhost:8080/api/cards/webhook/capture" `
    -Method Post `
    -Headers @{"Content-Type" = "application/json"} `
    -Body $body `
    -SkipHttpErrorCheck

Write-Host "Status Code:" $response.StatusCode
Write-Host "Odpowiedź:" $response.Content

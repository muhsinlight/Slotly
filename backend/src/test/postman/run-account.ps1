$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5433/slotly_test'
$env:DATABASE_USERNAME = 'slotly'
$env:DATABASE_PASSWORD = 'slotly'
$env:SERVER_PORT = '8082'
$env:LOGIN_RATE_LIMIT_ATTEMPTS = '1000'
$server = Start-Process java -ArgumentList @('-jar', (Join-Path $root 'backend/target/slotly-api-0.0.1-SNAPSHOT.jar')) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $env:TEMP 'slotly-account-api.log') -RedirectStandardError (Join-Path $env:TEMP 'slotly-account-api-error.log')
try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $health = Invoke-RestMethod 'http://127.0.0.1:8082/actuator/health'
            if ($health.status -eq 'UP') {
                $ready = $true
                break
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    if (!$ready) { throw 'Test API did not become healthy.' }
    & npx.cmd --yes newman run (Join-Path $PSScriptRoot 'account.postman_collection.json') --env-var 'baseUrl=http://127.0.0.1:8082/api' --reporters cli
    if ($LASTEXITCODE -ne 0) { throw 'Account Postman checks failed.' }
} finally {
    if (!$server.HasExited) { Stop-Process -Id $server.Id }
}

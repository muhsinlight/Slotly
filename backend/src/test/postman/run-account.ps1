$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5433/slotly_test'
$env:DATABASE_USERNAME = 'slotly'
$env:DATABASE_PASSWORD = 'slotly'
$port = if ($env:POSTMAN_API_PORT) { $env:POSTMAN_API_PORT } else { '8082' }
$probe = New-Object System.Net.Sockets.TcpClient
try {
    $async = $probe.BeginConnect('127.0.0.1', [int]$port, $null, $null)
    if ($async.AsyncWaitHandle.WaitOne(300) -and $probe.Connected) {
        throw "Port $port is already occupied. Set POSTMAN_API_PORT to a free port."
    }
} finally {
    $probe.Close()
}
$env:SERVER_PORT = $port
$env:LOGIN_RATE_LIMIT_ATTEMPTS = '1000'
$javaSettings = & cmd.exe /c 'java -XshowSettings:properties -version 2>&1'
$javaHomeLine = $javaSettings | Select-String '^\s*java.home = (.+)$' | Select-Object -First 1
if (!$javaHomeLine) { throw 'Could not resolve the Java runtime.' }
$javaExecutable = Join-Path $javaHomeLine.Matches[0].Groups[1].Value.Trim() 'bin/java.exe'
$server = Start-Process $javaExecutable -ArgumentList @('-jar', (Join-Path $root 'backend/target/slotly-api-0.0.1-SNAPSHOT.jar')) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $env:TEMP 'slotly-account-api.log') -RedirectStandardError (Join-Path $env:TEMP 'slotly-account-api-error.log')
try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $health = Invoke-RestMethod "http://127.0.0.1:$port/actuator/health"
            if ($health.status -eq 'UP') {
                $ready = $true
                break
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    if (!$ready) { throw 'Test API did not become healthy.' }
    foreach ($collection in @('account', 'recovery')) {
        & npx.cmd --yes newman run (Join-Path $PSScriptRoot "$collection.postman_collection.json") --env-var "baseUrl=http://127.0.0.1:$port/api" --reporters cli
        if ($LASTEXITCODE -ne 0) { throw "$collection Postman checks failed." }
    }
} finally {
    if (!$server.HasExited) {
        Stop-Process -Id $server.Id
        $server.WaitForExit()
    }
}

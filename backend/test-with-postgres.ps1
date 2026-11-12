$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repositoryRoot 'compose.integration-test.yaml'

docker compose -f $composeFile up -d --wait postgres-test
if ($LASTEXITCODE -ne 0) {
    throw 'Could not start the isolated Slotly integration-test database.'
}

try {
    $env:DATABASE_URL = 'jdbc:postgresql://localhost:5433/slotly_test'
    $env:DATABASE_USERNAME = 'slotly'
    $env:DATABASE_PASSWORD = 'slotly'
    & (Join-Path $PSScriptRoot 'mvnw.cmd') --batch-mode verify
    if ($LASTEXITCODE -ne 0) {
        throw "Maven verification failed with exit code $LASTEXITCODE."
    }
}
finally {
    docker compose -f $composeFile down
}

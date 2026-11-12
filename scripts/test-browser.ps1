$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repositoryRoot 'docker/compose.browser-test.yaml'

docker compose -f $composeFile up -d --wait
if ($LASTEXITCODE -ne 0) {
    throw 'Could not start the isolated browser-test database.'
}

try {
    Push-Location (Join-Path $repositoryRoot 'backend')
    try {
        & .\mvnw.cmd --batch-mode '-DskipTests' package
        if ($LASTEXITCODE -ne 0) {
            throw 'Backend packaging failed.'
        }
    }
    finally {
        Pop-Location
    }

    Push-Location (Join-Path $repositoryRoot 'frontend')
    try {
        & npm.cmd run test:e2e
        if ($LASTEXITCODE -ne 0) {
            throw 'Browser tests failed.'
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    docker compose -f $composeFile down
}

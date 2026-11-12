$ErrorActionPreference = 'Stop'

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if (!(Test-Path -LiteralPath (Join-Path $PSScriptRoot 'check.ps1') -PathType Leaf)) {
        throw 'Local scripts/check.ps1 is missing. Copy it before installing hooks.'
    }

    $existingHooksPath = & git config --get core.hooksPath
    if ($LASTEXITCODE -gt 1) {
        throw 'Could not read Git hook configuration.'
    }
    if ($existingHooksPath -and $existingHooksPath -ne '.githooks') {
        throw "An existing hooksPath is configured: $existingHooksPath. Integrate the pre-push hook before replacing it."
    }

    & git config --local core.hooksPath .githooks
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not install repository hooks.'
    }
    Write-Host 'Pre-push checks enabled for this clone.'
}
finally {
    Pop-Location
}

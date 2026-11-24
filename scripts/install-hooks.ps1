$ErrorActionPreference = 'Stop'

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
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
    if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'check.ps1') -PathType Leaf) {
        Write-Host 'Pre-push checks enabled for this clone (using local scripts/check.ps1).'
    }
    else {
        Write-Host 'Pre-push hook enabled (will run frontend build; add local scripts/check.ps1 for full DB checks).'
    }
}
finally {
    Pop-Location
}

# LittleLock - run unit tests
# Usage: .\tools\check.ps1

$ErrorActionPreference = "Stop"

Write-Host "Running LittleLock unit tests..." -ForegroundColor Cyan

& ./gradlew testDebugUnitTest --rerun-tasks

if ($LASTEXITCODE -ne 0) {
    Write-Host "Tests failed!" -ForegroundColor Red
    exit 1
}

Write-Host "All tests passed!" -ForegroundColor Green
exit 0

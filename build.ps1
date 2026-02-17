$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)][string]$Message,
        [Parameter(Mandatory = $true)][scriptblock]$Action
    )

    Write-Host "== $Message ==" -ForegroundColor Cyan
    & $Action
}

function Get-DockerComposeCommand {
    $composeExe = Get-Command docker-compose -ErrorAction SilentlyContinue
    if ($composeExe) { return @('docker-compose') }

    $dockerExe = Get-Command docker -ErrorAction SilentlyContinue
    if ($dockerExe) { return @('docker', 'compose') }

    throw "docker-compose or docker compose not found in PATH."
}

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

Invoke-Step -Message "Backend: test & package" -Action {
    Push-Location "$repoRoot/backend"
    mvn clean test package
    Pop-Location
}

Invoke-Step -Message "Frontend: install & build" -Action {
    Push-Location "$repoRoot/frontend"
    corepack enable *> $null
    pnpm install --frozen-lockfile
    pnpm build
    Pop-Location
}

$composeCmd = Get-DockerComposeCommand
Invoke-Step -Message "Docker compose build & restart" -Action {
    & $composeCmd build
    try {
        & $composeCmd down -v
    }
    catch {
        Write-Warning "docker compose down failed (continuing): $_"
    }
    & $composeCmd up -d
}

Invoke-Step -Message "Playwright smoke (against http://aisocialgame.seekerhut.com:10030)" -Action {
    Push-Location "$repoRoot/frontend"
    $env:PLAYWRIGHT_BASE_URL = "http://aisocialgame.seekerhut.com:10030"
    pnpm test:e2e
    Pop-Location
}

Write-Host "All done. Frontend: http://aisocialgame.seekerhut.com:10030  Backend: http://aisocialgame.seekerhut.com:20030" -ForegroundColor Green

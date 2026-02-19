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

function Invoke-Compose {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    if (Get-Command docker-compose -ErrorAction SilentlyContinue) {
        & docker-compose @Arguments
        return
    }

    if (Get-Command docker -ErrorAction SilentlyContinue) {
        & docker compose @Arguments
        return
    }

    throw "docker-compose or docker compose not found in PATH."
}

function Wait-Http {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [int]$Attempts = 60,
        [int]$DelaySeconds = 2
    )

    for ($i = 0; $i -lt $Attempts; $i++) {
        try {
            Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 | Out-Null
            return
        }
        catch {
            Start-Sleep -Seconds $DelaySeconds
        }
    }

    throw "Service $Url not ready after $Attempts attempts."
}

function Set-DefaultEnv {
    if ([string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_URL)) {
        $mysqlHost = if ([string]::IsNullOrWhiteSpace($env:MYSQL_HOST)) { '127.0.0.1' } else { $env:MYSQL_HOST }
        $mysqlPort = if ([string]::IsNullOrWhiteSpace($env:MYSQL_PORT)) { '3308' } else { $env:MYSQL_PORT }
        $mysqlDb = if ([string]::IsNullOrWhiteSpace($env:MYSQL_DB)) { 'aisocialgame' } else { $env:MYSQL_DB }
        $env:SPRING_DATASOURCE_URL = "jdbc:mysql://$mysqlHost`:$mysqlPort/${mysqlDb}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    }
    if ([string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_USERNAME)) { $env:SPRING_DATASOURCE_USERNAME = "aisocialgame" }
    if ([string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_PASSWORD)) { $env:SPRING_DATASOURCE_PASSWORD = "aisocialgame_pwd" }
    if ([string]::IsNullOrWhiteSpace($env:SPRING_DATA_REDIS_HOST)) { $env:SPRING_DATA_REDIS_HOST = "127.0.0.1" }
    if ([string]::IsNullOrWhiteSpace($env:SPRING_DATA_REDIS_PORT)) { $env:SPRING_DATA_REDIS_PORT = "6381" }
    if ([string]::IsNullOrWhiteSpace($env:CONSUL_HTTP_ADDR)) { $env:CONSUL_HTTP_ADDR = "http://127.0.0.1:8502" }
    if ([string]::IsNullOrWhiteSpace($env:QDRANT_HOST)) { $env:QDRANT_HOST = "http://127.0.0.1" }
    if ([string]::IsNullOrWhiteSpace($env:QDRANT_PORT)) { $env:QDRANT_PORT = "6335" }
    if ([string]::IsNullOrWhiteSpace($env:QDRANT_ENABLED)) { $env:QDRANT_ENABLED = "true" }
    if ([string]::IsNullOrWhiteSpace($env:SPRING_PROFILES_ACTIVE)) { $env:SPRING_PROFILES_ACTIVE = "prod" }
    if ([string]::IsNullOrWhiteSpace($env:SSO_CALLBACK_URL)) { $env:SSO_CALLBACK_URL = "http://aisocialgame.aienie.com/sso/callback" }
}

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

if ([string]::IsNullOrWhiteSpace($env:CI)) { $env:CI = "true" }
Set-DefaultEnv
Write-Host "Using shared services: MYSQL=$($env:SPRING_DATASOURCE_URL) REDIS=$($env:SPRING_DATA_REDIS_HOST):$($env:SPRING_DATA_REDIS_PORT) QDRANT=$($env:QDRANT_HOST):$($env:QDRANT_PORT) CONSUL=$($env:CONSUL_HTTP_ADDR)"

Invoke-Step -Message "Backend: test & package" -Action {
    Push-Location "$repoRoot/backend"
    try {
        mvn clean test package
    }
    finally {
        Pop-Location
    }
}

Invoke-Step -Message "Frontend: install & build" -Action {
    Push-Location "$repoRoot/frontend"
    try {
        corepack enable *> $null
        pnpm install --frozen-lockfile
        pnpm build
    }
    finally {
        Pop-Location
    }
}

Invoke-Step -Message "Docker compose pull & restart (prod)" -Action {
    try {
        Invoke-Compose -Arguments @('down', '-v')
    }
    catch {
        Write-Warning "docker compose down failed (continuing): $_"
    }
    Invoke-Compose -Arguments @('pull')
    Invoke-Compose -Arguments @('up', '-d')
}

Invoke-Step -Message "Wait for services" -Action {
    Wait-Http -Url "http://127.0.0.1:10030"
    Wait-Http -Url "http://127.0.0.1:20030/actuator/health"
}

Write-Host "All done. Frontend: http://aisocialgame.aienie.com  Backend: http://aisocialgame.aienie.com/api" -ForegroundColor Green

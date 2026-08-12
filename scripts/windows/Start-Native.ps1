[CmdletBinding()]
param(
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot '..\..\env.local'),
    [switch]$NativeWorker
)

Set-StrictMode -Version Latest

if (-not $NativeWorker) {
    $shellName = if ($PSVersionTable.PSEdition -eq 'Core') { 'pwsh.exe' } else { 'powershell.exe' }
    $shellPath = Join-Path $PSHOME $shellName
    if (-not (Test-Path -LiteralPath $shellPath -PathType Leaf)) {
        $shellPath = (Get-Command -Name $shellName -CommandType Application -ErrorAction Stop | Select-Object -First 1).Path
    }
    $resolvedEnvironmentFile = if (Test-Path -LiteralPath $EnvironmentFile -PathType Leaf) { (Resolve-Path -LiteralPath $EnvironmentFile).Path } else { $EnvironmentFile }
    & $shellPath -NoLogo -NoProfile -File $PSCommandPath -EnvironmentFile $resolvedEnvironmentFile -NativeWorker
    $workerExitCode = $LASTEXITCODE
    if ($null -ne $workerExitCode -and $workerExitCode -ne 0) {
        exit $workerExitCode
    }
    return
}

$modulePath = Join-Path $PSScriptRoot 'Native-Localbase.psm1'
Import-Module -Name $modulePath -Force

$projectRoot = Get-NativeProjectRoot
$stateDirectory = Get-NativeStateDirectory -ProjectRoot $projectRoot
$environmentNames = @(
    'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD',
    'SPRING_DATA_REDIS_HOST', 'SPRING_DATA_REDIS_PORT', 'SPRING_DATA_REDIS_PASSWORD',
    'QDRANT_HOST', 'QDRANT_PORT',
    'SERVER_ADDRESS', 'SERVER_PORT', 'BACKEND_PORT', 'VITE_LOCAL_BACKEND_PORT',
    'SSO_USER_SERVICE_BASE_URL', 'SSO_CALLBACK_URL', 'USER_GRPC_ADDR', 'BILLING_GRPC_ADDR', 'AI_GRPC_ADDR',
    'APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN', 'APP_EXTERNAL_PAYSERVICE_JWT',
    'APP_EXTERNAL_AISERVICE_HMAC_CALLER', 'APP_EXTERNAL_AISERVICE_HMAC_SECRET'
)

Import-LiteralEnvironmentFile -Path $EnvironmentFile -ClearNames $environmentNames
Set-LocalbaseJdbcMySqlEndpoint -EnvironmentName 'SPRING_DATASOURCE_URL'
$env:SPRING_DATA_REDIS_HOST = 'localbase.testhut.top'
$env:SPRING_DATA_REDIS_PORT = '26379'
$env:QDRANT_HOST = 'http://localbase.testhut.top'
$env:QDRANT_PORT = '26333'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '11031'
$env:BACKEND_PORT = '11031'
$env:VITE_LOCAL_BACKEND_PORT = '11031'

Assert-NativePortAvailable -Port 11031
Assert-NativePortAvailable -Port 11030
Assert-LocalbasePorts -Ports @(23306, 26379, 26333)

$backendCommand = Get-RequiredNativeCommandPath -Name 'mvn.cmd'
$frontendCommand = Get-RequiredNativeCommandPath -Name 'pnpm.cmd'
$backendDirectory = Join-Path $projectRoot 'backend'
$frontendDirectory = Join-Path $projectRoot 'frontend'

try {
    $backendProcess = Start-NativeProcess -Name 'backend' -FilePath $backendCommand -ArgumentList @('-q', 'spring-boot:run') -WorkingDirectory $backendDirectory -StateDirectory $stateDirectory -ProjectRoot $projectRoot
    Wait-NativeLoopbackPort -Port 11031 -ExpectedRootProcessId $backendProcess.Id -Name 'backend' -StateDirectory $stateDirectory -ProjectRoot $projectRoot
    $frontendProcess = Start-NativeProcess -Name 'frontend' -FilePath $frontendCommand -ArgumentList @('run', 'dev', '--', '--host', '127.0.0.1', '--port', '11030', '--strictPort') -WorkingDirectory $frontendDirectory -StateDirectory $stateDirectory -ProjectRoot $projectRoot
    Wait-NativeLoopbackPort -Port 11030 -ExpectedRootProcessId $frontendProcess.Id -Name 'frontend' -StateDirectory $stateDirectory -ProjectRoot $projectRoot
}
catch {
    $failure = $_
    foreach ($name in @('frontend', 'backend')) {
        try {
            Stop-NativeProcess -Name $name -StateDirectory $stateDirectory -ProjectRoot $projectRoot
        }
        catch {
            Write-Warning "Could not clean up native $name after startup failure: $($_.Exception.Message)"
        }
    }
    throw $failure
}

Write-Host 'AISocialGame is running at http://127.0.0.1:11030. Logs and PID state are in .native-run.'

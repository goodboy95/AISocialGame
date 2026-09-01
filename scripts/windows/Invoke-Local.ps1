[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Build', 'Start', 'Stop', 'Status', 'Test')][string]$Action,
    [ValidateSet('All', 'Backend', 'Frontend')][string]$Component = 'All',
    [string]$EnvironmentFile = (Join-Path $(if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { $env:TEMP }) 'Aienie\secrets\aisocialgame.env'),
    [ValidateRange(30, 900)][int]$StartupTimeoutSeconds = 180,
    [ValidateSet('L1', 'L2')][string]$TestLevel = 'L2',
    [switch]$AsJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'UserServiceJwt-Contract.ps1')
Import-Module (Join-Path $PSScriptRoot 'LocalRuntime.psm1') -Force

if ($PSVersionTable.PSEdition -ne 'Core' -or $PSVersionTable.PSVersion.Major -lt 7 -or $env:OS -ne 'Windows_NT') {
    throw 'AISocialGame Windows-native operations require PowerShell 7 or newer on Windows.'
}

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$stateRoot = Join-Path $(if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { $env:TEMP }) 'Aienie\native-runs\aisocialgame'
$statePath = Join-Path $stateRoot 'processes.json'
$components = @(
    [pscustomobject]@{ Name = 'Backend'; Directory = (Join-Path $repoRoot 'backend'); Command = 'mvn.cmd'; Install = @(); Build = @('-q', '-DskipTests', 'package'); Test = @('-q', 'test'); Start = @('-f', (Join-Path $repoRoot 'backend\pom.xml'), '-q', 'spring-boot:run'); Port = 11031; Health = '/actuator/health'; HealthKind = 'JsonUp'; RequiresInstall = $false },
    [pscustomobject]@{ Name = 'Frontend'; Directory = (Join-Path $repoRoot 'frontend'); Command = 'pnpm.cmd'; Install = @('install', '--frozen-lockfile'); Build = @('run', 'build'); Test = @('run', 'test:unit', '--maxWorkers=1'); Start = @('--dir', (Join-Path $repoRoot 'frontend'), 'exec', 'vite', '--host', '127.0.0.1', '--port', '11030', '--strictPort'); Port = 11030; Health = '/'; HealthKind = 'Http200'; RequiresInstall = $true }
)

function Get-Selected {
    if ($Component -eq 'All') { return @($components) }
    return @($components | Where-Object Name -eq $Component)
}

function Get-Tool([string]$Name) {
    $tool = Get-Command -Name $Name -CommandType Application -ErrorAction Stop | Select-Object -First 1
    if ($tool.Path) {
        return $tool.Path
    }
    return $tool.Source
}

function Invoke-Checked([string]$Command, [string[]]$Arguments, [string]$WorkingDirectory, [string]$Label) {
    Push-Location $WorkingDirectory
    try {
        & $Command @Arguments | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "$Label failed with exit code $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
}

function Read-PrivateEnvironment([string]$Path) {
    $file = Get-Item -LiteralPath (Resolve-AienieLocalPrivateFile -Path $Path -Label 'Environment file') -Force -ErrorAction Stop
    $blocked = @('PATH', 'PATHEXT', 'COMSPEC', 'SYSTEMROOT', 'WINDIR', 'PSMODULEPATH', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'MAVEN_OPTS', 'NODE_OPTIONS')
    $result = @{}
    $lineNumber = 0
    foreach ($line in [IO.File]::ReadLines($file.FullName)) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
        $match = [Regex]::Match($line, '^\s*(?:export\s+)?(?<name>[A-Za-z_][A-Za-z0-9_]*)\s*=(?<value>.*)$')
        if (-not $match.Success) { throw "Environment file has an unsupported line at $lineNumber. Use literal NAME=value entries only." }
        $name = $match.Groups['name'].Value
        if ($blocked -contains $name.ToUpperInvariant() -or (Test-AienieProtectedProcessVariable -Name $name)) { throw "Environment file cannot set protected process variable '$name'." }
        if ($result.ContainsKey($name)) { throw "Environment file defines '$name' more than once." }
        $value = $match.Groups['value'].Value
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) { $value = $value.Substring(1, $value.Length - 2) }
        $result[$name] = $value
    }
    return $result
}

function Get-ChildEnvironment([hashtable]$PrivateValues) {
    $result = @{}
    foreach ($entry in [Environment]::GetEnvironmentVariables('Process').GetEnumerator()) { $result[[string]$entry.Key] = [string]$entry.Value }
    Assert-AienieLocalOnlyEnvironment -Values $result
    Assert-AienieLocalOnlyEnvironment -Values $PrivateValues
    foreach ($name in @('APP_EXTERNAL_GRPC_AUTH_REQUIRED', 'APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN',
            'APP_EXTERNAL_USERSERVICE_JWT_CALLER_ID', 'APP_EXTERNAL_USERSERVICE_JWT_ISSUER',
            'APP_EXTERNAL_USERSERVICE_JWT_SECRET', 'APP_EXTERNAL_USERSERVICE_JWT_AUDIENCE',
            'APP_EXTERNAL_USERSERVICE_JWT_TTL_SECONDS', 'APP_EXTERNAL_USERSERVICE_JWT_SCOPES')) {
        $result.Remove($name)
    }
    foreach ($entry in $PrivateValues.GetEnumerator()) { $result[[string]$entry.Key] = [string]$entry.Value }
    Assert-AienieLocalOnlyEnvironment -Values $result
    Remove-AienieProcessInjectionEnvironment -Values $result
    $result['ENV'] = 'local'
    $result['APP_ENV'] = 'local'
    $result['SPRING_PROFILES_ACTIVE'] = 'local'
    $result['AIENIE_RUNTIME_PLANE'] = 'windows-local'
    $result['AUTH_MODE'] = 'password'
    $result['APP_PROJECT_KEY'] = 'aisocialgame'
    $result['SERVER_ADDRESS'] = '127.0.0.1'
    $result['SERVER_PORT'] = '11031'
    $result['VITE_LOCAL_BACKEND_PORT'] = '11031'
    $result['SPRING_DATASOURCE_URL'] = 'jdbc:mysql://localbase.testhut.top:23306/aisocialgame?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
    $result['SPRING_DATA_REDIS_HOST'] = 'localbase.testhut.top'
    $result['SPRING_DATA_REDIS_PORT'] = '26379'
    $result['SPRING_DATA_REDIS_SSL_ENABLED'] = 'false'
    $result['QDRANT_HOST'] = 'http://localbase.testhut.top'
    $result['QDRANT_PORT'] = '26333'
    $result['USER_GRPC_ADDR'] = 'static://localuserservice.testhut.top:12001'
    $result['BILLING_GRPC_ADDR'] = 'static://localpayservice.testhut.top:12021'
    $result['AI_GRPC_ADDR'] = 'static://localaiservice.testhut.top:12011'
    $result['GRPC_CLIENT_USER_SECURITY_TRUST_CERT_COLLECTION'] = ''
    $result['GRPC_CLIENT_BILLING_SECURITY_TRUST_CERT_COLLECTION'] = ''
    $result['GRPC_CLIENT_AI_SECURITY_TRUST_CERT_COLLECTION'] = ''
    $result['USER_GRPC_NEGOTIATION_TYPE'] = 'TLS'
    $result['BILLING_GRPC_NEGOTIATION_TYPE'] = 'TLS'
    $result['AI_GRPC_NEGOTIATION_TYPE'] = 'TLS'
    $result['BILLING_GRPC_PLAINTEXT_ENABLED'] = 'false'
    $result['APP_SECURITY_ALLOW_PLAINTEXT_GRPC'] = 'false'
    $result['SSO_USER_SERVICE_BASE_URL'] = 'https://localuserservice.testhut.top'
    $result['SSO_CALLBACK_URL'] = 'https://localsocialgame.testhut.top/sso/callback'
    return $result
}

function Get-State {
    if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) { return $null }
    try { return Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json } catch { throw "Cannot parse native process state '$statePath': $($_.Exception.Message)" }
}

function Test-Record($Record) {
    $spec = $components | Where-Object Name -ceq ([string]$Record.Name) | Select-Object -First 1
    if ($null -eq $spec) { return $false }
    return Test-AienieManagedProcessRecord -Record $Record -ExpectedName $spec.Name -ExpectedWorkingRoot $spec.Directory -ExpectedPorts @($spec.Port)
}

function Save-State([object[]]$Records) {
    New-Item -ItemType Directory -Path $stateRoot -Force | Out-Null
    [pscustomobject]@{ product = 'AISocialGame'; projectRoot = $repoRoot; updatedUtc = [DateTime]::UtcNow.ToString('o'); processes = @($Records) } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $statePath -Encoding utf8NoBOM
}

function Test-Tcp([int]$Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try { $task = $client.ConnectAsync('127.0.0.1', $Port); return $task.Wait(1000) -and $client.Connected } catch { return $false } finally { $client.Dispose() }
}

function Test-Http([int]$Port, [string]$Path, [ValidateSet('Http200', 'JsonUp')][string]$HealthKind = 'Http200') {
    return Invoke-AienieBoundedHttp -Uri ("http://127.0.0.1:{0}{1}" -f $Port, $Path) -TimeoutSeconds 3 -RequireJsonUp:($HealthKind -eq 'JsonUp')
}

function Resolve-ManagedProcess($Record, $Spec) {
    return Complete-AienieManagedProcessRecord -Record $Record -Ports @($Spec.Port)
}

function Start-Component($Spec, [hashtable]$ChildEnvironment) {
    if ($Spec.RequiresInstall -and -not (Test-Path -LiteralPath (Join-Path $Spec.Directory 'node_modules') -PathType Container)) {
        throw "Native $($Spec.Name) dependencies are missing. Run Build-Local.ps1 before Start-Local.ps1."
    }
    if (Test-Tcp -Port $Spec.Port) { throw "Port $($Spec.Port) is already listening. Stop the owning process before native startup." }
    $logs = Join-Path $stateRoot 'logs'
    New-Item -ItemType Directory -Path $logs -Force | Out-Null
    $process = Start-Process -FilePath (Get-Tool $Spec.Command) -ArgumentList $Spec.Start -WorkingDirectory $Spec.Directory -Environment $ChildEnvironment -RedirectStandardOutput (Join-Path $logs "$($Spec.Name.ToLowerInvariant()).stdout.log") -RedirectStandardError (Join-Path $logs "$($Spec.Name.ToLowerInvariant()).stderr.log") -PassThru
    $launchedStartUtc = try { $process.StartTime.ToUniversalTime() } catch { $null }
    $record = $null
    try {
        $record = New-AienieRootProcessRecord -Name $Spec.Name -Process $process -WorkingRoot $Spec.Directory
        $record | Add-Member -NotePropertyName Port -NotePropertyValue $Spec.Port
        $record | Add-Member -NotePropertyName HealthPath -NotePropertyValue $Spec.Health
        $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
        do {
            if ($process.HasExited) { throw "Native $($Spec.Name) process exited during startup with code $($process.ExitCode)." }
            if ((Test-Tcp -Port $Spec.Port) -and (Test-Http -Port $Spec.Port -Path $Spec.Health -HealthKind $Spec.HealthKind)) {
                return Resolve-ManagedProcess $record $Spec
            }
            Start-Sleep -Seconds 1
            $process.Refresh()
        } while ([DateTime]::UtcNow -lt $deadline)
        throw "Native $($Spec.Name) did not become healthy on port $($Spec.Port) within $StartupTimeoutSeconds seconds."
    } catch {
        if ($null -ne $record) {
            Stop-AienieRootProcessRecord -Record $record
        } else {
            $current = Get-Process -Id $process.Id -ErrorAction SilentlyContinue
            if ($null -ne $current -and $null -ne $launchedStartUtc -and
                [Math]::Abs(($current.StartTime.ToUniversalTime() - $launchedStartUtc).TotalMilliseconds) -le 1000) {
                & (Join-Path $env:SystemRoot 'System32\taskkill.exe') /PID ([string]$process.Id) /T /F 2>$null | Out-Null
            }
        }
        $process.Dispose()
        throw
    }
}

function Stop-Record($Record) {
    $spec = $components | Where-Object Name -ceq ([string]$Record.Name) | Select-Object -First 1
    if ($null -eq $spec) { return $false }
    if (@($Record.Listeners).Count -eq 0 -and $null -ne $Record.PSObject.Properties['Process']) {
        Stop-AienieRootProcessRecord -Record $Record
        return $true
    }
    Stop-AienieManagedProcessRecord -Record $Record -ExpectedName $spec.Name -ExpectedWorkingRoot $spec.Directory -ExpectedPorts @($spec.Port)
    return $true
}

switch ($Action) {
    'Build' {
        foreach ($spec in Get-Selected) {
            $tool = Get-Tool $spec.Command
            if ($spec.Install.Count -gt 0) { Invoke-Checked $tool $spec.Install $spec.Directory "$($spec.Name) dependency installation" }
            Invoke-Checked $tool $spec.Build $spec.Directory "$($spec.Name) build"
        }
    }
    'Test' {
        foreach ($spec in Get-Selected) {
            $tool = Get-Tool $spec.Command
            if ($spec.Install.Count -gt 0) { Invoke-Checked $tool $spec.Install $spec.Directory "$($spec.Name) dependency installation" }
            Invoke-Checked $tool $spec.Build $spec.Directory "$($spec.Name) L1 build"
            if ($TestLevel -eq 'L2') { Invoke-Checked $tool $spec.Test $spec.Directory "$($spec.Name) L2 tests" }
        }
    }
    'Start' {
        $privateValues = Read-PrivateEnvironment $EnvironmentFile
        Assert-AisocialUserServiceJwtEnvironment -Values $privateValues
        $state = Get-State
        $existing = if ($null -eq $state) { @() } else { @($state.processes | Where-Object { Test-Record $_ }) }
        if ($null -ne $state -and -not [string]::Equals([string]$state.projectRoot, $repoRoot, [StringComparison]::OrdinalIgnoreCase)) { throw "Native process state belongs to another checkout: $($state.projectRoot)" }
        $selected = Get-Selected
        foreach ($spec in $selected) { if (@($existing | Where-Object Name -eq $spec.Name).Count -gt 0) { throw "Native $($spec.Name) is already running. Use Get-LocalStatus.ps1 or Stop-Local.ps1." } }
        $started = @()
        try {
            foreach ($spec in $selected) {
                $started += Start-Component $spec (Get-ChildEnvironment $privateValues)
                Save-State (@($existing) + @($started | ForEach-Object { ConvertTo-AieniePersistedProcessRecord -Record $_ }))
            }
            Save-State (@($existing) + @($started | ForEach-Object { ConvertTo-AieniePersistedProcessRecord -Record $_ }))
            & $PSCommandPath -Action Status
        } catch {
            foreach ($record in $started) { try { Stop-Record $record | Out-Null } catch { Write-Warning "Could not clean up native $($record.Name): $($_.Exception.Message)" } }
            throw
        } finally {
            foreach ($record in $started) { $record.Process.Dispose() }
        }
    }
    'Stop' {
        $state = Get-State
        if ($null -eq $state) { Write-Output 'No recorded AISocialGame Windows-native processes are active.'; return }
        if (-not [string]::Equals([string]$state.projectRoot, $repoRoot, [StringComparison]::OrdinalIgnoreCase)) { throw "Native process state belongs to another checkout: $($state.projectRoot)" }
        $remaining = @()
        foreach ($record in @($state.processes)) {
            if ($Component -ne 'All' -and $record.Name -ne $Component) { $remaining += $record; continue }
            if (Stop-Record $record) { Write-Output "Stopped AISocialGame $($record.Name) process $($record.RootProcess.ProcessId)." }
        }
        if ($remaining.Count -eq 0) { Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue } else { Save-State $remaining }
    }
    'Status' {
        $state = Get-State
        $records = if ($null -eq $state) { @() } else { @($state.processes) }
        $result = foreach ($spec in $components) {
            $record = @($records | Where-Object Name -eq $spec.Name | Select-Object -First 1)
            [pscustomobject]@{ product = 'AISocialGame'; component = $spec.Name; port = $spec.Port; processRecorded = $record.Count -eq 1; processLive = ($record.Count -eq 1 -and (Test-Record $record[0])); tcpListening = Test-Tcp $spec.Port; healthy = Test-Http $spec.Port $spec.Health $spec.HealthKind }
        }
        if ($AsJson) { $result | ConvertTo-Json -Depth 4 } else { $result | Format-Table -AutoSize }
    }
}

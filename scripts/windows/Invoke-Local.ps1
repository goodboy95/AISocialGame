[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Build', 'Start', 'Stop', 'Status', 'Test')][string]$Action,
    [ValidateSet('All', 'Backend', 'Frontend')][string]$Component = 'All',
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot '..\..\env.local'),
    [ValidateRange(30, 900)][int]$StartupTimeoutSeconds = 180,
    [ValidateSet('L1', 'L2')][string]$TestLevel = 'L2',
    [switch]$AsJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($PSVersionTable.PSEdition -ne 'Core' -or $PSVersionTable.PSVersion.Major -lt 7 -or $env:OS -ne 'Windows_NT') {
    throw 'AISocialGame Windows-native operations require PowerShell 7 or newer on Windows.'
}

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$stateRoot = Join-Path $(if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { $env:TEMP }) 'Aienie\native-runs\aisocialgame'
$statePath = Join-Path $stateRoot 'processes.json'
$operationalStateWriter = if ([string]::IsNullOrWhiteSpace($env:AIENIE_PRODUCT_STATE_WRITER)) {
    Join-Path $repoRoot '..\..\aienie-runtime\infrastructure\monitoring\windows-agent\Set-AienieProductOperationalState.ps1'
} else {
    $env:AIENIE_PRODUCT_STATE_WRITER
}
$components = @(
    [pscustomobject]@{ Name = 'Backend'; Directory = (Join-Path $repoRoot 'backend'); Command = 'mvn.cmd'; Install = @(); Build = @('-q', '-DskipTests', 'package'); Test = @('-q', 'test'); Start = @('-q', 'spring-boot:run'); Port = 11031; Health = '/actuator/health'; RequiresInstall = $false },
    [pscustomobject]@{ Name = 'Frontend'; Directory = (Join-Path $repoRoot 'frontend'); Command = 'pnpm.cmd'; Install = @('install', '--frozen-lockfile'); Build = @('run', 'build'); Test = @('run', 'test:unit'); Start = @('run', 'dev', '--', '--host', '127.0.0.1', '--port', '11030', '--strictPort'); Port = 11030; Health = '/'; RequiresInstall = $true }
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
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Environment file was not found: $Path" }
    $file = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if (($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Environment file must not be a reparse point: $Path" }
    $blocked = @('PATH', 'PATHEXT', 'COMSPEC', 'SYSTEMROOT', 'WINDIR', 'PSMODULEPATH', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'MAVEN_OPTS', 'NODE_OPTIONS')
    $result = @{}
    $lineNumber = 0
    foreach ($line in [IO.File]::ReadLines($file.FullName)) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
        $match = [Regex]::Match($line, '^\s*(?:export\s+)?(?<name>[A-Za-z_][A-Za-z0-9_]*)\s*=(?<value>.*)$')
        if (-not $match.Success) { throw "Environment file has an unsupported line at $lineNumber. Use literal NAME=value entries only." }
        $name = $match.Groups['name'].Value
        if ($blocked -contains $name.ToUpperInvariant() -or $name -like 'SPRING_CONFIG_*') { throw "Environment file cannot set protected process variable '$name'." }
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
    foreach ($entry in $PrivateValues.GetEnumerator()) { $result[[string]$entry.Key] = [string]$entry.Value }
    $result['VITE_LOCAL_BACKEND_PORT'] = '11031'
    $result['SPRING_DATA_REDIS_HOST'] = 'localbase.testhut.top'
    $result['QDRANT_HOST'] = 'http://localbase.testhut.top'
    $result['USER_GRPC_ADDR'] = 'static://localuserservice.testhut.top:12001'
    $result['BILLING_GRPC_ADDR'] = 'static://localpayservice.testhut.top:12021'
    $result['AI_GRPC_ADDR'] = 'static://localaiservice.testhut.top:12011'
    $localCaRootUri = 'file:///C:/ProgramData/AieniePki/pki/root/aienie-local-root-ca.crt'
    $result['GRPC_CLIENT_USER_SECURITY_TRUST_CERT_COLLECTION'] = $localCaRootUri
    $result['GRPC_CLIENT_BILLING_SECURITY_TRUST_CERT_COLLECTION'] = $localCaRootUri
    $result['GRPC_CLIENT_AI_SECURITY_TRUST_CERT_COLLECTION'] = $localCaRootUri
    $result['USER_GRPC_NEGOTIATION_TYPE'] = 'TLS'
    $result['BILLING_GRPC_NEGOTIATION_TYPE'] = 'TLS'
    $result['AI_GRPC_NEGOTIATION_TYPE'] = 'TLS'
    return $result
}

function Get-State {
    if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) { return $null }
    try { return Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json } catch { throw "Cannot parse native process state '$statePath': $($_.Exception.Message)" }
}

function Test-Record($Record) {
    try {
        $process = Get-Process -Id ([int]$Record.Pid) -ErrorAction Stop
        $expected = if ($Record.ProcessStartTimeUtc -is [DateTime]) { ([DateTime]$Record.ProcessStartTimeUtc).ToUniversalTime() } else { [DateTimeOffset]::Parse([string]$Record.ProcessStartTimeUtc).UtcDateTime }
        return [Math]::Abs(($process.StartTime.ToUniversalTime() - $expected).TotalSeconds) -le 2
    } catch { return $false }
}

function Save-State([object[]]$Records) {
    New-Item -ItemType Directory -Path $stateRoot -Force | Out-Null
    [pscustomobject]@{ product = 'AISocialGame'; projectRoot = $repoRoot; updatedUtc = [DateTime]::UtcNow.ToString('o'); processes = @($Records) } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $statePath -Encoding utf8NoBOM
}

function Test-Tcp([int]$Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try { $task = $client.ConnectAsync('127.0.0.1', $Port); return $task.Wait(1000) -and $client.Connected } catch { return $false } finally { $client.Dispose() }
}

function Test-Http([int]$Port, [string]$Path) {
    try { $response = Invoke-WebRequest -Uri ("http://127.0.0.1:{0}{1}" -f $Port, $Path) -TimeoutSec 3 -SkipHttpErrorCheck; return $response.StatusCode -ge 200 -and $response.StatusCode -lt 500 } catch { return $false }
}

function Resolve-ManagedProcess($Record, $Spec) {
    $connection = Get-NetTCPConnection -State Listen -LocalPort $Spec.Port -ErrorAction Stop |
        Where-Object { $_.LocalAddress -in @('127.0.0.1', '::1', '::') } |
        Select-Object -First 1
    if ($null -eq $connection) { throw "Native $($Spec.Name) has no listener on port $($Spec.Port) after readiness." }
    $minimumStart = [DateTime]::Parse([string]$Record.ProcessStartTimeUtc).ToUniversalTime().AddSeconds(-2)
    $current = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $connection.OwningProcess) -ErrorAction Stop
    $managedRoot = $current
    while ($current.ParentProcessId -gt 0) {
        $parent = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $current.ParentProcessId) -ErrorAction SilentlyContinue
        if ($null -eq $parent -or $parent.CreationDate.ToUniversalTime() -lt $minimumStart) { break }
        $current = $parent
        if ($current.Name -notin @('cmd.exe', 'powershell.exe', 'pwsh.exe')) { $managedRoot = $current }
    }
    $managed = Get-Process -Id $managedRoot.ProcessId -ErrorAction Stop
    return [pscustomobject]@{ Name = $Record.Name; Pid = $managedRoot.ProcessId; ProcessStartTimeUtc = $managed.StartTime.ToUniversalTime().ToString('o'); Port = $Record.Port; HealthPath = $Record.HealthPath; Process = $Record.Process }
}

function Start-Component($Spec, [hashtable]$ChildEnvironment) {
    if ($Spec.RequiresInstall -and -not (Test-Path -LiteralPath (Join-Path $Spec.Directory 'node_modules') -PathType Container)) {
        throw "Native $($Spec.Name) dependencies are missing. Run Build-Local.ps1 before Start-Local.ps1."
    }
    if (Test-Tcp -Port $Spec.Port) { throw "Port $($Spec.Port) is already listening. Stop the owning process before native startup." }
    $logs = Join-Path $stateRoot 'logs'
    New-Item -ItemType Directory -Path $logs -Force | Out-Null
    $process = Start-Process -FilePath (Get-Tool $Spec.Command) -ArgumentList $Spec.Start -WorkingDirectory $Spec.Directory -Environment $ChildEnvironment -RedirectStandardOutput (Join-Path $logs "$($Spec.Name.ToLowerInvariant()).stdout.log") -RedirectStandardError (Join-Path $logs "$($Spec.Name.ToLowerInvariant()).stderr.log") -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    do {
        if ($process.HasExited) { throw "Native $($Spec.Name) process exited during startup with code $($process.ExitCode)." }
        if ((Test-Tcp -Port $Spec.Port) -and (Test-Http -Port $Spec.Port -Path $Spec.Health)) {
            $record = [pscustomobject]@{ Name = $Spec.Name; Pid = $process.Id; ProcessStartTimeUtc = (Get-Process -Id $process.Id).StartTime.ToUniversalTime().ToString('o'); Port = $Spec.Port; HealthPath = $Spec.Health; Process = $process }
            return Resolve-ManagedProcess $record $Spec
        }
        Start-Sleep -Seconds 1
        $process.Refresh()
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Native $($Spec.Name) did not become healthy on port $($Spec.Port) within $StartupTimeoutSeconds seconds."
}

function Stop-Record($Record) {
    if (-not (Test-Record $Record)) { return $false }
    & taskkill.exe /PID ([string]$Record.Pid) /T /F | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Unable to stop native $($Record.Name) process $($Record.Pid) (taskkill exit $LASTEXITCODE)." }
    return $true
}

function Publish-OperationalState {
    if (-not (Test-Path -LiteralPath $operationalStateWriter -PathType Leaf)) {
        Write-Verbose "Operational-state writer is unavailable: $operationalStateWriter"
        return
    }
    $state = Get-State
    $records = if ($null -eq $state) { @() } else { @($state.processes) }
    $live = @($records | Where-Object { Test-Record $_ })
    $healthyComponents = @($components | Where-Object {
            (Test-Tcp $_.Port) -and (Test-Http $_.Port $_.Health)
        })
    $desiredState = if ($live.Count -gt 0) { 'running' } else { 'stopped' }
    $health = if ($healthyComponents.Count -eq $components.Count) {
        'healthy'
    } elseif ($live.Count -gt 0) {
        'unhealthy'
    } else {
        'unknown'
    }
    try {
        & $operationalStateWriter -Component 'ai-social-game' -DesiredState $desiredState -Health $health
    } catch {
        # Shared observability publishing is best-effort and must not turn a
        # successful repository build or test into a false negative.
        Write-Warning "Could not publish AISocialGame operational state: $($_.Exception.Message)"
    }
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
            $arguments = if ($TestLevel -eq 'L1') { $spec.Build } else { $spec.Test }
            Invoke-Checked $tool $arguments $spec.Directory "$($spec.Name) $TestLevel verification"
        }
        Publish-OperationalState
    }
    'Start' {
        $privateValues = Read-PrivateEnvironment $EnvironmentFile
        $state = Get-State
        $existing = if ($null -eq $state) { @() } else { @($state.processes | Where-Object { Test-Record $_ }) }
        if ($null -ne $state -and -not [string]::Equals([string]$state.projectRoot, $repoRoot, [StringComparison]::OrdinalIgnoreCase)) { throw "Native process state belongs to another checkout: $($state.projectRoot)" }
        $selected = Get-Selected
        foreach ($spec in $selected) { if (@($existing | Where-Object Name -eq $spec.Name).Count -gt 0) { throw "Native $($spec.Name) is already running. Use Get-LocalStatus.ps1 or Stop-Local.ps1." } }
        $started = @()
        try {
            foreach ($spec in $selected) {
                $started += Start-Component $spec (Get-ChildEnvironment $privateValues)
                Save-State (@($existing) + @($started | ForEach-Object { [pscustomobject]@{ Name = $_.Name; Pid = $_.Pid; ProcessStartTimeUtc = $_.ProcessStartTimeUtc; Port = $_.Port; HealthPath = $_.HealthPath } }))
            }
            Save-State (@($existing) + @($started | ForEach-Object { [pscustomobject]@{ Name = $_.Name; Pid = $_.Pid; ProcessStartTimeUtc = $_.ProcessStartTimeUtc; Port = $_.Port; HealthPath = $_.HealthPath } }))
            Publish-OperationalState
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
            if (Stop-Record $record) { Write-Output "Stopped AISocialGame $($record.Name) process $($record.Pid)." }
        }
        if ($remaining.Count -eq 0) { Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue } else { Save-State $remaining }
        Publish-OperationalState
    }
    'Status' {
        $state = Get-State
        $records = if ($null -eq $state) { @() } else { @($state.processes) }
        $result = foreach ($spec in $components) {
            $record = @($records | Where-Object Name -eq $spec.Name | Select-Object -First 1)
            [pscustomobject]@{ product = 'AISocialGame'; component = $spec.Name; port = $spec.Port; processRecorded = $record.Count -eq 1; processLive = ($record.Count -eq 1 -and (Test-Record $record[0])); tcpListening = Test-Tcp $spec.Port; healthy = Test-Http $spec.Port $spec.Health }
        }
        Publish-OperationalState
        if ($AsJson) { $result | ConvertTo-Json -Depth 4 } else { $result | Format-Table -AutoSize }
    }
}

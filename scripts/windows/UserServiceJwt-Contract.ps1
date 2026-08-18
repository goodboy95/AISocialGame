Set-StrictMode -Version Latest

function Assert-AisocialUserServiceJwtEnvironment {
    [CmdletBinding()]
    param([Parameter(Mandatory)][System.Collections.IDictionary]$Values)

    function Read-Value([string]$Name) {
        if ($Values.Contains($Name) -and $null -ne $Values[$Name]) { return [string]$Values[$Name] }
        return ''
    }

    $legacy = Read-Value 'APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN'
    if ($legacy.Length -gt 0) {
        throw 'APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN is legacy and must be absent or empty.'
    }
    if ((Read-Value 'APP_EXTERNAL_GRPC_AUTH_REQUIRED') -eq 'false') { return }

    $expected = [ordered]@{
        APP_EXTERNAL_USERSERVICE_JWT_CALLER_ID = 'aisocialgame'
        APP_EXTERNAL_USERSERVICE_JWT_ISSUER = 'aisocialgame'
        APP_EXTERNAL_USERSERVICE_JWT_AUDIENCE = 'aienie-userservice-grpc'
        APP_EXTERNAL_USERSERVICE_JWT_SCOPES = 'user.auth.session.read,user.directory.read,user.ban.read,user.ban.write'
    }
    foreach ($entry in $expected.GetEnumerator()) {
        if ((Read-Value $entry.Key) -cne $entry.Value) {
            throw "Invalid canonical UserService caller JWT setting: $($entry.Key)."
        }
    }

    $ttlText = Read-Value 'APP_EXTERNAL_USERSERVICE_JWT_TTL_SECONDS'
    $ttl = 0L
    if (-not [long]::TryParse($ttlText, [Globalization.NumberStyles]::None,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$ttl) -or $ttl -lt 30 -or $ttl -gt 900) {
        throw 'APP_EXTERNAL_USERSERVICE_JWT_TTL_SECONDS must be an integer between 30 and 900.'
    }

    $secret = Read-Value 'APP_EXTERNAL_USERSERVICE_JWT_SECRET'
    $secretBytes = [Text.Encoding]::UTF8.GetByteCount($secret)
    $normalized = $secret.ToUpperInvariant()
    if ($secretBytes -lt 32 -or $secretBytes -gt 4096 -or $secret -cne $secret.Trim() -or
            $secret -match '[\x00-\x1F\x7F]' -or $normalized.Contains('REPLACE') -or
            $normalized.Contains('CHANGE_ME') -or $normalized.Contains('CHANGE-ME') -or
            $normalized.Contains('CHANGEME') -or
            $normalized.Contains('PLACEHOLDER') -or $normalized.StartsWith('<') -or $normalized.EndsWith('>')) {
        throw 'APP_EXTERNAL_USERSERVICE_JWT_SECRET must contain 32..4096 non-placeholder UTF-8 bytes without boundary whitespace or controls.'
    }
}

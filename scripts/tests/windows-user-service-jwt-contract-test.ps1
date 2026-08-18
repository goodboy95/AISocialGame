$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\windows\UserServiceJwt-Contract.ps1')

$valid = @{
    APP_EXTERNAL_GRPC_AUTH_REQUIRED = 'true'
    APP_EXTERNAL_USERSERVICE_JWT_CALLER_ID = 'aisocialgame'
    APP_EXTERNAL_USERSERVICE_JWT_ISSUER = 'aisocialgame'
    APP_EXTERNAL_USERSERVICE_JWT_SECRET = 'aisocialgame-userservice-test-secret-32-bytes'
    APP_EXTERNAL_USERSERVICE_JWT_AUDIENCE = 'aienie-userservice-grpc'
    APP_EXTERNAL_USERSERVICE_JWT_TTL_SECONDS = '300'
    APP_EXTERNAL_USERSERVICE_JWT_SCOPES = 'user.auth.session.read,user.directory.read,user.ban.read,user.ban.write'
}
Assert-AisocialUserServiceJwtEnvironment -Values $valid

foreach ($mutation in @(
        @{ Name = 'APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN'; Value = 'legacy-static-token' },
        @{ Name = 'APP_EXTERNAL_USERSERVICE_JWT_CALLER_ID'; Value = 'another-caller' },
        @{ Name = 'APP_EXTERNAL_USERSERVICE_JWT_AUDIENCE'; Value = 'wrong-audience' },
        @{ Name = 'APP_EXTERNAL_USERSERVICE_JWT_TTL_SECONDS'; Value = '901' },
        @{ Name = 'APP_EXTERNAL_USERSERVICE_JWT_SCOPES'; Value = 'user.auth.session.read,user.admin.write' },
        @{ Name = 'APP_EXTERNAL_USERSERVICE_JWT_SECRET'; Value = 'private-changeme-userservice-secret-32-bytes' },
        @{ Name = 'APP_EXTERNAL_USERSERVICE_JWT_SECRET'; Value = ' secret-with-boundary-whitespace-32-bytes' }
    )) {
    $candidate = @{} + $valid
    $candidate[$mutation.Name] = $mutation.Value
    try {
        Assert-AisocialUserServiceJwtEnvironment -Values $candidate
        throw "Expected rejection for $($mutation.Name)."
    } catch {
        if ($_.Exception.Message -like 'Expected rejection*') { throw }
        if ($_.Exception.Message.Contains([string]$mutation.Value)) {
            throw "Validation error leaked the rejected value for $($mutation.Name)."
        }
    }
}

foreach ($launcher in @('Start-Native.ps1', 'Invoke-Local.ps1')) {
    $content = Get-Content -Raw (Join-Path $PSScriptRoot "..\windows\$launcher")
    if ($content -notmatch 'Assert-AisocialUserServiceJwtEnvironment') {
        throw "$launcher does not invoke the UserService caller JWT startup gate."
    }
}

Write-Output 'AISocialGame Windows UserService JWT contract tests: PASS'

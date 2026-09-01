# Windows local direct runtime

Use PowerShell 7 from the repository checkout. The five standard entrypoints
run AISocialGame directly on Windows without Config Center, the release plane,
or the monitoring state writer.

| Operation | Command |
| --- | --- |
| Build | `.\scripts\windows\Build-Local.ps1` |
| Start | `.\scripts\windows\Start-Local.ps1` |
| Status | `.\scripts\windows\Get-LocalStatus.ps1` |
| Stop | `.\scripts\windows\Stop-Local.ps1` |
| L1/L2 checks | `.\scripts\windows\Test-Local.ps1 -Level L1` / `-Level L2` |

The default private input is
`%LOCALAPPDATA%\Aienie\secrets\aisocialgame.env`. It must be a regular,
non-reparse file. No special ACL, administrator ownership, UAC, or elevation
is required or created. The file retains credentials and caller-auth values;
local ports, identity, data endpoints, SSO endpoints, and public-service
targets are fixed by the launcher.

Startup rejects inherited or file-supplied non-local `ENV`, `APP_ENV`, and
`SPRING_PROFILES_ACTIVE` values, then forces the exact local profile,
`AIENIE_RUNTIME_PLANE=windows-local`, `APP_PROJECT_KEY=aisocialgame`, and loopback backend/frontend ports
`11031/11030`. Health is HTTP 200 with backend top-level `status=UP`. Process
identity state is stored under
`%LOCALAPPDATA%\Aienie\native-runs\aisocialgame`.

The Windows product instance is separate from the WSL dependency plane known
as `aienie-wsl`. MySQL, Redis, and Qdrant use `localbase.testhut.top`; public
AI/User/Pay calls use the three `local*.testhut.top` TLS endpoints. Java uses
the active JDK/JVM trust store. The launcher never redirects a public service
to a Windows loopback port and never enables trust-all or plaintext fallback.

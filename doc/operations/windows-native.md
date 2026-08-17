# Windows-native on-demand operations

Use PowerShell 7 from the repository checkout. The standard entrypoints run
the local application directly on Windows and do not invoke WSL or container
tooling.

| Operation | Command |
| --- | --- |
| Build | `.\scripts\windows\Build-Local.ps1` |
| Start | `.\scripts\windows\Start-Local.ps1 -EnvironmentFile <private-env-file>` |
| Status | `.\scripts\windows\Get-LocalStatus.ps1` |
| Stop | `.\scripts\windows\Stop-Local.ps1` |
| L1/L2 checks | `.\scripts\windows\Test-Local.ps1 -Level L1` / `-Level L2` |

The native launcher records only process identity and endpoint metadata under
`%LOCALAPPDATA%\Aienie\native-runs\aisocialgame`. It never copies the
private environment file into the repository and stops only matching owned
process trees. Successful start, stop, status, and test commands update the
shared product operational-state metric when its configured writer is
available. Run the build before the first native start. Browser L4 acceptance
remains a separate, already-running-runtime operation.

Shared MySQL, Redis, Qdrant and all three public services stay on the VM known
to operators as `ssh aienie-wsl`; the product itself never runs Docker or SSH.
The launcher uses `localbase.testhut.top` for shared data and TLS gRPC
`localuserservice/localpayservice/localaiservice.testhut.top` on
`12001/12021/12011`, with the fixed Aienie local CA root injected into all
three gRPC clients.

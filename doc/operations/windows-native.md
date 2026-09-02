# Windows 本机调试启动

在仓库检出目录使用 PowerShell 7。仅保留两个前台调试脚本，直接在 Windows 上运行
AISocialGame，不经过 Config Center、发布平面或监控状态写入器。

| 操作 | 命令 |
| --- | --- |
| 启动后端（127.0.0.1:11031） | `.\scripts\windows\Start-Backend.ps1` |
| 启动前端（127.0.0.1:11030） | `.\scripts\windows\Start-Frontend.ps1` |

两个脚本都在前台运行：日志直接输出到当前控制台，Ctrl+C 停止，退出码透传给
调用方，因此不再需要单独的 Stop/Status 入口或进程状态文件。

默认私有输入为 `%LOCALAPPDATA%\Aienie\secrets\aisocialgame.env`（必须是普通
非 reparse 文件，可用 `-EnvironmentFile` 覆盖）。不要求特殊 ACL、管理员所有权、
UAC 或提权。文件保存凭据与 caller-auth 值；本地端口、身份、数据端点、SSO 端点
与公共服务目标由脚本固定。

启动拒绝继承或文件提供的非本地 `ENV`、`APP_ENV`、`SPRING_PROFILES_ACTIVE`
值，随后强制本地 profile、`AIENIE_RUNTIME_PLANE=windows-local`、
`APP_PROJECT_KEY=aisocialgame` 与回环端口 11031/11030；同时剥离
`JAVA_TOOL_OPTIONS`、`MAVEN_OPTS`、`SPRING_APPLICATION_JSON`、`SPRING_CONFIG_*`
等进程注入变量，并在启动前执行 UserService caller JWT 契约校验（拒绝旧
`APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN` 非空值）。后端由
`mvn spring-boot:run` 启动，前端由 `pnpm exec vite` 启动；前端依赖缺失时自动
执行 `pnpm install --frozen-lockfile`。健康验证为
`http://127.0.0.1:11031/actuator/health` 返回 HTTP 200 且顶层 `status=UP`。

Windows 产品实例与 `aienie-wsl` 依赖平面分离。MySQL、Redis、Qdrant 使用
`localbase.testhut.top`；公共服务 AI/User/Pay 调用三个 `local*.testhut.top`
TLS 端点。Java 使用当前 JDK/JVM 信任库。脚本不会把公共服务重定向到 Windows
回环端口，也不启用 trust-all 或明文回退。

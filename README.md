# AISocialGame

基于 Spring Boot + React 的社交推理游戏平台。

## 技术栈

- 后端：Java 25、Spring Boot、MySQL、Redis、gRPC
- 前端：React 18、TypeScript、Vite、Tailwind、TanStack Query、shadcn/ui
- 部署：Docker Compose（仅编排本项目的前后端）

## 项目结构

- `frontend/`：前端源码、构建配置、Playwright 工具配置
- `backend/`：后端源码、SQL、proto、单测
- `doc/`：接口、模块、测试与运维文档
- `build.sh`：唯一部署脚本；本地域名默认为 `localsocialgame.testhut.top`，可通过 `APP_DOMAIN` 覆盖；每次部署重建 frontend 镜像（`docker compose build frontend`）以确保前端产物最新
- `env.example`：无秘密的配置清单；复制为被忽略的 `env.local` 后填入真实值并设置权限 `0600`

## 认证与积分

- 登录/注册统一走 user-service SSO，本项目不提供本地账号体系。
- 项目专属积分在本项目本地账本管理。
- 通用积分由 pay-service 提供，并支持 1:1 兑换为项目永久专属积分。
- 首次登录会自动执行：
  - pay-service 用户初始化
  - 本地积分账户初始化

## gRPC 安全要求（严格）

默认启用 `APP_EXTERNAL_GRPC_AUTH_REQUIRED=true`，并要求以下变量非空：

- `APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN`
- `APP_EXTERNAL_PAYSERVICE_JWT`
- `APP_EXTERNAL_AISERVICE_HMAC_CALLER`
- `APP_EXTERNAL_AISERVICE_HMAC_SECRET`

缺失任一变量时，后端会在启动期 fail-fast。

## 运行依赖

项目依赖以下外部服务：

- MySQL
- Redis
- Qdrant
- user-service / pay-service / ai-service

本地部署默认不依赖 Consul：

- MySQL / Redis / Qdrant：`localbase.testhut.top:23306 / 26379 / 26333`
- user-service gRPC：`static://localuserservice.testhut.top:443`，TLS
- pay-service gRPC：`static://localpayservice.testhut.top:443`，TLS
- ai-service gRPC：`static://localaiservice.testhut.top:443`，TLS
- SSO 入口：`https://localuserservice.testhut.top`

MySQL、Redis、Qdrant 由外部环境提供，项目脚本不负责部署、初始化或连通性预检。

## 部署

### 启动前脚本清单

测试/正式环境默认以 `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` 启动，后端不会在启动时自动改表。涉及表结构变更的 SQL 需要先在目标数据库执行，确认成功后再启动应用。

首次部署或版本升级时，先按日期顺序执行并核验迁移。管理员 TOTP 迁移使用
`CREATE TABLE IF NOT EXISTS`，可幂等重复核验：

```bash
mysql \
  --host="${MYSQL_HOST:-localbase.testhut.top}" \
  --port="${MYSQL_PORT:-23306}" \
  --user="${SPRING_DATASOURCE_USERNAME:-aisocialgame}" \
  --password \
  aisocialgame < backend/sql/20260519_performance_stability.sql

mysql \
  --host="${MYSQL_HOST:-localbase.testhut.top}" \
  --port="${MYSQL_PORT:-23306}" \
  --user="${SPRING_DATASOURCE_USERNAME:-aisocialgame}" \
  --password \
  aisocialgame < backend/sql/20260810_admin_totp_auth.sql
```

说明：
- 执行前先备份目标库；`20260519` 脚本仅在尚未应用时执行。
- 启动应用前必须确认 `backend/sql/20260810_admin_totp_auth.sql` 已成功应用，认证表均存在。
- 后续新增版本化 SQL 时，按文件日期顺序在测试/正式环境启动前执行。

准备本机运行配置：

```bash
cp env.example env.local
chmod 600 env.local
# 编辑 env.local，替换全部占位符
```

`build.sh` 会拒绝缺失、符号链接或权限不是 `0600` 的 `env.local`。容器只读挂载并加载同一文件，
不会以仅存在于宿主进程的秘密通过预检。

每次部署执行：

```bash
./build.sh
```

如需切换域名，直接覆盖 `APP_DOMAIN`，不要再区分多套部署脚本：

```bash
APP_DOMAIN=socialgame.testhut.top ./build.sh
```

持续或可重复执行：
- `build.sh` 只负责构建和部署，不登录管理员，也不自动执行余额迁移。
- 历史账本需要全量迁移时，由授权运维人员在部署完成后显式运行
  `scripts/admin-billing-migrate-all.sh`。脚本会在首次使用时先完成 TOTP enrollment，
  再交互完成管理员登录和操作确认，结束时主动退出；返回 `failed>0` 时脚本以非零状态结束。

### Linux

统一部署：

```bash
./build.sh
APP_DOMAIN=socialgame.testhut.top ./build.sh
```

脚本流程包含：

1. 后端 `mvn clean test package`
2. 前端 `pnpm install --frozen-lockfile && pnpm build`
3. Docker Compose 重建前后端
4. 健康检查

说明：
- 只维护一个 `build.sh`，环境差异通过 `APP_DOMAIN` 与已有环境变量覆盖，不再维护研发/生产双入口。
- `build.sh` 不执行任何管理员登录或特权迁移；真实验收测试也不由它自动触发。

### VS Code F5（以 `backend/` 为工作区根）

1. 在 VS Code 中直接打开 `backend/` 目录。
2. 选择调试配置 `Backend: Launch AiSocialGameApplication` 并按 `F5`。
3. 调试前会自动执行 `backend: compile`，用于生成 protobuf/gRPC 代码。
4. 调试进程会读取权限为 `0600` 且未入库的 `../env.local`，未显式提供 `SERVER_PORT` 时会回退到 `BACKEND_PORT`。
5. 启动成功后，可访问 `http://127.0.0.1:11031/actuator/health` 验证服务状态。

### Windows 本机启动（localbase WSL）

共享 MySQL、Redis 与 Qdrant 由 `aienie-wsl` 提供，应用仅通过 `localbase.testhut.top` 访问。首次启动前，请以管理员身份从项目根目录执行：

```powershell
.\scripts\windows\Ensure-LocalbaseHosts.ps1
```

该脚本幂等维护带标记的 `172.20.0.2 localbase.testhut.top` hosts 条目。准备好被 Git 忽略的 `env.local` 后，执行 `.\scripts\windows\Start-Native.ps1`；它按字面读取 `NAME=value`、验证 localbase 解析与共享数据端口，并将后端和前端分别限制在 `127.0.0.1:11031`、`127.0.0.1:11030`。执行 `.\scripts\windows\Stop-Native.ps1` 停止进程。停止不会删除 hosts 映射，日志与 PID 状态位于 `.native-run/`。

## 域名与端口

- 本地域名：`localsocialgame.testhut.top`
- 预发布域名：`socialgame.testhut.top`
- 生产迁移目标：`socialgame.seekerhut.com`（完成切换验收前不得描述为当前入口）
- 前端端口：`11030`
- 后端端口：`11031`

## 关键文档

- 结构：`doc/structure.md`
- 认证与钱包：`doc/modules/auth-wallet-module.md`
- gRPC 集成：`doc/modules/grpc-integration-module.md`
- 测试与运维：`doc/test/integratedTest.md`

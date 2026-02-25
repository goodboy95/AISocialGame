# AISocialGame

基于 Spring Boot + React 的社交推理游戏平台。

## 技术栈

- 后端：Java 21、Spring Boot、MySQL、Redis、gRPC
- 前端：React 18、TypeScript、Vite、Tailwind、TanStack Query、shadcn/ui
- 部署：Docker Compose（仅编排本项目的前后端）

## 项目结构

- `frontend/`：前端源码、构建配置、Playwright 测试
- `backend/`：后端源码、SQL、proto、单测
- `doc/`：接口、模块、测试与运维文档
- `build.sh`：测试域名部署脚本（`aisocialgame.seekerhut.com`）
- `build_prod.sh`：正式域名部署脚本（`aisocialgame.aienie.com`）
- `build_common.sh`：`build.sh/build_prod.sh` 共用部署逻辑
- `build_local.ps1`：Windows 本地直启脚本
- `env.txt`：部署配置（可被系统环境变量覆盖）

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

项目依赖以下外部服务（默认 `192.168.5.141`）：

- MySQL
- Redis
- Qdrant
- Consul
- user-service / pay-service / ai-service

当依赖不可达时，部署脚本会直接失败并提示缺失依赖。

## 部署

### Linux

测试环境部署：

```bash
./build.sh
```

正式环境部署：

```bash
./build_prod.sh
```

脚本流程包含：

1. 后端 `mvn clean test package`
2. 前端 `pnpm install && pnpm build`
3. Docker Compose 重建前后端
4. 健康检查
5. 自动执行“全量积分迁移”
6. Playwright 回归（含真实链路测试）

### Windows（本地直启）

```powershell
.\build_local.ps1
```

## 域名与端口

- 测试域名：`aisocialgame.seekerhut.com`
- 正式域名：`aisocialgame.aienie.com`
- 前端端口：`11030`
- 后端端口：`11031`

## 关键文档

- 结构：`doc/structure.md`
- 认证与钱包：`doc/modules/auth-wallet-module.md`
- gRPC 集成：`doc/modules/grpc-integration-module.md`
- 测试与运维：`doc/test/integratedTest.md`

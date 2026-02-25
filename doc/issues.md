# 问题跟踪（更新于 2026-02-24）

## 当前状态

当前无阻塞发布的已知问题。

## 已解决（本轮）

### 1. 三服务 gRPC 鉴权未统一注入

- 现象：调用 user/pay/ai 服务时，存在未携带必需 metadata 的风险。
- 修复：
  - 新增 `UserGrpcAuthClientInterceptor`
  - 新增 `BillingGrpcAuthClientInterceptor`
  - 新增 `AiGrpcHmacClientInterceptor`
  - 三个 gRPC client 全量挂载拦截器
- 验证：后端编译通过，调用路径可自动注入鉴权头。

### 2. 鉴权配置缺失导致运行时不确定失败

- 现象：缺失凭据时仅在运行时调用报错，不易排查。
- 修复：
  - 新增 `ExternalGrpcAuthValidator`
  - 默认 `APP_EXTERNAL_GRPC_AUTH_REQUIRED=true`
  - 缺失关键变量时启动期 fail-fast
- 验证：配置缺失会在启动阶段直接报错。

### 3. 部署脚本重复且迁移执行不稳定

- 现象：`build.sh` 与 `build_prod.sh` 逻辑重复，迁移需人工触发。
- 修复：
  - 新增 `build_common.sh`
  - `build.sh` / `build_prod.sh` 改为薄包装，仅域名差异
  - 部署后自动执行 `migrate-all`
- 验证：脚本结构已统一并可自动迁移。

## 观察项（非阻塞）

- 前端构建存在 chunk 体积告警（`index-*.js > 500kB`），不影响当前功能，可后续做按路由拆包优化。

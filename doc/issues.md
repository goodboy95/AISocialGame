# 问题跟踪（更新于 2026-03-04）

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

### 4. pay-service 服务 JWT 过期导致 SSO 回调 401

- 现象：`POST /api/auth/sso-callback` 返回 `401`，消息为 `Invalid token`。
- 根因：`APP_EXTERNAL_PAYSERVICE_JWT` 过期，`BillingGrpcClient.ensureUserInitialized` 调用被 pay-service gRPC 鉴权拒绝。
- 修复：
  - 重新签发满足 pay-service 鉴权约束的服务 JWT；
  - 通过 `sudo ./build.sh` 重新部署生效。
- 验证：`build.sh` 内置真实链路回归 `6 passed`，SSO 回调可正常换取应用 token。

### 5. 卧底/狼人真实流程 E2E 长时卡住

- 现象：真实链路测试在投票或等待阶段偶发长时间无进展。
- 修复：
  - 测试编排增加阶段推进日志、无进展检测与自动 reload 恢复；
  - 投票阶段增加目标选择与提交重试；
  - 对局日志区域增加稳定 `data-testid`。
- 验证：`tests/real-full-e2e.spec.ts` 全场景通过（单人/三人 + AI，卧底/狼人）。

## 观察项（非阻塞）

- 前端构建存在 chunk 体积告警（`index-*.js > 500kB`），不影响当前功能，可后续做按路由拆包优化。

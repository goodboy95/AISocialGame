# 问题跟踪（更新于 2026-02-23）

## 当前状态

当前无阻塞发布的已知问题。

## 已解决（本轮）

### 1. SSO 回调在跨网络场景下失败

- 现象：`/api/auth/sso-callback` 偶发返回会话校验失败。
- 根因：容器内通过 Consul 解析 gRPC 地址不稳定。
- 修复：
  - `AuthService` 优先使用 `app.sso.user-service-base-url`（域名）
  - `env.txt` 默认 gRPC 地址改为静态域名 `static://*.seekerhut.com:*`
  - `docker-compose.yml` 增加对应 `extra_hosts`
- 验证：真实账号 SSO + 回调 + 钱包链路通过。

### 2. `build.sh` 在 sudo 场景下测试失败

- 现象：`sudo build.sh` 中 Maven 测试误用运行时数据源环境变量。
- 修复：测试阶段显式 `env -u SPRING_DATASOURCE_*`，维持 H2 测试配置。
- 验证：`sudo build.sh` 完整通过。

### 3. 真实 E2E 断言不稳定

- 现象：兑换历史存在多条记录时，Playwright strict mode 报错。
- 修复：`frontend/tests/real-flow.spec.ts` 中相关断言改为 `.first()`。
- 验证：`test:e2e:real` 稳定通过。

## 观察项（非阻塞）

- 前端构建存在 chunk 体积告警（`index-*.js > 500kB`），当前不影响功能与部署，可后续做按路由拆包优化。

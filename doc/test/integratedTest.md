# 集成测试记录（2026-03-04）

## 1. 执行环境与前置

- 部署命令：`sudo ./build.sh`
- 测试域名：`https://aisocialgame.seekerhut.com`
- 验证账号：`goodboy95 / superhs2cr1`
- 严格 gRPC 鉴权变量已注入：
  - `APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN`
  - `APP_EXTERNAL_PAYSERVICE_JWT`
  - `APP_EXTERNAL_AISERVICE_HMAC_CALLER`
  - `APP_EXTERNAL_AISERVICE_HMAC_SECRET`
- 说明：`APP_EXTERNAL_PAYSERVICE_JWT` 有过期时间，若过期会导致 `/api/auth/sso-callback` 返回 `401 Invalid token`。

## 2. 本次关键修复

### 2.1 真实链路 E2E 完整覆盖

新增并接入 `frontend/tests/real-full-e2e.spec.ts`，`build.sh` 现在会执行：

- `tests/real-flow.spec.ts`
- `tests/real-full-e2e.spec.ts`

### 2.2 卧底/狼人流程稳定性修复

在 E2E 编排中补充：

- 阶段推进日志与无进展检测
- 卡住自动刷新恢复
- 投票阶段目标选择与提交重试

## 3. build.sh 结果

`build.sh` 全流程成功：

1. 后端 `mvn clean test package` 成功（`Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`）
2. 前端构建成功
3. Docker Compose 重建成功
4. 健康检查通过
5. 自动全量迁移通过：
   - `{"scanned":23,"success":23,"failed":0,"batchSize":100,"failures":[]}`
6. Playwright 冒烟 + 真实链路全部通过

## 4. Playwright 结果

### 4.1 冒烟/功能回归

执行：

- `tests/basic.spec.ts`
- `tests/full-flow.spec.ts`
- `tests/v2-features.spec.ts`

结果：`3 passed`

### 4.2 真实链路（REAL_E2E=1）

执行：

- `tests/real-flow.spec.ts`
- `tests/real-full-e2e.spec.ts`

结果：`6 passed`

覆盖点：

- 真实 SSO 登录 + 应用 token 换取
- 钱包签到、兑换码创建与兑换、通用转专属
- 社区发帖、AI 对话、排行/成就/回放/百科页面可达
- 谁是卧底：
  - 单人玩家 + 其他 AI（完整到结算）
  - 3 人玩家 + 其他 AI（完整到结算，含观战）
- 狼人杀：
  - 单人玩家 + 其他 AI（完整到结算）
  - 3 人玩家 + 其他 AI（完整到结算）

## 5. 抽检

- `https://aisocialgame.seekerhut.com` 可访问
- `http://127.0.0.1:11031/actuator/health` 返回 `UP`
- `POST /api/auth/sso-callback` 可正常换取应用 token（修复过期 JWT 后）

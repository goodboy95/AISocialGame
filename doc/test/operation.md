# 系统功能与操作步骤（2026-02-25）

## 0. 入口与端口

1. 测试域名入口：`https://aisocialgame.seekerhut.com`
2. 前端直连入口：`http://127.0.0.1:11030`
3. 后端直连入口：`http://127.0.0.1:11031/api`
4. 健康检查：`http://127.0.0.1:11031/actuator/health`
5. WebSocket：`wss://aisocialgame.seekerhut.com/ws`

## 1. 部署执行

1. 注入三服务 gRPC 鉴权环境变量（四项必须）。
2. 执行 `sudo ./build.sh`。
3. 期望输出包含：
   - 后端测试通过
   - 前端构建完成
   - 容器重建完成
   - `Run full credit migration` 且 `failed=0`
   - Playwright `3 passed` + `1 passed`

## 2. SSO 登录/注册跳转

1. 首页点击“登录”。
2. 期望请求 `GET /api/auth/sso/login?state=<一次性状态>` 并返回 `302`。
3. 期望 `Location` 指向 user-service，且包含：
   - `redirect=https://aisocialgame.seekerhut.com/sso/callback`
   - `state=<原始状态值>`
4. 注册同理，通过 `GET /api/auth/sso/register?state=...`。
5. `state` 非法时返回 `400`。

## 3. SSO 回调安全校验

1. 打开 `https://aisocialgame.seekerhut.com/sso/callback` 并伪造 `state`。
2. 期望前端提示 `SSO 状态校验失败，请重新登录`。
3. 期望回到首页且不会建立本地登录态。

## 4. 钱包与积分

1. 登录后进入 `/profile?tab=wallet`。
2. 校验余额区展示：
   - `publicPermanentTokens`
   - `projectTempTokens`
   - `projectPermanentTokens`
3. 执行 `100` 通用积分兑换专属积分：
   - 接口：`POST /api/wallet/exchange/public-to-project`
   - 期望：兑换成功，余额更新，记录写入。
4. 查看 `通用积分兑换记录`：
   - 期望能看到 `兑换数量：100`
   - 期望展示 `通用积分：<前> -> <后>` 与 `项目永久积分：<前> -> <后>`。

## 5. 管理后台

1. 管理员登录：`admin/admin123`（可由环境变量覆盖）。
2. 进入积分管理页执行：
   - `migrate-user`
   - `migrate-all`
   - `adjust`
   - `reversal`
   - `redeem-codes`
3. 期望 `migrate-all` 返回 `scanned/success/failed` 与失败详情。

## 6. AI 消耗记录

1. 用户执行 AI 对话（`/api/ai/chat` 或 `/api/ai/chat/stream`）。
2. 期望本地账本新增 `CONSUME` 流水。
3. 期望专属积分按 token 使用量扣减（临时优先）。

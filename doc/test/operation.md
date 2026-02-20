# 系统功能与操作步骤（2026-02-19）

## 0. 入口与端口
1. 测试域名入口：`http://aisocialgame.seekerhut.com`。
2. 前端直连入口：`http://127.0.0.1:11030`。
3. 后端直连入口：`http://127.0.0.1:11031/api`。
4. 健康检查：`http://127.0.0.1:11031/actuator/health`。
5. 实时通道：`ws://<host>/ws`（通过测试域名统一访问）。

## 1. SSO 登录/注册跳转（本次修复重点）
1. 打开首页，点击“登录”按钮。
2. 期望浏览器跳转到后端入口 `GET /api/auth/sso/login?state=<一次性状态>`，后端返回 `302`。
3. 期望最终到达 user-service 登录页，URL 中包含：
   - `redirect=http://aisocialgame.seekerhut.com/sso/callback`
   - `state=<与请求一致的状态值>`
4. 返回首页后点击“注册”按钮，期望通过 `GET /api/auth/sso/register?state=<一次性状态>` 完成同样链路。
5. 接口校验点：
   - `state` 非法（例如 `bad`）时，`/api/auth/sso/login` 返回 `400` 与 `SSO state 格式不合法`。

## 2. SSO 回调 state 防重放校验
1. 打开 `http://aisocialgame.seekerhut.com/sso/callback` 并携带伪造 `state` 参数（与会话缓存不一致）。
2. 期望页面提示 `SSO 状态校验失败，请重新登录`。
3. 期望页面被重定向回首页 `/`，且不会建立本地登录态。

## 3. v2 导航与社交入口
1. 顶栏可见：`成就`、`回放`、`百科`、`快速开始`、`好友`。
2. 路由验证：
   - `/achievements`
   - `/replays`
   - `/guide`
3. 期望：页面可访问、无阻断错误，好友抽屉与快速匹配弹窗可正常打开。

## 4. 大厅到房间主路径
1. 打开首页 `/`，确认“热门游戏”列表可渲染。
2. 进入卧底房：`/room/undercover/{roomId}`。
3. 进入狼人房：`/room/werewolf/{roomId}`。
4. 期望：房间内可见阶段、玩家区、操作区、日志区、聊天区。

## 5. 房间聊天路径
1. 在卧底房聊天框输入并提交文本：`我先给关键词边界，等大家发言后再锁定卧底。`
2. 在狼人房聊天框输入并提交文本：`白天先记冲突，夜晚再结合身份做判断。`
3. 期望：
   - WebSocket 已连接时，聊天消息出现在聊天区。
   - 连接断开时，页面提示重连中且发送失败有提示。

## 6. 专属积分体系与兑换链路（2026-02-20）
1. 用户登录后进入钱包面板，查看余额字段：
   - `projectTempTokens`
   - `projectPermanentTokens`
   - `publicPermanentTokens`
2. 执行签到（`POST /api/wallet/checkin`）后刷新余额：
   - 期望：本地专属积分增加；同日重复签到返回已签到，不重复发放。
3. 使用项目兑换码（`POST /api/wallet/redeem`）：
   - 期望：成功时发放本地专属积分并记录流水；
   - 错误码连续失败达到风控阈值后返回 `429`。
4. 执行通用转专属（`POST /api/wallet/exchange/public-to-project`）：
   - 入参：`amount`、可选 `requestId`；
   - 期望：按 `1:1` 将 payService 通用积分兑换为本地专属积分并记录流水；
   - 同 `requestId` 重放返回同一成功结果，不重复扣减。
5. 管理端客服操作（需 `X-Admin-Token`）：
   - `POST /api/admin/billing/adjust`：补发/扣回临时或永久专属积分；
   - `POST /api/admin/billing/reversal`：按原始 `requestId` 冲正；
   - `POST /api/admin/billing/migrate-user`：执行一次性迁移并冻结。
6. 流水核验：
   - 用户侧：`GET /api/wallet/ledger`
   - 管理侧：`GET /api/admin/billing/ledger?userId=<id>`
   - 期望：可见签到、兑换码、通用转专属、客服调账、冲正、迁移初始化等本地流水类型。

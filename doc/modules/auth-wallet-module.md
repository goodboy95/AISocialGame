# 认证与钱包模块说明（v1.6）

## 模块职责

- 认证：统一通过 user-service SSO 登录/注册，本项目仅负责回调校验与本地会话建立。
- 钱包：项目专属积分由本地账本维护，通用积分通过 pay-service 读取与兑换。
- 扣费：AI 调用成功后，仅扣减本地项目专属积分（临时优先，永久补足）。

## 关键实现

- 后端
  - `AuthController` / `AuthService`
    - `GET /api/auth/sso/login`
    - `GET /api/auth/sso/register`
    - `POST /api/auth/sso-callback`
    - `GET /api/auth/me`
  - `WalletController` / `WalletService`
    - `GET/POST /api/wallet/*`
- `ProjectCreditService`
    - 作为本地积分门面，保留账户初始化、签到、兑换码、通用转专属、消耗、管理员调账/冲正/迁移入口
    - 通用转专属采用 PENDING -> pay-service -> SUCCESS/FAILED 状态机，远程 gRPC 不在本地数据库事务内执行。
- `CreditLedgerService`
    - 统一账本写入、账本查询、消耗记录查询和 metadata JSON 编解码。
- 前端
  - `useAuth`：生成一次性 `state` 并跳转 SSO
  - `SsoCallback`：从 query 读取 `code/state`，校验 `state` 后调用后端回调
  - `Profile` + `WalletPanel`：余额、兑换、历史记录

## SSO 地址解析策略

`AuthService` 的 user-service 地址解析顺序：

1. 使用 `app.sso.user-service-base-url`（本地默认 `https://localuserservice.testhut.top`）
2. 当前版本不使用 Consul 服务发现；如地址变化，应通过环境变量 `SSO_USER_SERVICE_BASE_URL` 调整。

## 首次登录初始化

user-service 回跳 `/sso/callback?code=...&state=...` 后，前端调用 `POST /api/auth/sso-callback`。后端先用 `code + redirect` 调 user-service `POST /sso/token` 换取 `accessToken/userId/username/sessionId`，再在首次登录时执行：

- `billingGrpcClient.ensureUserInitialized(...)`
- `projectCreditService.ensureAccountInitialized(...)`

确保首次登录用户可直接使用钱包与业务能力。

## 钱包能力清单

- 每日签到：`POST /api/wallet/checkin`
- 签到状态：`GET /api/wallet/checkin-status`
- 余额：`GET /api/wallet/balance`
- 消耗记录：`GET /api/wallet/usage-records`
- 本地账本：`GET /api/wallet/ledger`
- 兑换码：`POST /api/wallet/redeem`
- 通用转专属：`POST /api/wallet/exchange/public-to-project`
- 兑换码历史：`GET /api/wallet/redemption-history`
- 通用转专属历史（含兑换前后余额）：`GET /api/wallet/exchange-history`

## 登录边界

- 本项目不提供本地账号密码注册/登录页面。
- 用户所有注册登录流程均在 user-service SSO 页面完成。
- 对局、观战状态、WebSocket 和 AI 调用均要求登录用户身份。
- 前端用户 token 仍使用 `sessionStorage`；管理员认证使用 HttpOnly、Secure、
  SameSite=Strict 的服务端会话 cookie，前端不读取或保存管理员 token。
- 业务控制器通过 `@CurrentUser` 参数解析器获取登录用户，避免新增接口漏掉登录校验。

## 管理员 session

- 管理员 session 保存在数据库并绑定认证策略、凭据版本、密码哈希指纹和认证时间；
  Redis 用于登录、验证码等多维限流，任一依赖异常均拒绝认证。
- 管理端业务控制器通过 `@CurrentAdmin` 参数解析器读取服务端认证后的 principal，
  获取操作者用户名，用于调账、冲正、迁移等审计字段。
- TOTP 模式下，所有管理端写操作还必须消费 60 秒有效、单次使用且绑定操作路径的证明。

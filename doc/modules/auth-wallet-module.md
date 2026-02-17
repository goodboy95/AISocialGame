# 认证与钱包模块说明（v1.2）

## 模块职责

- 认证链路：前端统一走 user-service SSO 页面，回调后由后端建立本地会话。
- 钱包链路：提供签到、余额、消费记录、账本明细、兑换码兑换、兑换历史能力。
- 统一会话：业务接口通过 `X-Auth-Token` 识别当前用户并复用远端 `session_id`。

## 关键实现

- 后端：
  - `AuthController` / `AuthService`：`/sso-url`、`/sso-callback`、`/me`
  - `WalletController` / `WalletService`：`/api/wallet/*`
  - `ConsulHttpServiceDiscovery`：解析 `aienie-userservice-http` 实例地址
- 前端：
  - `useAuth`：`redirectToSsoLogin`、`ssoCallback` 会话处理
  - `SsoCallback` 页面：解析 hash 参数并调用后端回调
  - `Profile` + `WalletPanel`：钱包入口与操作页面

## 关键配置

- `app.consul.address`：Consul HTTP 地址
- `app.sso.user-service-name`：user-service HTTP 服务名
- `app.sso.callback-url`：SSO 回跳地址
- `SERVER_PORT`：后端端口（默认 `20030`）

## 主要流程

1. 前端点击登录，调用 `/api/auth/sso-url` 获取跳转地址。
2. 浏览器跳转 user-service，登录后回调到 `/sso/callback#...`。
3. 前端调用 `/api/auth/sso-callback`，后端校验会话并签发本地 token。
4. 钱包页面携带 `X-Auth-Token` 调用 `/api/wallet/*` 完成签到、查询、兑换等操作。

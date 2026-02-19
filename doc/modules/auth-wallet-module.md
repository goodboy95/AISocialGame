# 认证与钱包模块说明（v1.2）

## 模块职责

- 认证链路：前端统一走 user-service SSO 页面，回调后由后端建立本地会话。
- 钱包链路：提供签到、余额、消费记录、账本明细、兑换码兑换、兑换历史能力。
- 统一会话：业务接口通过 `X-Auth-Token` 识别当前用户并复用远端 `session_id`。

## 关键实现

- 后端：
  - `AuthController` / `AuthService`：`/sso/login`、`/sso/register`、`/sso-callback`、`/me`
  - `WalletController` / `WalletService`：`/api/wallet/*`
  - `ConsulHttpServiceDiscovery`：解析 `aienie-userservice-http` 实例地址
- 前端：
  - `useAuth`：生成一次性 `state`，跳转 `/api/auth/sso/login|register`
  - `SsoCallback` 页面：解析 hash 参数，强校验 `state` 后调用后端回调
  - `Profile` + `WalletPanel`：钱包入口与操作页面

## 关键配置

- `app.consul.address`：Consul HTTP 地址
- `app.sso.user-service-name`：user-service HTTP 服务名
- `app.sso.callback-url`：SSO 回跳地址
- `SERVER_PORT`：后端端口（应用默认 `20030`，项目脚本默认 `11031`）

## 主要流程

1. 前端点击登录/注册，生成一次性 `state`。
2. 前端生成一次性 `state`，访问 `/api/auth/sso/login?state=...`（或 `/sso/register`）。
3. 后端 302 跳转 user-service 页面，用户完成登录/注册后回调到 `/sso/callback#...&state=...`。
4. 前端回调页严格比对 `state`，校验通过后调用 `/api/auth/sso-callback`。
5. 后端校验远端会话并签发本地 token；钱包页面再携带 `X-Auth-Token` 调用 `/api/wallet/*`。

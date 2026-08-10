# AdminAuthController

基址：`/api/admin/auth`。所有响应均使用 `Cache-Control: no-store`，认证成功只设置
`AISOCIAL_ADMIN_SESSION`（`HttpOnly; Secure; SameSite=Strict`）cookie，不返回浏览器可持久化 token。

## 策略与登录

- `GET /policy`：返回严格 OS 环境变量 `ENV` / `AUTH_MODE` 解析后的策略与绑定状态。
- `POST /login`：先验证 BCrypt 管理员密码。`local/password` 直接建立会话；TOTP 模式返回 HTTP 202
  `TOTP_REQUIRED` 或 `ENROLLMENT_REQUIRED` challenge，验证 TOTP 前不会建立会话。
- `POST /totp/verify`：用密码阶段签发的 challenge 和 6 位 TOTP 完成登录。
- `POST /enrollment/start`、`POST /enrollment/confirm`：首次绑定。密钥只在 start 响应中出现，确认后以
  AES-256-GCM + 版本化 keyring 加密保存；恢复码只显示一次。
- `POST /recovery/verify`：恢复码只能在密码已验证的 challenge 上使用，成功后只建立 `RECOVERY` 会话。
  该会话只能调用 `rebind/start`、`rebind/confirm`、`me` 和 `logout`；重绑成功后旧会话与旧恢复码失效。

## 会话与安全操作

- `GET /me`、`POST /logout`：查询或撤销服务端会话。
- `POST /recovery-codes/regenerate`：完整会话再次验证 TOTP 后重新生成恢复码。
- `POST /operation/verify`：验证高危操作 challenge，签发 60 秒、一次性、绑定用户/会话/方法/目标的 proof。
- 所有管理员写请求必须携带受信任的精确 `Origin`。TOTP 模式下，管理员业务写请求先返回 HTTP 428
  `ADMIN_OPERATION_PROOF_REQUIRED`；验证后以 `X-Admin-Operation-Proof` 重试一次。`local/password` 模式跳过 step-up。

认证 challenge、会话、恢复码、proof 与审计均存数据库；Redis 限流或数据库不可用时认证 fail closed。
日志和审计不得记录密码、TOTP、TOTP 密钥、恢复码、cookie、challenge 或 proof 原文。

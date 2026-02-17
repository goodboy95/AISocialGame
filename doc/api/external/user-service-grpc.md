# user-service gRPC 接口（外部依赖）

- 来源：gRPC Reflection + Consul 服务发现
- 获取时间：2026-02-16
- 说明：以下为本项目 v1.1 实际使用/运维相关接口。

## 服务
- `fireflychat.user.v1.UserAuthService`
  - `RegisterUser`
  - `LoginUser`
  - `ValidateSession`
- `fireflychat.user.v1.UserDirectoryService`
  - `GetUserBasic`
- `fireflychat.user.v1.UserBanService`
  - `GetBanStatus`
  - `BanUser`
  - `UnbanUser`

## 核心请求字段
- `RegisterUserRequest`: `request_id`, `username`, `email`, `password`, `display_name`, `avatar_url`, `ip_address`, `user_agent`
- `LoginUserRequest`: `request_id`, `username`, `password`, `ip_address`, `user_agent`
- `ValidateSessionRequest`: `user_id`, `session_id`
- `BanUserRequest`: `user_id`, `ban_type`, `reason`, `expires_at`, `operator_user_id`

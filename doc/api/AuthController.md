# AuthController 接口说明

基址：`/api/auth`

## POST /register
- 用途：通过 `user-service` 完成注册，并返回本系统 token + 用户资料 + 聚合积分。
- 请求体：
  - `username` (string, optional，空时默认取邮箱前缀)
  - `email` (string, required)
  - `password` (string, required, >=6)
  - `nickname` (string, required)
- 响应 201：
```json
{
  "token": "uuid",
  "user": {
    "id": "local-user-id",
    "externalUserId": 10001,
    "username": "demo_user",
    "nickname": "演示玩家",
    "email": "demo@example.com",
    "avatar": "https://...",
    "level": 1,
    "coins": 1200,
    "balance": {
      "publicPermanentTokens": 1000,
      "projectTempTokens": 100,
      "projectPermanentTokens": 100,
      "totalTokens": 1200,
      "projectTempExpiresAt": "2026-02-20T00:00:00Z"
    }
  }
}
```

## POST /login
- 用途：通过 `user-service` 登录并同步会话。
- 请求体：
  - `account` (string, required，支持用户名；可传邮箱，系统会尝试映射本地已同步用户名)
  - `password` (string, required)
- 响应 200：同 `AuthResponse`。

## GET /me
- 用途：根据 `X-Auth-Token` 校验远端会话并返回当前用户资料与积分。
- 请求头：`X-Auth-Token: <token>`
- 响应 200：`AuthUserView`
- 错误：401 未登录/会话无效。

# AuthController 接口说明

基址：`/api/auth`

## POST /register
- **用途**：注册新用户并下发 token。
- **请求体**
  - `email` (string, required)
  - `password` (string, required, >=6)
  - `nickname` (string, required)
- **响应 201**
```json
{
  "token": "uuid",
  "user": {
    "id": "uuid",
    "email": "string",
    "nickname": "string",
    "avatar": "url",
    "coins": 1000,
    "level": 1,
    "createdAt": "2025-11-25T00:29:35.592365",
    "updatedAt": "2025-11-25T00:29:35.592365"
  }
}
```
- **说明**：用户信息持久化在 MySQL，`password` 字段不会出现在响应中；登录 token 存储于 Redis，默认有效期 168 小时（`APP_AUTH_TOKEN_TTL_HOURS` 可调）。

## POST /login
- **用途**：用户登录。
- **请求体**：`email`, `password`
- **响应 200**：同上 `AuthResponse`。

## GET /me
- **用途**：根据 `X-Auth-Token` 读取当前用户信息。
- **请求头**：`X-Auth-Token: <token>`
- **响应 200**：`User` 对象。
- **错误**：401 未登录。*** End Patch

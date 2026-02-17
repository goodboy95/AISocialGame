# AdminAuthController 接口说明

基址：`/api/admin/auth`

## POST /login
- 用途：管理员登录，返回管理端 token。
- 请求体：
  - `username` (string, required)
  - `password` (string, required)
- 响应 200：`AdminAuthResponse`（`token/username/displayName`）。

## GET /me
- 用途：校验管理端 token。
- 请求头：`X-Admin-Token` (required)
- 响应 200：`AdminAuthResponse`
- 错误：401 token 缺失或过期。

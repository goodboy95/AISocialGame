# AdminUserController 接口说明

基址：`/api/admin/users`

## GET /{userId}
- 用途：按外部用户 ID 查询用户基础信息 + 封禁状态 + 积分。
- 请求头：`X-Admin-Token` (required)
- 响应 200：`AdminUserView`

## POST /{userId}/ban
- 用途：封禁用户（永久或临时）。
- 请求头：`X-Admin-Token` (required)
- 请求体：
  - `permanent` (boolean, optional, default true)
  - `expiresAt` (datetime, optional，临时封禁可传)
  - `reason` (string, required)
- 响应 200：`AdminUserView`

## POST /{userId}/unban
- 用途：解封用户。
- 请求头：`X-Admin-Token` (required)
- 请求体：
  - `reason` (string, required)
- 响应 200：`AdminUserView`

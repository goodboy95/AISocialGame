# AuthController 接口文档

- 基础路径：`/api/auth`
- 认证要求：除公开接口外，其余接口需在请求头 `Authorization: Bearer <access token>` 中携带登录后的访问令牌。
- 相关 DTO：`AuthDtos`（定义于 `backend/src/main/java/com/aisocialgame/backend/dto/AuthDtos.java`）。

## POST `/api/auth/register/`
- **说明**：注册新账号，创建后立即返回用户资料。
- **请求体**：
  ```json
  {
    "username": "string",
    "email": "string",
    "password": "string",
    "display_name": "string"
  }
  ```
- **返回**：`200 OK`，`UserProfile` 对象。
  - `id`：用户 ID。
  - `username`：用户名。
  - `email`：邮箱。
  - `display_name`：昵称。
  - `avatar`：头像地址（当前可能为空字符串）。
  - `bio`：个性签名（当前可能为空字符串）。
  - `is_admin`：是否管理员。
- **逻辑要点**：调用 `UserService.register` 创建账号，会校验唯一性并加密密码。

## POST `/api/auth/token/`
- **说明**：用户名密码登录，发放新的访问令牌与刷新令牌。
- **请求体**：
  ```json
  {
    "username": "string",
    "password": "string"
  }
  ```
- **返回**：`200 OK`，`TokenResponse` 对象。
  - `access`：访问令牌 JWT，后续访问受保护接口需携带。
  - `refresh`：刷新令牌 JWT。
- **逻辑要点**：`AuthService.login` 完成凭证校验，刷新令牌会写入存储用于失效控制。

## POST `/api/auth/token/refresh/`
- **说明**：使用刷新令牌换取新的访问/刷新令牌对。
- **请求体**：
  ```json
  {
    "refresh": "string"
  }
  ```
- **返回**：`200 OK`，`TokenResponse` 对象（字段同上）。
- **逻辑要点**：`AuthService.refresh` 校验刷新令牌是否有效，若过期或已失效将抛出异常返回 401。

## POST `/api/auth/logout/`
- **说明**：使刷新令牌失效，相当于退出登录。
- **请求体**：
  ```json
  {
    "refresh": "string"
  }
  ```
- **返回**：`204 No Content`。
- **逻辑要点**：`AuthService.logout` 会删除或标记刷新令牌，防止再次使用。

## GET `/api/auth/me/`
- **说明**：获取当前登录用户的资料。
- **鉴权**：需要访问令牌。
- **返回**：
  - 成功：`200 OK`，`UserProfile` 对象（字段同注册返回）。
  - 未登录：`401 Unauthorized`。
- **逻辑要点**：`AuthService.currentUser` 根据访问令牌解析出的用户上下文返回实体。

## GET `/api/auth/me/export/`
- **说明**：导出个人档案及房间参与历史等扩展信息。
- **鉴权**：需要访问令牌。
- **返回**：
  - 成功：`200 OK`，`UserExport` 对象。
    - `exported_at`：导出的时间戳（ISO-8601）。
    - `profile`：`UserProfile`，同上。
    - `memberships`：数组，历史加入的房间概况。
      - `roomId`、`roomName`、`roomCode`、`status`、`joinedAt`、`isHost`、`isAi`、`aiStyle`、`role`、`word`、`alive`。
    - `ownedRooms`：数组，个人创建房间列表。
      - `id`、`name`、`code`、`createdAt`、`status`。
    - `statistics`：统计信息。
      - `joinedRooms`：加入房间数。
      - `ownedRooms`：创建房间数。
  - 未登录：`401 Unauthorized`。
- **逻辑要点**：`UserService.exportUser` 汇总房间成员与创建记录，便于数据迁移或支持客服排查。

## DELETE `/api/auth/me/`
- **说明**：删除当前账号。
- **鉴权**：需要访问令牌。
- **返回**：
  - 成功：`204 No Content`。
  - 未登录：`401 Unauthorized`。
- **逻辑要点**：`UserService.deleteAccount` 清理账号及关联数据（如房间成员记录），适用于用户主动注销。

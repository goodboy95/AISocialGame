# AuthController

## 简介

- 职责：提供 SSO 跳转地址与 SSO 回调换取本地会话能力。
- 鉴权要求：`/sso-url`、`/sso-callback` 无需本地鉴权；`/me` 需要 `X-Auth-Token`。
- 基础路径：`/api/auth`

## 接口列表

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | /api/auth/sso-url | 获取 user-service 的登录/注册跳转地址 |
| POST | /api/auth/sso-callback | 处理 SSO 回调并建立本地会话 |
| GET | /api/auth/me | 获取当前登录用户信息 |

## 接口详情

### GET /api/auth/sso-url - 获取 SSO 跳转地址

**用途**：根据 Consul 服务发现结果拼接 user-service SSO 登录与注册 URL。

**鉴权**：无需鉴权

**请求参数**

Path params：无

Query params：无

Body：无

**返回值**

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| loginUrl | String | SSO 登录地址 | `http://user-service:19090/sso/login?...` |
| registerUrl | String | SSO 注册地址 | `http://user-service:19090/register?...` |

**示例请求**

```bash
curl -X GET "http://localhost:20030/api/auth/sso-url"
```

**示例响应**

```json
{
  "loginUrl": "http://user-service:19090/sso/login?redirect=http%3A%2F%2Flocalhost%3A10030%2Fsso%2Fcallback",
  "registerUrl": "http://user-service:19090/register?redirect=http%3A%2F%2Flocalhost%3A10030%2Fsso%2Fcallback"
}
```

**错误码/常见错误**

| 错误码 | 说明 |
|--------|------|
| 503 | Consul 无可用实例或服务发现失败 |

### POST /api/auth/sso-callback - 建立本地会话

**用途**：校验 SSO 会话并签发本系统 token。

**鉴权**：无需鉴权

**请求参数**

Path params：无

Query params：无

Body：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| accessToken | String | 是 | user-service 访问令牌 | `"xxx"` |
| userId | Long | 是 | 外部用户 ID | `1001` |
| username | String | 是 | 用户名 | `"demo_user"` |
| sessionId | String | 是 | user-service 会话 ID | `"sess-xxx"` |

**返回值**

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| token | String | 本地会话 token | `"local-token"` |
| user | Object | 当前用户信息 | `{...}` |

**示例请求**

```bash
curl -X POST "http://localhost:20030/api/auth/sso-callback" \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "remote-token",
    "userId": 1001,
    "username": "demo_user",
    "sessionId": "sess-001"
  }'
```

**示例响应**

```json
{
  "token": "local-token",
  "user": {
    "id": "a8f0c8f8-...",
    "externalUserId": 1001,
    "username": "demo_user",
    "nickname": "demo_user",
    "email": "demo@example.com",
    "avatar": "https://...",
    "level": 1,
    "coins": 0,
    "balance": {
      "publicPermanentTokens": 0,
      "projectTempTokens": 0,
      "projectPermanentTokens": 0,
      "totalTokens": 0
    }
  }
}
```

**错误码/常见错误**

| 错误码 | 说明 |
|--------|------|
| 400 | 参数缺失 |
| 401 | SSO 会话无效或已过期 |

### GET /api/auth/me - 获取当前用户信息

**用途**：通过本地 token 校验并返回用户信息与积分聚合快照。

**鉴权**：需要 `X-Auth-Token`

**请求参数**

Path params：无

Query params：无

Body：无

**返回值**

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| id | String | 本地用户 ID | `"a8f0c8f8-..."` |
| externalUserId | Long | 外部用户 ID | `1001` |
| nickname | String | 昵称 | `"demo_user"` |
| coins | Long | 总积分（聚合） | `1200` |
| balance | Object | 余额快照 | `{...}` |

**示例请求**

```bash
curl -X GET "http://localhost:20030/api/auth/me" \
  -H "X-Auth-Token: <token>"
```

**示例响应**

```json
{
  "id": "a8f0c8f8-...",
  "externalUserId": 1001,
  "username": "demo_user",
  "nickname": "demo_user",
  "email": "demo@example.com",
  "avatar": "https://...",
  "level": 1,
  "coins": 1200,
  "balance": {
    "publicPermanentTokens": 1000,
    "projectTempTokens": 100,
    "projectPermanentTokens": 100,
    "totalTokens": 1200
  }
}
```

**错误码/常见错误**

| 错误码 | 说明 |
|--------|------|
| 401 | 未登录或 token 失效 |

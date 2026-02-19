# AuthController

## 简介

- 职责：提供 SSO 入口重定向、SSO 回调换取本地会话能力。
- 鉴权要求：`/sso/login`、`/sso/register`、`/sso-callback` 无需本地鉴权；`/me` 需要 `X-Auth-Token`。
- 基础路径：`/api/auth`

## 接口列表

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | /api/auth/sso/login | 后端 302 跳转到 user-service 登录页 |
| GET | /api/auth/sso/register | 后端 302 跳转到 user-service 注册页 |
| POST | /api/auth/sso-callback | 处理 SSO 回调并建立本地会话 |
| GET | /api/auth/me | 获取当前登录用户信息 |

## 接口详情

### GET /api/auth/sso/login - 重定向到 SSO 登录页

**用途**：由后端拼接 user-service 登录地址并返回 `302`，浏览器直接跳转。

**鉴权**：无需鉴权

**请求参数**

Path params：无

Query params：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| state | String | 是 | 一次性状态参数（16~128 位，`[A-Za-z0-9_-]`） | `"f3f5d4...2ab1"` |

Body：无

**返回值**

- HTTP 状态：`302 Found`
- 响应头：`Location: http://<user-service>/sso/login?redirect=<callback>&state=<state>`

**示例请求**

```bash
curl -i -X GET "http://localhost:11031/api/auth/sso/login?state=2f4b47e894fd4d17b09a7f6401896f4a"
```

**错误码/常见错误**

| 错误码 | 说明 |
|--------|------|
| 400 | `state` 缺失或格式非法 |
| 503 | Consul 无可用实例或服务发现失败 |

### GET /api/auth/sso/register - 重定向到 SSO 注册页

**用途**：由后端拼接 user-service 注册地址并返回 `302`，浏览器直接跳转。

**鉴权**：无需鉴权

**请求参数**

Path params：无

Query params：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| state | String | 是 | 一次性状态参数（16~128 位，`[A-Za-z0-9_-]`） | `"f3f5d4...2ab1"` |

Body：无

**返回值**

- HTTP 状态：`302 Found`
- 响应头：`Location: http://<user-service>/register?redirect=<callback>&state=<state>`

**示例请求**

```bash
curl -i -X GET "http://localhost:11031/api/auth/sso/register?state=2f4b47e894fd4d17b09a7f6401896f4a"
```

**错误码/常见错误**

| 错误码 | 说明 |
|--------|------|
| 400 | `state` 缺失或格式非法 |
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
curl -X POST "http://localhost:11031/api/auth/sso-callback" \
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
curl -X GET "http://localhost:11031/api/auth/me" \
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

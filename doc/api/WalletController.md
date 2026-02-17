# WalletController

## 简介

- 职责：提供钱包能力（签到、余额、消费记录、账本、兑换码、兑换历史）。
- 鉴权要求：全部接口需要 `X-Auth-Token`。
- 基础路径：`/api/wallet`

## 接口列表

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | /api/wallet/checkin | 每日签到 |
| GET | /api/wallet/checkin-status | 查询签到状态 |
| GET | /api/wallet/balance | 查询余额 |
| GET | /api/wallet/usage-records | 查询消费记录分页 |
| GET | /api/wallet/ledger | 查询账本分页 |
| POST | /api/wallet/redeem | 兑换码兑换 |
| GET | /api/wallet/redemption-history | 查询兑换历史分页 |

## 接口详情

### POST /api/wallet/checkin - 每日签到

**用途**：触发每日签到并返回签到奖励结果。

**鉴权**：需要 `X-Auth-Token`

**请求参数**

Path params：无

Query params：无

Body：无

**返回值**

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| success | Boolean | 是否成功 | `true` |
| tokensGranted | Long | 本次发放积分 | `100` |
| alreadyCheckedIn | Boolean | 今日是否已签到 | `false` |
| errorMessage | String | 失败信息 | `""` |
| balance | Object | 最新余额 | `{...}` |

**示例请求**

```bash
curl -X POST "http://localhost:20030/api/wallet/checkin" \
  -H "X-Auth-Token: <token>"
```

**示例响应**

```json
{
  "success": true,
  "tokensGranted": 100,
  "alreadyCheckedIn": false,
  "errorMessage": "",
  "balance": {
    "publicPermanentTokens": 300,
    "projectTempTokens": 100,
    "projectPermanentTokens": 900,
    "totalTokens": 1300
  }
}
```

**错误码/常见错误**

| 错误码 | 说明 |
|--------|------|
| 401 | 未登录 |
| 502 | 积分服务调用失败 |

### GET /api/wallet/checkin-status - 查询签到状态

**用途**：查询当日签到状态。

**鉴权**：需要 `X-Auth-Token`

**请求参数**

Path params：无

Query params：无

Body：无

**返回值**

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| checkedInToday | Boolean | 今日是否已签到 | `true` |
| lastCheckinDate | String | 最近签到时间 | `"2026-02-17T00:00:00Z"` |
| tokensGrantedToday | Long | 今日签到奖励 | `100` |

**示例请求**

```bash
curl -X GET "http://localhost:20030/api/wallet/checkin-status" \
  -H "X-Auth-Token: <token>"
```

**示例响应**

```json
{
  "checkedInToday": true,
  "lastCheckinDate": "2026-02-17T00:00:00Z",
  "tokensGrantedToday": 100
}
```

**错误码/常见错误**

| 错误码 | 说明 |
|--------|------|
| 401 | 未登录 |

### GET /api/wallet/balance - 查询余额

**用途**：返回公共积分与项目积分聚合。

**鉴权**：需要 `X-Auth-Token`

**请求参数**

Path params：无

Query params：无

Body：无

**返回值**

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| publicPermanentTokens | Long | 公共永久积分 | `300` |
| projectTempTokens | Long | 项目临时积分 | `100` |
| projectPermanentTokens | Long | 项目永久积分 | `900` |
| totalTokens | Long | 总积分 | `1300` |
| projectTempExpiresAt | String | 临时积分过期时间 | `"2026-02-20T00:00:00Z"` |

**示例请求**

```bash
curl -X GET "http://localhost:20030/api/wallet/balance" \
  -H "X-Auth-Token: <token>"
```

**示例响应**

```json
{
  "publicPermanentTokens": 300,
  "projectTempTokens": 100,
  "projectPermanentTokens": 900,
  "totalTokens": 1300
}
```

**错误码/常见错误**

| 错误码 | 说明 |
|--------|------|
| 401 | 未登录 |

### GET /api/wallet/usage-records - 查询消费记录分页

**用途**：查询用户消费记录分页。

**鉴权**：需要 `X-Auth-Token`

**请求参数**

Path params：无

Query params：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| page | int | 否 | 页码，默认 1 | `1` |
| size | int | 否 | 页大小，默认 20 | `20` |

Body：无

**返回值**

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| items | Array | 消费记录列表 | `[{...}]` |
| page | int | 当前页 | `1` |
| size | int | 页大小 | `20` |
| total | Long | 总数 | `42` |

**示例请求**

```bash
curl -X GET "http://localhost:20030/api/wallet/usage-records?page=1&size=20" \
  -H "X-Auth-Token: <token>"
```

**示例响应**

```json
{
  "items": [
    {
      "requestId": "req-001",
      "modelKey": "gpt-4o-mini",
      "promptTokens": 10,
      "completionTokens": 20,
      "billedTokens": 30,
      "createdAt": "2026-02-17T08:00:00Z"
    }
  ],
  "page": 1,
  "size": 20,
  "total": 1
}
```

**错误码/常见错误**

| 错误码 | 说明 |
|--------|------|
| 401 | 未登录 |

### GET /api/wallet/ledger - 查询账本分页

**用途**：查询账本明细分页。

**鉴权**：需要 `X-Auth-Token`

**请求参数**

Path params：无

Query params：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| page | int | 否 | 页码，默认 1 | `1` |
| size | int | 否 | 页大小，默认 20 | `20` |

Body：无

**返回值**

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| items | Array | 账本条目列表 | `[{...}]` |
| page | int | 当前页 | `1` |
| size | int | 页大小 | `20` |
| total | Long | 总数 | `12` |

**示例请求**

```bash
curl -X GET "http://localhost:20030/api/wallet/ledger?page=1&size=20" \
  -H "X-Auth-Token: <token>"
```

**示例响应**

```json
{
  "items": [
    {
      "id": "1",
      "type": "CHECKIN",
      "tokens": 100,
      "reason": "CHECKIN",
      "createdAt": "2026-02-17T08:00:00Z"
    }
  ],
  "page": 1,
  "size": 20,
  "total": 1
}
```

**错误码/常见错误**

| 错误码 | 说明 |
|--------|------|
| 401 | 未登录 |

### POST /api/wallet/redeem - 兑换码兑换

**用途**：兑换码换取积分。

**鉴权**：需要 `X-Auth-Token`

**请求参数**

Path params：无

Query params：无

Body：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| code | String | 是 | 兑换码 | `"WELCOME-2026"` |

**返回值**

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| success | Boolean | 是否成功 | `true` |
| tokensGranted | Long | 发放积分 | `50` |
| creditType | String | 积分类型 | `"CREDIT_TYPE_PERMANENT"` |
| errorMessage | String | 失败信息 | `""` |
| balance | Object | 最新余额 | `{...}` |

**示例请求**

```bash
curl -X POST "http://localhost:20030/api/wallet/redeem" \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: <token>" \
  -d '{"code":"WELCOME-2026"}'
```

**示例响应**

```json
{
  "success": true,
  "tokensGranted": 50,
  "creditType": "CREDIT_TYPE_PERMANENT",
  "errorMessage": "",
  "balance": {
    "publicPermanentTokens": 300,
    "projectTempTokens": 100,
    "projectPermanentTokens": 950,
    "totalTokens": 1350
  }
}
```

**错误码/常见错误**

| 错误码 | 说明 |
|--------|------|
| 400 | 兑换码不能为空 |
| 401 | 未登录 |
| 502 | 积分服务调用失败 |

### GET /api/wallet/redemption-history - 查询兑换历史分页

**用途**：查询兑换码历史记录分页。

**鉴权**：需要 `X-Auth-Token`

**请求参数**

Path params：无

Query params：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| page | int | 否 | 页码，默认 1 | `1` |
| size | int | 否 | 页大小，默认 20 | `20` |

Body：无

**返回值**

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| items | Array | 兑换记录列表 | `[{...}]` |
| page | int | 当前页 | `1` |
| size | int | 页大小 | `20` |
| total | Long | 总数 | `5` |

**示例请求**

```bash
curl -X GET "http://localhost:20030/api/wallet/redemption-history?page=1&size=20" \
  -H "X-Auth-Token: <token>"
```

**示例响应**

```json
{
  "items": [
    {
      "code": "WELCOME-2026",
      "tokensGranted": 50,
      "creditType": "CREDIT_TYPE_PERMANENT",
      "redeemedAt": "2026-02-17T08:00:00Z"
    }
  ],
  "page": 1,
  "size": 20,
  "total": 1
}
```

**错误码/常见错误**

| 错误码 | 说明 |
|--------|------|
| 401 | 未登录 |

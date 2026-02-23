# WalletController

基址：`/api/wallet`  
鉴权：全部接口要求 `X-Auth-Token`

## 接口列表

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/wallet/checkin` | 每日签到，发放本地专属积分 |
| GET | `/api/wallet/checkin-status` | 查询签到状态 |
| GET | `/api/wallet/balance` | 查询余额（通用 + 本地专属） |
| GET | `/api/wallet/usage-records` | 查询消费记录分页（本地账本口径） |
| GET | `/api/wallet/ledger` | 查询本地积分流水分页 |
| POST | `/api/wallet/redeem` | 使用本项目兑换码 |
| POST | `/api/wallet/exchange/public-to-project` | 通用积分按 1:1 兑换专属积分 |
| GET | `/api/wallet/redemption-history` | 查询兑换码兑换历史 |
| GET | `/api/wallet/exchange-history` | 查询通用转专属历史（含兑换前后余额） |

## 关键接口说明

### POST `/api/wallet/exchange/public-to-project`

- Body
  - `amount` (Long, required, `>=1`)
  - `requestId` (String, optional, 幂等键)
- 响应
  - `success` (Boolean)
  - `requestId` (String)
  - `exchangedTokens` (Long)
  - `balance` (`BalanceView`)
- 失败码
  - `400` 参数不合法 / 超出日限额 / requestId 已失败
  - `401` 未登录
  - `409` requestId 正在处理中

### GET `/api/wallet/exchange-history`

- Query
  - `page` (int, optional, default `1`)
  - `size` (int, optional, default `20`)
- 响应：`PagedResponse<ExchangeHistoryView>`
- `ExchangeHistoryView` 字段
  - `requestId`
  - `exchangedTokens`
  - `publicBefore`
  - `publicAfter`
  - `projectPermanentBefore`
  - `projectPermanentAfter`
  - `createdAt` (ISO-8601)

### GET `/api/wallet/ledger`

返回本地不可变流水，覆盖签到、兑换码、通用转专属、客服调账、冲正、迁移初始化、AI 消耗等类型。

## BalanceView 字段

| 字段 | 含义 |
|---|---|
| `publicPermanentTokens` | 通用积分（pay-service） |
| `projectTempTokens` | 项目临时专属积分（本地） |
| `projectPermanentTokens` | 项目永久专属积分（本地） |
| `projectTempExpiresAt` | 项目临时积分过期时间 |
| `totalTokens` | 三类积分聚合总和 |

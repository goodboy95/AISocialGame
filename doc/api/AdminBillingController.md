# AdminBillingController 接口说明

基址：`/api/admin/billing`  
鉴权：全部接口要求 `X-Admin-Token`

## GET `/balance`
- 用途：查询用户余额（本地专属积分 + 通用积分快照）
- Query：`userId` (long, required)
- 响应：`BalanceView`

## GET `/ledger`
- 用途：查询本地积分流水
- Query：
  - `userId` (long, required)
  - `page` (int, optional, default `1`)
  - `size` (int, optional, default `20`)
- 响应：`AdminLedgerPageResponse`

## POST `/adjust`
- 用途：客服补发/扣回积分
- Body：
  - `userId` (long, required)
  - `deltaTemp` (long, optional，可负)
  - `deltaPermanent` (long, optional，可负)
  - `reason` (string, required)
  - `requestId` (string, optional，幂等键)
- 响应：`BalanceView`

## POST `/reversal`
- 用途：按原始 `requestId` 冲正一笔流水
- Body：
  - `userId` (long, required)
  - `originalRequestId` (string, required)
  - `reason` (string, required)
- 响应：`BalanceView`
- 失败：
  - 原始流水不存在
  - 已冲正
  - 冲正后余额会为负

## POST `/migrate-user`
- 用途：按用户将 payService 项目积分快照迁移到本地（幂等）
- Body：
  - `userId` (long, required)
- 响应：`BalanceView`

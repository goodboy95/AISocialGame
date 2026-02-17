# AdminBillingController 接口说明

基址：`/api/admin/billing`

## GET /balance
- 用途：按用户查询聚合余额。
- 请求头：`X-Admin-Token` (required)
- 查询参数：`userId` (long, required)
- 响应 200：`BalanceView`

## GET /ledger
- 用途：按用户查询积分流水。
- 请求头：`X-Admin-Token` (required)
- 查询参数：
  - `userId` (long, required)
  - `page` (int, optional, default 1)
  - `size` (int, optional, default 20)
- 响应 200：`AdminLedgerPageResponse`

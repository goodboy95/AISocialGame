# pay-service gRPC 接口（外部依赖）

- 来源：gRPC Reflection + Consul 服务发现
- 获取时间：2026-02-16

## 服务
- `fireflychat.billing.v1.BillingBalanceService`
  - `GetProjectBalance`
  - `GetPublicBalance`
- `fireflychat.billing.v1.BillingConversionService`
  - `ConvertPublicToProject`
- `fireflychat.billing.v1.BillingQueryService`
  - `ListLedgerEntries`

## 核心请求字段
- `GetProjectBalanceRequest`: `project_key`, `user_id`
- `GetPublicBalanceRequest`: `user_id`
- `ConvertPublicToProjectRequest`: `request_id`, `project_key`, `user_id`, `tokens`
- `ListLedgerEntriesRequest`: `user_id`, `project_key`, `page`, `size`

## 核心响应字段
- `ProjectBalance`: `temp_tokens`, `temp_expires_at`, `permanent_tokens`
- `GetPublicBalanceResponse`: `public_permanent_tokens`
- `ConvertPublicToProjectResponse`: `public_permanent_tokens`, `project_balance`
- `LedgerEntry`: `type`, `token_delta_temp`, `token_delta_permanent`, `token_delta_public`, `created_at`

# pay-service gRPC 接口（外部依赖）

> 更新时间：2026-02-24

## 服务名

- Consul：`aienie-payservice-grpc`
- gRPC 服务：
  - `fireflychat.billing.v1.BillingBalanceService`
  - `fireflychat.billing.v1.BillingConversionService`
  - `fireflychat.billing.v1.BillingCheckinService`
  - `fireflychat.billing.v1.BillingRedeemCodeService`
  - `fireflychat.billing.v1.BillingQueryService`
  - `fireflychat.billing.v1.BillingOnboardingService`

## 本项目使用的方法

- `GetProjectBalance` / `GetPublicBalance`
- `ConvertPublicToProject`
- `Checkin` / `GetCheckinStatus`
- `RedeemCode` / `GetRedemptionHistory`
- `ListUsageRecords` / `ListLedgerEntries`
- `EnsureUserInitialized`

## 鉴权要求

pay-service 业务 gRPC 请求需携带：

- `authorization: Bearer <service_jwt>`

该 token 必须满足 pay-service 的 `issuer/audience/role/scopes` 约束。
本项目由 `BillingGrpcAuthClientInterceptor` 自动注入该 header。

# user-service gRPC 接口（外部依赖）

> 更新时间：2026-02-24

## 服务名

- Consul：`aienie-userservice-grpc`
- gRPC 服务：
  - `fireflychat.user.v1.UserAuthService`
  - `fireflychat.user.v1.UserDirectoryService`
  - `fireflychat.user.v1.UserBanService`

## 本项目使用的方法

- `UserAuthService/ValidateSession`
- `UserDirectoryService/GetUserBasic`
- `UserBanService/GetBanStatus`
- `UserBanService/BanUser`
- `UserBanService/UnbanUser`

## 鉴权要求

除少数公开方法外，user-service 受保护方法需携带：

- `x-internal-token: ${USERSERVICE_INTERNAL_GRPC_TOKEN}`

本项目由 `UserGrpcAuthClientInterceptor` 自动注入该 header。

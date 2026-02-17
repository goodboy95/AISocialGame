# gRPC 集成模块说明（v1.1）

## 目标
将 AISocialGame 后端从“本地账户/本地积分/本地 AI”升级为通过 gRPC 对接外部微服务。

## 组成
- `integration/consul/ConsulNameResolverProvider`：支持 `consul:///service-name` 地址解析。
- `integration/grpc/client/UserGrpcClient`：注册/登录/会话校验/封禁相关调用。
- `integration/grpc/client/BillingGrpcClient`：余额与账单流水查询。
- `integration/grpc/client/AiGrpcClient`：模型列表与对话补全。
- `src/main/proto/*`：`user/billing/ai` 协议定义与 Maven 自动生成代码。

## 关键行为
1. 启动时根据 `grpc.client.*.address` 连接外部服务（支持 `static://` 与 `consul:///`）。
2. `AuthService` 登录链路走 `user-service`，并把远端会话映射到本地 token。
3. `BalanceService` 聚合 `pay-service` 公共积分与项目积分。
4. `AiProxyService` 统一封装 `ai-service` 对话接口，供前台与后台复用。

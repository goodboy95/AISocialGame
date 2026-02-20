# gRPC 集成模块说明（v1.3）

## 目标
将 AISocialGame 后端通过 gRPC 对接外部用户、积分、AI 服务，并在 v1.2 增补钱包和 AI 扩展能力。

## 组成
- `integration/consul/ConsulNameResolverProvider`：支持 `consul:///service-name` 地址解析。
- `integration/consul/ConsulHttpServiceDiscovery`：通过 Consul HTTP 查询 user-service 的 SSO HTTP 地址。
- `integration/grpc/client/UserGrpcClient`：注册/登录/会话校验/封禁相关调用。
- `integration/grpc/client/BillingGrpcClient`：通用积分读取、通用转项目兑换、历史兼容查询、迁移快照读取。
- `integration/grpc/client/AiGrpcClient`：模型列表、对话补全、Embeddings、OCR。
- `src/main/proto/*`：`user/billing/ai` 协议定义与 Maven 自动生成代码。

## 关键行为
1. 启动时根据 `grpc.client.*.address` 连接外部服务（支持 `static://` 与 `consul:///`）。
2. `AuthService` 在 SSO 回调后校验外部会话，并映射为本地 token。
3. `WalletService` 通过本地 `ProjectCreditService` 处理签到/账本/兑换码；仅在通用转专属时调用 `BillingGrpcClient`。
4. `AiProxyService` 统一封装 `chat/embeddings/ocr`，对外暴露 HTTP 与 SSE 能力。

# gRPC 集成模块说明（v1.4）

> 更新时间：2026-02-23

## 目标

对接 user-service、pay-service、ai-service 三个外部服务，并支持 Consul 服务发现与静态域名地址两种模式。

## 组成

- `integration/grpc/client/UserGrpcClient`
  - 会话校验、用户信息、封禁状态
- `integration/grpc/client/BillingGrpcClient`
  - 通用积分读取、通用转专属、项目积分迁移、onboarding 初始化
- `integration/grpc/client/AiGrpcClient`
  - 模型列表、对话、embeddings、ocr
- `integration/consul/ConsulNameResolverProvider`
  - 支持 `consul:///service-name` 的 gRPC 名称解析
- `integration/consul/ConsulHttpServiceDiscovery`
  - SSO HTTP 地址发现（AuthService 的回退路径）

## 当前配置策略

- Consul 地址配置：`CONSUL_HTTP_ADDR`（默认 `http://192.168.5.141:60000`）
- Consul 服务名：
  - `USER_GRPC_SERVICE_NAME=aienie-userservice-grpc`
  - `BILLING_GRPC_SERVICE_NAME=aienie-payservice-grpc`
  - `AI_GRPC_SERVICE_NAME=aienie-aiservice-grpc`
- `env.txt` 当前默认 gRPC 地址为静态域名，优先避免跨网络 Consul 解析问题：
  - `USER_GRPC_ADDR=static://userservice.seekerhut.com:10001`
  - `BILLING_GRPC_ADDR=static://payservice.seekerhut.com:20021`
  - `AI_GRPC_ADDR=static://aiservice.seekerhut.com:10011`

当未显式指定 `*_GRPC_ADDR` 时，应用默认回退到 `consul:///${*_GRPC_SERVICE_NAME}`。

## 运行链路

1. 启动阶段按 `grpc.client.*.address` 建立 gRPC 通道。
2. SSO 回调时通过 `UserGrpcClient.validateSession(...)` 校验外部会话。
3. 首次登录执行 pay-service onboarding + 本地账户初始化。
4. 钱包兑换时调用 `BillingGrpcClient.exchangePublicToProject(...)`，本地账本同步记账。
5. AI 请求经 `AiGrpcClient` 执行后，在本地落地 `CONSUME` 流水。

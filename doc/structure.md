# 项目结构说明

> 更新时间：2026-02-17

## 结构树

AISocialGame/
├── backend/                      # Spring Boot BFF
│   ├── src/main/java/com/aisocialgame/
│   │   ├── controller/           # HTTP 接口层（含 WalletController、AiController、AuthController）
│   │   ├── service/              # 业务层（AuthService、WalletService、AiProxyService 等）
│   │   ├── integration/          # Consul + gRPC 客户端封装
│   │   ├── dto/                  # 请求/响应对象
│   │   ├── model/                # JPA 实体
│   │   ├── repository/           # 数据访问层
│   │   └── config/               # 配置映射与 Web 配置
│   ├── src/main/proto/           # user/billing/ai 协议定义
│   ├── src/main/resources/       # application.yml 与 SPI 资源
│   └── src/test/java/com/aisocialgame/
│       ├── AuthServiceTest.java
│       ├── WalletServiceTest.java
│       ├── AiProxyServiceTest.java
│       └── ConsulHttpServiceDiscoveryTest.java
├── frontend/                     # React + Vite 前端
│   ├── src/pages/
│   │   ├── SsoCallback.tsx       # SSO 回调页
│   │   ├── Profile.tsx           # 个人中心（含钱包入口）
│   │   └── AiChat.tsx            # SSE 流式聊天页
│   ├── src/components/wallet/    # 钱包页面组件（余额/签到/兑换/记录）
│   ├── src/hooks/useAuth.tsx     # 登录态管理（SSO 跳转与回调）
│   ├── src/services/api.ts       # 前端 API 封装（auth/ai/wallet/admin）
│   ├── src/types/index.ts        # 类型定义
│   ├── tests/full-flow.spec.ts   # 前端 E2E 用例（SSO + 钱包 + AI 流式）
│   └── nginx.conf                # 前端反向代理到 backend:20030
├── doc/
│   ├── api/                      # Controller API 文档（含 Auth/Ai/Wallet）
│   ├── api/external/             # 外部微服务协议文档
│   └── modules/                  # 模块说明文档
├── sql/
│   ├── schema.sql                # 全量表结构
│   └── *.sql                     # 业务表脚本
├── design-doc/v1.2/              # v1.2 规划与技术设计文档
├── docker-compose.yml            # 运行编排（前端 10030，后端 20030）
├── build.sh                      # Linux 构建/部署脚本
└── build.ps1                     # Windows 构建/部署脚本

## 关键目录说明

- `backend/src/main/java/com/aisocialgame/controller`
  - 负责 HTTP API 出口，包含鉴权入口、AI 能力、钱包能力与既有游戏接口。
- `backend/src/main/java/com/aisocialgame/integration`
  - 负责外部系统接入：Consul 服务发现与 gRPC 调用映射，屏蔽上游协议细节。
- `frontend/src/components/wallet`
  - 钱包 UI 组件拆分：余额、签到、兑换、消费记录、账本明细。
- `doc/api`
  - 以 Controller 为粒度维护接口文档，接口变更时同步更新。

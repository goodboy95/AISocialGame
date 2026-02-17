# 项目结构说明

> 更新时间：2026-02-17

## 结构树

AISocialGame/
├── backend/                                  # Spring Boot 后端
│   ├── src/main/java/com/aisocialgame/
│   │   ├── controller/                       # HTTP + WS 入口（GamePlayController/RoomController/RoomChatController）
│   │   ├── service/                          # 业务流程（房间、对局、统计、AI 发言等）
│   │   ├── websocket/                        # WebSocket 认证、连接状态、推送与限流
│   │   ├── dto/                              # 请求响应 DTO（含 dto/ws 事件模型）
│   │   ├── model/                            # JPA 实体 + 对局状态对象
│   │   ├── repository/                       # 数据访问层
│   │   ├── integration/                      # 外部服务发现与 gRPC 调用封装
│   │   └── config/                           # 应用配置、CORS、WebSocket Broker 配置
│   ├── src/main/resources/application.yml    # 服务端口、连接阈值、WS 开关等
│   └── src/test/java/com/aisocialgame/       # 单元/集成测试
├── frontend/                                 # React + Vite 前端
│   ├── src/pages/games/                      # 狼人杀/卧底房间页
│   ├── src/components/game/                  # 对局房间通用 UI（倒计时、过渡、连接状态、聊天）
│   ├── src/hooks/useGameSocket.ts            # 原生 WS + STOMP 客户端 Hook
│   ├── src/services/api.ts                   # HTTP API 封装
│   ├── src/types/index.ts                    # 对局状态与 WS 事件类型
│   ├── vite.config.ts                        # 开发端口 10030 与 /api 代理到 20030
│   └── nginx.conf                            # /api 与 /ws 反向代理到 backend:20030
├── doc/
│   ├── api/                                  # Controller 接口文档（新增 RoomChatController）
│   ├── modules/                              # 模块说明（新增 modules/README、realtime-ws-module）
│   ├── test/                                 # 操作与集成测试说明
│   ├── issues.md                             # 环境阻塞与遗留问题
│   └── structure.md                          # 本文件
├── artifacts/test/                           # 每次 Playwright/联调证据与日志
├── sql/                                      # 数据库结构与脚本
├── docker-compose.yml                        # 编排（前端 10030 / 后端 20030）
├── build.sh                                  # Linux 构建脚本
└── build.ps1                                 # Windows 构建脚本

## 关键目录说明

- `backend/src/main/java/com/aisocialgame/controller`
  - 负责对局 REST 接口和房间聊天 STOMP 入口，统一承接房间、开局、动作提交与聊天消息。
- `backend/src/main/java/com/aisocialgame/websocket`
  - 提供 WS 鉴权拦截、连接生命周期跟踪、聊天限流与推送封装，支撑前端实时化。
- `frontend/src/hooks/useGameSocket.ts`
  - 负责 WS 连接、订阅、自动重连、聊天发送和事件回调分发。
- `frontend/src/components/game`
  - 封装可复用的房间级 UI 组件，避免两种游戏房间页重复实现。
- `doc/api`
  - 以 Controller 为粒度维护接口说明，本次同步了对局与房间实时相关文档。

# 项目结构说明

> 更新时间：2026-02-19

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
│   ├── src/pages/games/                      # 狼人杀/卧底房间页（含结算面板、点击头像投票）
│   ├── src/pages/Achievements.tsx            # 成就中心页
│   ├── src/pages/Replays.tsx                 # 回放列表页
│   ├── src/pages/ReplayPlayer.tsx            # 回放播放器页
│   ├── src/pages/Guide.tsx                   # 新手引导与规则百科页
│   ├── src/pages/SpectatorRoom.tsx           # 观战模式页
│   ├── src/components/game/                  # 对局房间通用 UI（倒计时、过渡、连接状态、聊天、结算）
│   ├── src/components/social/                # 快速匹配弹窗、好友侧边面板
│   ├── src/components/tutorial/              # 新手引导覆盖层
│   ├── src/hooks/useGameSocket.ts            # 原生 WS + STOMP 客户端 Hook
│   ├── src/services/api.ts                   # HTTP API 封装
│   ├── src/services/v2Social.ts              # v2 社交留存能力（好友/成就/回放/快速匹配）
│   ├── src/types/index.ts                    # 对局状态与 WS/社交/成就/回放类型
│   ├── vite.config.ts                        # 开发/预览端口 11030，allowedHosts 与后端端口可配置
│   └── nginx.conf                            # /api 与 /ws 反向代理到 backend:20030（宿主机映射默认 11031）
├── doc/
│   ├── api/                                  # Controller 接口文档（新增 RoomChatController）
│   ├── modules/                              # 模块说明（新增 modules/README、realtime-ws-module）
│   ├── test/                                 # 操作与集成测试说明
│   ├── issues.md                             # 环境阻塞与遗留问题
│   └── structure.md                          # 本文件
├── artifacts/test/                           # 每次 Playwright/联调证据与日志
├── sql/                                      # 数据库结构与脚本
├── deploy/                                   # 第三方依赖独立部署脚本（与服务本体部署解耦）
│   ├── docker-compose.yml                    # MySQL/Redis/Qdrant/Consul 编排（127.0.0.1 + 默认端口+2）
│   ├── build.sh                              # 依赖容器启动/重启脚本
│   └── .gitignore                            # 依赖持久化目录忽略规则与 .gitkeep 例外
├── docker-compose.yml                        # 仅编排前后端（宿主机映射 11030/11031），数据库与缓存走外部服务
├── build.sh                                  # 测试环境 Docker 部署脚本（Linux/macOS）
├── build_prod.sh                             # 生产环境 Docker 部署脚本（Linux/macOS）
├── build_local.sh                            # 本地直启脚本（非 Docker，Linux/macOS）
├── build.ps1                                 # build.sh 的 PowerShell 版本
├── build_prod.ps1                            # build_prod.sh 的 PowerShell 版本
└── build_local.ps1                           # build_local.sh 的 PowerShell 版本（非 Docker）

## 关键目录说明

- `backend/src/main/java/com/aisocialgame/controller`
  - 负责对局 REST 接口和房间聊天 STOMP 入口，统一承接房间、开局、动作提交与聊天消息。
- `backend/src/main/java/com/aisocialgame/websocket`
  - 提供 WS 鉴权拦截、连接生命周期跟踪、聊天限流与推送封装，支撑前端实时化。
- `frontend/src/hooks/useGameSocket.ts`
  - 负责 WS 连接、订阅、自动重连、聊天发送和事件回调分发。
- `frontend/src/components/game`
  - 封装可复用的房间级 UI 组件，新增结算揭示面板与点击头像投票交互。
- `frontend/src/components/social`
  - 提供全局快速匹配与好友侧边栏，实现模块 06/07/17 的核心前端入口。
- `frontend/src/services/v2Social.ts`
  - 提供好友、成就、回放、快速匹配的前端能力层；后端缺口场景下可本地降级。
- `doc/api`
  - 以 Controller 为粒度维护接口说明，本次同步了对局与房间实时相关文档。
- `doc/modules/third-party-components.md`
  - 记录来自 `user-service/fireflyChat/fireflychat_studio` 的第三方组件基线，以及 AISocialGame 对齐结果和 deploy 独立部署方式。

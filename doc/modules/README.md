# 模块文档索引

> 更新时间：2026-02-20

| 模块 | 作用 | 入口实现位置 |
|---|---|---|
| 大厅与房间模块 | 管理创建房间、入座、AI 补位、座位广播与房间页入口 | `backend/src/main/java/com/aisocialgame/controller/RoomController.java`、`backend/src/main/java/com/aisocialgame/service/RoomService.java`、`frontend/src/pages/Lobby.tsx`、`frontend/src/pages/games/*.tsx` |
| 游戏流程模块 | 管理对局状态、阶段推进、超时处理、断线托管与结算 | `backend/src/main/java/com/aisocialgame/controller/GamePlayController.java`、`backend/src/main/java/com/aisocialgame/service/GamePlayService.java` |
| 房间实时通信模块 | 管理 STOMP 鉴权、状态推送、座位推送、房间聊天与连接状态 | `backend/src/main/java/com/aisocialgame/config/WebSocketConfig.java`、`backend/src/main/java/com/aisocialgame/controller/RoomChatController.java`、`backend/src/main/java/com/aisocialgame/websocket/*.java`、`frontend/src/hooks/useGameSocket.ts` |
| v2 社交留存与导航模块 | 覆盖快速匹配、好友、成就、回放、观战、新手引导与全局导航入口 | `doc/modules/v2-social-retention-module.md`、`frontend/src/components/social/*`、`frontend/src/pages/{Achievements,Replays,ReplayPlayer,Guide,SpectatorRoom}.tsx`、`frontend/src/services/v2Social.ts` |
| 认证与钱包模块 | 管理登录态、本地专属积分、签到、兑换码、通用转专属兑换 | `backend/src/main/java/com/aisocialgame/controller/AuthController.java`、`backend/src/main/java/com/aisocialgame/controller/WalletController.java`、`backend/src/main/java/com/aisocialgame/service/ProjectCreditService.java`、`frontend/src/components/wallet/*` |
| 管理后台模块 | 管理管理员登录、用户封禁、积分流水检查、客服补发/冲正/迁移、联通性诊断 | `backend/src/main/java/com/aisocialgame/controller/Admin*Controller.java`、`frontend/src/pages/admin/*` |
| gRPC 集成模块 | 管理 user/pay/ai 微服务发现与调用封装（payService 侧重通用积分能力） | `backend/src/main/java/com/aisocialgame/integration/*` |
| 第三方组件对齐模块 | 记录与基线服务统一的第三方组件（MySQL/Redis/Qdrant/Consul）与独立 deploy 方案 | `doc/modules/third-party-components.md`、`deploy/docker-compose.yml`、`deploy/build.sh` |

# 模块文档索引

> 更新时间：2026-02-17

| 模块 | 作用 | 入口实现位置 |
|---|---|---|
| 大厅与房间模块 | 管理创建房间、入座、AI 补位、座位广播与房间页入口 | `backend/src/main/java/com/aisocialgame/controller/RoomController.java`、`backend/src/main/java/com/aisocialgame/service/RoomService.java`、`frontend/src/pages/Lobby.tsx`、`frontend/src/pages/games/*.tsx` |
| 游戏流程模块 | 管理对局状态、阶段推进、超时处理、断线托管与结算 | `backend/src/main/java/com/aisocialgame/controller/GamePlayController.java`、`backend/src/main/java/com/aisocialgame/service/GamePlayService.java` |
| 房间实时通信模块 | 管理 STOMP 鉴权、状态推送、座位推送、房间聊天与连接状态 | `backend/src/main/java/com/aisocialgame/config/WebSocketConfig.java`、`backend/src/main/java/com/aisocialgame/controller/RoomChatController.java`、`backend/src/main/java/com/aisocialgame/websocket/*.java`、`frontend/src/hooks/useGameSocket.ts` |
| 认证与钱包模块 | 管理登录态、用户信息、金币与账单 | `backend/src/main/java/com/aisocialgame/controller/AuthController.java`、`backend/src/main/java/com/aisocialgame/controller/WalletController.java`、`frontend/src/hooks/useAuth.tsx`、`frontend/src/components/wallet/*` |
| 管理后台模块 | 管理管理员登录、用户封禁、计费检查、联通性诊断 | `backend/src/main/java/com/aisocialgame/controller/Admin*Controller.java`、`frontend/src/pages/admin/*` |
| gRPC 集成模块 | 管理 user/pay/ai 微服务发现与调用封装 | `backend/src/main/java/com/aisocialgame/integration/*` |

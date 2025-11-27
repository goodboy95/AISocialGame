# NexusPlay 项目结构与网站地图

本文档概述了 NexusPlay 社交推理游戏平台的页面结构、路由关系及各页面对应的详细开发文档。

## 1. 网站地图 (Sitemap)

```mermaid
graph TD
    L[登录 (Login)] --> A[首页 (Index)]
    R[注册 (Register)] --> L
    
    A --> B[游戏大厅 (RoomList)]
    A --> C[社区广场 (Community)]
    A --> D[排行榜 (Rankings)]
    A --> E[个人中心 (Profile)]
    
    B --> F[创建房间 (CreateRoom)]
    B --> G[游戏房间/等待室 (Lobby)]
    
    G --> H[谁是卧底 (UndercoverRoom)]
    G --> I[狼人杀 (WerewolfRoom)]
```

## 2. 页面清单与文档索引

| 页面名称 | 路由路径 | 作用描述 | 详细文档 |
| :--- | :--- | :--- | :--- |
| **登录** | `/login` | 用户登录入口，支持账号密码及第三方登录。 | [page_login.md](./page_login.md) |
| **注册** | `/register` | 新用户注册页面，包含表单验证。 | [page_register.md](./page_register.md) |
| **首页** | `/` | 平台的落地页，展示游戏列表、Banner 和入口。 | [page_index.md](./page_index.md) |
| **房间列表** | `/game/:gameId` | 特定游戏的房间大厅，展示当前活跃房间，提供搜索和筛选。 | [page_room_list.md](./page_room_list.md) |
| **创建房间** | `/create/:gameId` | 房间配置表单，根据游戏类型动态生成配置项。 | [page_create_room.md](./page_create_room.md) |
| **通用大厅** | `/room/:gameId/:roomId` | 游戏前的等待室，负责座位管理、聊天和游戏路由分发。 | [page_lobby.md](./page_lobby.md) |
| **谁是卧底** | (嵌入在 Lobby 中) | “谁是卧底”玩法的核心游戏页面，包含发牌、描述、投票等全流程。 | [page_game_undercover.md](./page_game_undercover.md) |
| **狼人杀** | (嵌入在 Lobby 中) | “狼人杀”玩法的核心页面，包含昼夜切换、技能交互、投票结算。 | [page_game_werewolf.md](./page_game_werewolf.md) |
| **社区广场** | `/community` | 玩家交流论坛，包含帖子流、话题和发布功能。 | [page_community.md](./page_community.md) |
| **排行榜** | `/rankings` | 展示全服玩家排名，支持按游戏类型切换榜单。 | [page_rankings.md](./page_rankings.md) |
| **个人中心** | `/profile` | 展示玩家数据、战绩历史和资产信息。 | [page_profile.md](./page_profile.md) |

## 3. 核心目录说明

*   `src/pages/`: 存放上述所有页面组件。
*   `src/components/`: 通用 UI 组件 (基于 shadcn/ui)。
*   `src/services/`: 后端 API 封装（`gameApi`/`roomApi`/`personaApi`/`authApi`）。首页和房间列表等页面的数据均来自这些接口。
*   `src/config/`: 游戏静态配置（`games.ts` 为开发期的参考/兜底 Schema，实际渲染依赖后端返回的数据）。
*   `src/types/`: TypeScript 类型定义。

# 系统功能与操作步骤（2025-11-27 更新）

## 0. 启动与访问
- 在仓库根目录执行 `./build.sh`（Docker Maven 容器编译后端 JAR、pnpm 构建前端 dist，准备 MySQL/Redis 数据卷，拉取 temurin/nginx 运行时镜像并挂载编译产物重启 docker-compose，不再执行本地 docker build）。启动后访问 `http://localhost`；Nginx 反代 `/api` 到后端。
- 后端 API 端口 `8080`，MySQL 映射 `3307`，Redis 映射 `6380`（数据持久化到 `/var/lib/aisocialgame`）。

## 1. 注册 / 登录 / 游客
1. 打开 `/login` 或右上角“登录”。
2. 已有账号：输入邮箱+密码登录。
3. 新用户：`/register` 填写邮箱、密码、昵称后自动登录（默认金币 1000，等级 1）。
4. 未登录时系统生成随机游客昵称与头像（localStorage 保存），仍可加入房间与开局；调用游戏接口或重连时需携带 `selfPlayerId`。

## 2. 首页大厅（`/`）
- 页面通过 `/api/games` 获取游戏卡片（在线人数为后端统计的实时房间座位数）。
- 点击“进入大厅”跳转 `/game/:gameId`。

## 3. 房间列表（`/game/:gameId`）
- 列表来自 `/api/games/{id}/rooms`，卡片展示房名、人数、公开/私密、交流模式、配置标签。
- “立即加入”进入 `/room/:gameId/:roomId`；进行中的房间禁用按钮（暂不支持观战）。

## 4. 创建房间（`/create/:gameId`）
- 根据游戏 schema 渲染配置（卧底：人数/卧底数模式/白板/词库/发言时长；狼人杀：人数/板子预设/女巫规则/胜利条件/发言时长等）。
- 提交调用 `POST /api/games/{id}/rooms`，创建者若登录自动成为房主/主持。

## 5. 通用房间大厅（`/room/:gameId/:roomId`）
- 首次进入自动调用 `POST /join`，响应体携带 `selfPlayerId`（已写入 localStorage，后续 `X-Player-Id` 头重用以防重复占座）。
- 可在空位选择 AI 人设 `POST /ai` 补位（昵称由后端随机生成，必要时可配置 `ai.name.endpoint` 走外部 AI 接口，失败自动回退本地随机名）。邀请按钮仅复制链接，房间聊天占位已移除（统一在玩法页查看日志）。
- “开始游戏”按钮需满足玩法人数，落到对应玩法页处理。

## 6. 谁是卧底真实流程（`/room/undercover/:roomId`）
1. 加载 `GET /state` 查看阶段/座位/词语；房主点击“开始游戏” -> 后端分配 civilian/undercover/blank 词语并切到发言阶段。
2. 当前座位发言者可输入描述并“提交发言”调用 `/speak`；AI 座位会在轮询 `/state` 时一位一位自动发言（不会一次跳过多人），日志实时追加，全部存活玩家发言完才会切到投票。
3. 投票阶段选择目标调用 `/vote`；全部玩家（含 AI）完成投票后才会一次性公布票型和出局人，否则保持在投票阶段等待未投票者。
4. 我的词语/身份仅在结算或本人视角显示；日志区域实时展示系统/发言/投票记录。

## 7. 狼人杀真实流程（`/room/werewolf/:roomId`）
1. 开局 `POST /start` 后后端分配角色（根据人数：狼人/预言家/女巫/猎人/村民），状态切到夜晚。
2. 夜晚：
   - `/state` 返回 `pendingAction` 提示人类角色的操作。狼人刀人/预言家查验/女巫解毒或下毒均通过 `POST /night-action` 提交；AI 自动补全缺失操作，超时后默认跳过。
3. 天亮讨论：
   - `phase=DAY_DISCUSS`，座位顺序发言，轮到本人时输入内容调用 `/speak`，AI 自动发言。
4. 投票：
   - `phase=DAY_VOTE` 选择目标调用 `/vote`，全员投完后公布出局身份并检查胜负，否则进入下一夜。
5. 结算：
   - `phase=SETTLEMENT`，日志展示胜负方；结算会为获胜人类玩家发放金币并刷新排行榜。

## 8. 社区与排行榜
- **社区 `/community`**：帖子实时从 `/api/community/posts` 获取；发布按钮调用 `POST /community/posts`（游客用 `X-Guest-Name`，登录自动带账号头像）；点赞调用 `/community/posts/{id}/like`。
- **排行榜 `/rankings`**：从 `/api/rankings?gameId=` 读取（werewolf/undercover/total），来源于真实结算的 `PlayerStats`。
- **个人中心 `/profile`**：展示后端用户金币/等级；未登录提示跳转登录。

## 9. 身份重连与安全
- 所有游戏内接口支持游客/断线玩家通过 `X-Player-Id` 标识席位；登录用户自动使用 token 中的 userId。
- 房主才能调用 `/start`，非法阶段/重复投票/非法目标均会返回 400。

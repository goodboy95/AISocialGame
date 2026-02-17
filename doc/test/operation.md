# 系统功能与操作步骤（2026-02-16 更新）

## 0. 启动与访问
1. 在项目根目录执行 `./build.sh`。
2. 浏览器访问 `http://socialgame.seekerhut.com`（或本地映射域名）。
3. 前端通过 `/api` 访问后端，后端再通过 gRPC 调用 user/pay/ai 微服务。

## 1. 用户注册 / 登录
1. 进入 `/register`。
2. 输入登录用户名、游戏昵称、邮箱、密码并提交。
3. 注册成功后自动登录，页面跳转首页。
4. 退出后进入 `/login`，输入“用户名或邮箱 + 密码”重新登录。

## 2. 个人信息与积分展示
1. 登录后打开 `/profile`。
2. 查看头像、昵称、等级、积分总额（由 `pay-service` 聚合返回）。
3. 返回首页，顶部积分胶囊应与 `/profile` 一致。

## 3. 大厅与房间玩法
1. 首页点击“进入大厅”进入某玩法房间列表。
2. 创建房间并入座。
3. 添加 AI 玩家后开局。
4. 在“谁是卧底 / 狼人杀”页面进行发言与投票，确认对局可推进至结算。

## 4. AI 接口（前台）
1. 调用 `GET /api/ai/models` 确认模型列表可返回。
2. 调用 `POST /api/ai/chat` 发送 `messages`，确认返回 `content/modelKey/token 统计`。

## 5. 管理后台
1. 进入 `/admin/login`。
2. 使用管理员账号登录。
3. 在 `/admin` 查看概览指标。
4. 在 `/admin/users` 输入用户 ID，查询用户，执行封禁/解封。
5. 在 `/admin/billing` 查询余额与流水。
6. 在 `/admin/ai` 查看模型并执行测试对话。
7. 在 `/admin/integration` 查看 user/pay/ai 服务联通状态。

## 6. 社区与排行榜
1. 打开 `/community` 发布帖子并点赞。
2. 打开 `/rankings` 查看总榜/分游戏榜。

## 7. 回归重点
1. `AuthController`：注册/登录/me 三接口均走 `user-service` 会话。
2. `AiController`：模型与聊天均走 `ai-service`。
3. `Admin*Controller`：后台所有接口都要求 `X-Admin-Token`。

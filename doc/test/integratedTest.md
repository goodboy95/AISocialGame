# 集成测试清单（2026-02-16）

## 1. 后端单元测试（已执行）
- 命令：`cd backend && mvn -q test`
- 结果：通过。
- 覆盖重点：
  - `AuthServiceTest`：注册/登录/邮箱映射登录/会话失效。
  - `BalanceServiceTest`：余额聚合逻辑。
  - `AdminAuthServiceTest`：管理员登录与 token 校验。
  - 原有 `GameControllerTest`、`RoomServiceTest`、`GamePlayServiceUndercoverTest` 回归通过。

## 2. 后端编译检查（已执行）
- 命令：`cd backend && mvn -q -DskipTests compile`
- 结果：通过。

## 3. 前端构建检查（已执行）
- 命令：`pnpm -C frontend build`
- 结果：失败（当前环境缺少前端依赖，`vite` 不可用）。
- 处理：阻塞已记录到 `doc/issues.md`。

## 4. Playwright MCP 页面自测（已执行）
- 访问 `http://socialgame.seekerhut.com`：`ERR_NAME_NOT_RESOLVED`。
- 访问 `http://localhost`：`ERR_CONNECTION_REFUSED`。
- 说明：当前环境缺少可访问的前端运行实例，无法完成页面级验证。
- 处理：阻塞与建议已记录到 `doc/issues.md`。

## 5. 待通过项（环境就绪后执行）
1. 前台 E2E：注册 -> 登录 -> 积分展示 -> 创建房间 -> AI 对局推进。
2. 管理台 E2E：管理员登录 -> 用户查询/封禁 -> 余额流水 -> AI 测试对话 -> 联通状态。
3. 部署验证：执行 `build.sh` 后通过目标域名完整访问前后台。

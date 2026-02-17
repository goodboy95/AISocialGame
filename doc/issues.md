# 未解决 / 阻塞记录（2026-02-17）

## 1. 后端联调阻塞（数据库连接不可用）
- 现象：执行 `mvn -f backend/pom.xml spring-boot:run -DskipTests` 时启动失败。
- 错误：`Connection refused`（MySQL 连接失败），随后 Hibernate 无法确定方言，应用退出。
- 影响：本地端口 `20030` 无法启动，导致前端 `/api` 与 `/ws` 联调失败。
- 证据：`artifacts/test/20260217-231428/backend.out.log`

## 2. 前端仓库目录构建阻塞
- 现象：执行 `npm --prefix frontend run build` 报错 `vite is not recognized`。
- 影响：仓库目录无法直接构建前端产物。
- 临时措施：使用 `frontend_tmp_run_20260217230120` 进行构建与页面自测。

## 3. Playwright 联调阶段的网络错误
- 现象：房间页持续出现 `/api/personas`、`/api/games/...` 500。
- 影响：WS 与后端动作路径无法闭环验证，当前只能验证前端渲染和交互降级行为。
- 证据：`artifacts/test/20260217-231428/playwright-network.log`

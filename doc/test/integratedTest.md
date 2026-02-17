# 集成测试清单（2026-02-17）

## 1. 后端单元测试
- 命令：`mvn -f backend/pom.xml test`
- 结果：通过（18/18）。
- 覆盖重点：
  - `GamePlayServiceUndercoverTest`：卧底流程与投票阶段回归
  - `RoomServiceTest`：房间生命周期与入座逻辑
  - 认证、余额、管理端、游戏控制器等既有测试回归通过

## 2. 前端构建测试
- 仓库目录构建：
  - 命令：`npm --prefix frontend run build`
  - 结果：失败（当前目录 `node_modules` 不可用，`vite` 命令缺失）。
- 临时运行目录构建（用于自测）：
  - 命令：`npm --prefix frontend_tmp_run_20260217230120 run build`
  - 结果：通过。

## 3. Playwright MCP 页面自测
- 执行方式：直接访问 `http://127.0.0.1:10030`（域名路径受本机环境限制，采用 IP+端口降级）。
- 覆盖页面：
  - `/`
  - `/room/undercover/mock-room`
  - `/room/werewolf/mock-room`
- 已执行动作：
  - 进入两个房间页，验证连接状态条、日志区、聊天区、倒计时/阶段 UI 结构可渲染。
  - 在聊天文本输入框填写功能相关文本并提交（按需求执行）。
- 结果：**部分通过 / 部分阻塞**。
  - 页面与前端交互层通过。
  - 后端依赖未就绪导致 API/WS 联调阻塞（大量 500）。

## 4. 证据位置
- 本轮证据目录：`artifacts/test/20260217-231428/`
- 关键文件：
  - `home.png`
  - `undercover-room.png`
  - `werewolf-room.png`
  - `werewolf-room-after-fix.png`
  - `playwright-console.log`
  - `playwright-network.log`
  - `backend.out.log`
  - `backend.err.log`
  - `run-meta.json`

## 5. 阻塞说明
1. 后端启动阻塞：`spring-boot:run` 连接数据库失败（Connection refused），端口 `20030` 未能就绪。
2. 前端仓库目录 `node_modules` 不可直接用于构建，需修复后再进行本目录标准构建与 E2E。

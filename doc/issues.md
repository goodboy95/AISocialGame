# 问题跟踪（更新于 2026-02-19）

## 已解决问题

### 1. 后端数据库连接阻塞（已解决）
- 结论：后端已可稳定启动，`/actuator/health` 返回 `UP`。
- 修复：
  - 默认 MySQL 连接改为 `192.168.1.4:3306/aisocialgame_main`（含 `createDatabaseIfNotExist=true`）。
  - Redis 改为 `192.168.1.4:6379`，Token key 使用项目前缀 `aisocialgame:auth:token:`。
- 证据：
  - `artifacts/test/20260218-084707/backend-health-fresh.txt`
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/java/com/aisocialgame/config/TokenStoreConfig.java`
  - `backend/src/main/java/com/aisocialgame/service/token/RedisTokenStore.java`

### 2. 前端仓库目录构建阻塞（已解决）
- 结论：仓库目录可直接执行构建，`vite build` 通过。
- 证据：
  - `artifacts/test/20260218-084707/frontend-build.log`

### 3. 房间页 Join 403 与 WS 连接中断（已解决）
- 现象（历史）：失效 token 或本地缓存场景下，房间自动入座可能触发 `join` 403，且前端未代理 `/ws` 导致实时聊天持续“连接中断”。
- 修复：
  - 房间自动入座逻辑增加 `auth loading` 与已有 seat 状态判断，避免无效重复入座。
  - `roomApi.join` 在 `401/403` 时自动按游客模式重试，避免入座链路被中断。
  - `vite.config.ts` 增加 `/ws` 反向代理（`ws: true`）到后端 `20030`。
- 验证：
  - 卧底房/狼人房均可通过 Playwright 输入功能匹配文本并实际显示聊天消息。
- 证据：
  - `artifacts/test/20260218-084707/undercover-room-ws-ok.png`
  - `artifacts/test/20260218-084707/werewolf-room-ws-ok.png`
  - `artifacts/test/20260218-084707/playwright-console-ws-ok.log`
  - `artifacts/test/20260218-084707/playwright-network-ws-ok.log`

### 4. Windows 端口排除策略占用 20030（已解决）
- 现象（历史）：本机无法绑定 `20030`，报错 `WinError 10013`。
- 修复：
  - 清理系统端口排除后复核，`20030` 已不在 `excludedportrange`；
  - 本地 backend 已恢复监听 `20030`；
  - 本机 nginx 已恢复将域名下 `/api`、`/ws` 转发到 `20030`。
- 验证：
  - `curl --noproxy "*" http://127.0.0.1:20030/api/games` 返回 `200`；
  - `curl --noproxy "*" http://aisocialgame.seekerhut.com/api/games` 返回 `200`；
  - `curl --noproxy "*" http://aisocialgame.aienie.com/api/games` 返回 `200`。

## 待处理问题

### 5. build_local.ps1 本地部署后端启动失败（待处理）
- 时间：2026-02-19
- 结论：`build_local.ps1` 可完成前后端编译并拉起前端首页，但后端在初始化数据源时失败，导致 `/actuator/health` 未就绪，脚本最终以失败退出。
- 关键错误：
  - `Access denied for user 'aisocialgame'@'172.18.0.1' (using password: YES)`
  - `Unknown database 'aisocialgame' / 'aisocialgame_main'`
  - `Unable to determine Dialect without JDBC metadata`
- 验证结果：
  - `http://127.0.0.1:10030` 可访问（首页可打开）
  - `http://127.0.0.1:20030/actuator/health` 不可用
- 证据：
  - `artifacts/local-run/backend.stdout.log`
  - `artifacts/local-run/backend.stderr.log`
  - `artifacts/local-run/frontend.stdout.log`
  - `artifacts/test/build-local-homepage-20260219.png`
  - `artifacts/test/build-local-homepage-20260219-rerun.png`

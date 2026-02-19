# 集成测试清单（2026-02-18）

## 1. 后端单元测试
- 命令：`mvn -f backend/pom.xml clean test package`
- 结果：通过（20/20，`BUILD SUCCESS`）。

## 2. 前端构建测试
- 命令：`pnpm --dir frontend build`
- 结果：通过（vite 构建成功）。

## 3. 域名、hosts、nginx 验证
- hosts：
  - `127.0.0.1 aisocialgame.seekerhut.com`
  - `127.0.0.1 aisocialgame.aienie.com`
- nginx：本机 `nginx.conf` 新增 `aisocialgame.seekerhut.com` / `aisocialgame.aienie.com` server，`/` 反代 `10030`，`/api` 和 `/ws` 反代 `20030`。
- 验证：
  - `curl --noproxy "*" -I http://aisocialgame.seekerhut.com` 返回 `200`
  - `curl --noproxy "*" http://aisocialgame.seekerhut.com/api/games` 返回游戏列表 JSON

## 4. Playwright E2E
- 命令：`PLAYWRIGHT_BASE_URL=http://aisocialgame.seekerhut.com pnpm --dir frontend test:e2e`
- 结果：`3 passed`
  - `tests/basic.spec.ts`
  - `tests/full-flow.spec.ts`
  - `tests/v2-features.spec.ts`

## 5. Playwright MCP 手工回归
- 覆盖路径：
  - `/`
  - `/achievements`
  - `/replays`
  - `/guide`
- 覆盖交互：
  - 顶部快速匹配弹窗打开/关闭
  - 好友侧边面板打开
- 控制台检查：无错误、无告警（修复 `DialogContent` 描述缺失后复测）。
- 证据：`artifacts/test/manual-home.png`

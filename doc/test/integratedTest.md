# 集成测试清单（2026-02-23）

## 1. 本地部署回归

- 命令
  - `echo "$SUDO_PASSWORD" | sudo -S bash ./build.sh`
- 结果
  - 通过（后端测试 + 打包、前端构建、Docker 重建、健康等待全部通过）
  - 输出末尾：`All done. Frontend: https://aisocialgame.seekerhut.com  Backend API: https://aisocialgame.seekerhut.com/api`

## 2. 后端测试

- 来源：`build.sh` 内部执行 `mvn clean test package`
- 结果：通过（`Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`）

## 3. 前端 E2E（Mock + Real）

### 3.1 Mock E2E

- 命令
  - `PLAYWRIGHT_BASE_URL=http://127.0.0.1:11030 pnpm --dir frontend test:e2e`
- 结果
  - `3 passed, 1 skipped`
  - 覆盖：首页、v2 导航、SSO 回调/钱包基础链路

### 3.2 Real E2E（真实 SSO + 真实兑换）

- 命令
  - `PLAYWRIGHT_BASE_URL=https://aisocialgame.seekerhut.com REAL_E2E=1 E2E_USERNAME=goodboy95 E2E_PASSWORD=superhs2cr1 pnpm --dir frontend test:e2e:real`
- 结果
  - `1 passed`
- 覆盖点
  - 使用真实 user-service 登录
  - 调用本项目 `/api/auth/sso-callback` 建立本地会话
  - 执行 `100` 通用积分兑换专属积分
  - 校验兑换历史展示“兑换前后通用积分/项目永久积分”

## 4. 接口抽检

### 4.1 SSO 跳转

- 命令
  - `curl --noproxy "*" -k -i "https://aisocialgame.seekerhut.com/api/auth/sso/login?state=1234567890abcdef1234567890abcdef"`
- 结果
  - `302 Found`
  - `Location` 指向 `https://userservice.seekerhut.com/sso/login?...&state=...`

### 4.2 管理端全量迁移

- 命令
  - 登录获取管理员 token：`POST /api/admin/auth/login`
  - 执行：`POST /api/admin/billing/migrate-all`
- 结果示例
  - `{"scanned":1,"success":1,"failed":0,"batchSize":100,"failures":[]}`

### 4.3 基础可用性

- 域名首页
  - `curl --noproxy "*" -k -I https://aisocialgame.seekerhut.com` -> `HTTP/2 200`
- 后端健康
  - `curl --noproxy "*" http://127.0.0.1:11031/actuator/health` -> `{"status":"UP"}`

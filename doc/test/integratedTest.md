# 集成测试记录（2026-02-25）

## 1. 执行环境与前置

- 部署命令：`sudo ./build.sh`
- 测试域名：`https://aisocialgame.seekerhut.com`
- 账号：`goodboy95 / superhs2cr1`
- 严格 gRPC 鉴权变量已注入：
  - `APP_EXTERNAL_USERSERVICE_INTERNAL_GRPC_TOKEN`
  - `APP_EXTERNAL_PAYSERVICE_JWT`
  - `APP_EXTERNAL_AISERVICE_HMAC_CALLER`
  - `APP_EXTERNAL_AISERVICE_HMAC_SECRET`

## 2. 系统级修复（本次执行）

### 2.1 hosts 修复

将三服务域名指向 `192.168.5.141`：

- `userservice.seekerhut.com`
- `payservice.seekerhut.com`
- `aiservice.seekerhut.com`

保留 `aisocialgame.seekerhut.com -> 127.0.0.1` 用于本机站点访问。

### 2.2 nginx 反代修复

修复文件：`/etc/nginx/sites-enabled/aisocialgame.seekerhut.com.conf`

- 移除错误配置：`location /sso/ { proxy_pass http://127.0.0.1:11031/sso/; ... }`
- 原因：`/sso/callback` 应由前端路由接管，错误反代会导致回调页 500。
- 执行：`nginx -t && nginx -s reload`

## 3. build.sh 结果

`build.sh` 全流程成功：

1. 后端 `mvn clean test package` 成功（`Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`）
2. 前端构建成功
3. Docker Compose 重建成功
4. 健康检查通过
5. 自动全量迁移通过：
   - `{"scanned":1,"success":1,"failed":0,"batchSize":100,"failures":[]}`
6. Playwright 冒烟 + 真实链路全部通过

## 4. Playwright 结果

### 4.1 冒烟/功能回归

执行：

- `tests/basic.spec.ts`
- `tests/full-flow.spec.ts`
- `tests/v2-features.spec.ts`

结果：`3 passed`

### 4.2 真实链路

执行：`tests/real-flow.spec.ts`（`REAL_E2E=1`）

结果：`1 passed`

覆盖点：

- 真实 SSO 登录
- 用户个人页钱包可访问
- 执行 `100` 通用积分兑换专属积分
- 展示兑换历史（通用积分与项目永久积分兑换前后值）

## 5. 抽检

- `https://aisocialgame.seekerhut.com` 可访问
- `http://127.0.0.1:11031/actuator/health` 返回 `UP`
- `GET /api/auth/sso/login?state=...` 返回 `302` 到 user-service

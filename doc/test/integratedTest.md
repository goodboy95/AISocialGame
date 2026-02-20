# 集成测试清单（2026-02-19）

## 1. 部署与健康检查
- 部署脚本：`powershell -ExecutionPolicy Bypass -File .\build_local.ps1`
- 结果：通过（前后端服务均启动成功）。
- 健康检查：
  - `curl http://127.0.0.1:11030` -> `200`
  - `curl http://127.0.0.1:11031/actuator/health` -> `{"status":"UP"}`

## 2. 后端单元测试
- 命令：`mvn -f backend/pom.xml test`
- 结果：通过（`BUILD SUCCESS`，22 tests passed）。

## 3. 前端构建测试
- 命令：`pnpm --dir frontend build`
- 结果：通过（vite 构建成功）。

## 4. 域名与 Nginx 验证
- 验证命令：`curl --noproxy "*" -I http://aisocialgame.seekerhut.com`
- 结果：返回 `HTTP/1.1 200 OK`，由 `nginx/1.28.2` 提供。
- 当前端口映射：
  - 前端对外端口：`11030`
  - 后端对外端口：`11031`

## 5. SSO 接口级回归（本次修复点）
- 非法 `state`：
  - 命令：`curl -i "http://127.0.0.1:11031/api/auth/sso/login?state=bad"`
  - 结果：`400`，响应 `{"message":"SSO state 格式不合法","status":400}`
- 登录跳转：
  - 命令：`curl -i "http://127.0.0.1:11031/api/auth/sso/login?state=1234567890abcdef1234567890abcdef"`
  - 结果：`302`，`Location` 指向 user-service `/sso/login`，并包含：
    - `redirect=http://aisocialgame.seekerhut.com/sso/callback`
    - `state=1234567890abcdef1234567890abcdef`
- 注册跳转：
  - 命令：`curl -i "http://127.0.0.1:11031/api/auth/sso/register?state=1234567890abcdef1234567890abcdef"`
  - 结果：`302`，`Location` 指向 user-service `/register`，并包含相同 `redirect` 与 `state`。

## 6. Playwright MCP 手工回归
- 用例 1：从 `http://aisocialgame.seekerhut.com` 点击登录，确认跳转到 user-service 登录页。
- 用例 2：在 user-service 页面确认回调地址为 `http://aisocialgame.seekerhut.com/sso/callback`。
- 用例 3：构造错误 `state` 访问 `/sso/callback`，确认前端阻断并提示失败后返回首页。
- 控制台与网络：关键链路无阻断错误。
- 证据：
  - `artifacts/test/20260219-sso-rerun/sso-login-redirect.png`
  - `artifacts/test/20260219-sso-rerun/sso-state-mismatch.png`

---

# 集成测试清单（2026-02-20）

## 1. 后端单元测试（含专属积分服务新增分支）
- 命令：`mvn -q -f backend/pom.xml test`
- 结果：通过（`28` tests passed, `0` failures）。
- 重点覆盖：
  - `ProjectCreditServiceTest`：签到幂等、兑换码失败次数风控、无效兑换码失败记录、通用转专属日限额、`requestId` 幂等重放、冲正防重入。

## 2. 前端构建测试
- 命令：`npm --prefix frontend run build`
- 结果：通过（Vite 构建成功，产物输出到 `frontend/dist`）。

## 3. Playwright 页面验收（deploy-test 关键路径）
- 访问：`http://aisocialgame.seekerhut.com`
- 结果：首页可打开，页面首屏渲染正常。
- 控制台与网络：无阻断错误。
- 证据：
  - `artifacts/test/20260220-224940/home-full.png`
  - `artifacts/test/20260220-224940/console.log`
  - `artifacts/test/20260220-224940/network.log`

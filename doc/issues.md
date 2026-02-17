# 未解决 / 备忘（2026-02-16）

## 1. 前端构建依赖缺失
- 现象：执行 `pnpm -C frontend build` 时报错 `vite is not recognized`。
- 影响：无法在当前环境重新构建前端产物，导致页面级回归测试受阻。
- 建议：先执行前端依赖安装（`pnpm install --frozen-lockfile`），再执行 `pnpm build` 与 Playwright 页面测试。

## 2. Playwright 页面自测阻塞
- 现象：
  - `http://socialgame.seekerhut.com` -> `ERR_NAME_NOT_RESOLVED`
  - `http://localhost` -> `ERR_CONNECTION_REFUSED`
- 影响：当前会话无法访问可用的前端站点，无法完成 UI 路径验收。
- 建议：确保目标域名可解析并有服务实例，或先在本机启动前端服务后再执行 Playwright。

## 3. 本地“无依赖”后端启动限制
- 现象：尝试用打包 JAR + H2 启动进行 API 自测时，运行时 classpath 不含 H2 驱动（H2 为 test scope）。
- 影响：无法直接通过 JAR 在无 MySQL/Redis 环境完成端到端接口联调。
- 建议：
  1. 使用 `spring-boot:run` 并启用 test classpath，或
  2. 提供可用 MySQL/Redis 环境后执行完整联调。

## 4. Java 版本兼容提醒
- 当前后端使用 JDK 25 运行但 `--release 21` 编译。若需完全 class 版本 69，需要升级 Spring/ASM 相关依赖链。

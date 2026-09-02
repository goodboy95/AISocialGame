# AISocialGame 项目结构

本文件是根目录快速入口；详细、持续维护的结构说明以
[`doc/structure.md`](doc/structure.md) 为准。当前实现不是 Vue 项目，也没有独立的
`manage/` 管理前端。

## 顶层

```text
AISocialGame/
├── backend/                 Spring Boot 后端、SQL、proto 与测试
├── frontend/                React 18 + TypeScript + Vite 单一前端工程
├── doc/                     架构、模块、API、运维与可重复测试说明
├── ci/                      发版中心两阶段发布契约（build-release.sh 唯一发版入口）
├── scripts/windows/         Windows 本机调试启动脚本（Start-Backend/Start-Frontend）
└── env.example              无秘密配置模板（本地 F5 调试用）
```

## 前端边界

- `frontend/src/main.tsx` 按 URL 精确分流：`/admin` 与 `/admin/*` 动态加载
  `App.tsx`，其余路由动态加载 `UserApp.tsx`。
- 用户端与管理端属于同一 React/Vite 工程和同一前端制品，但使用独立入口 chunk。
  管理端固定简体中文，不加载用户端 i18n 资源。
- `frontend/src/pages/admin/` 与 `frontend/src/components/layout/AdminLayout.tsx`
  是内嵌管理台；不存在独立 Vue 管理项目。
- 管理员认证由后端本地管理员策略与 HttpOnly 会话 Cookie 裁决，不能以
  LocalStorage 标记或普通 SSO 会话替代。

## 后端边界

- `backend/src/main/java/com/aisocialgame/controller/`：HTTP、WebSocket 与 Admin API。
- `backend/src/main/java/com/aisocialgame/engine/`：GameEngine 插件化入口。
- `backend/src/main/java/com/aisocialgame/service/`：游戏、SSO、积分、AI 与运营编排。
- `backend/src/main/java/com/aisocialgame/integration/`：user/pay/ai 静态 gRPC 客户端。
- `backend/src/main/resources/application.yml` 与 `backend/sql/`：运行配置与版本化 SQL。

## 运行入口

- 发版中心发布（Linux Docker）：`ci/build-release.sh`（运行时配置由 config-center 提供）。
- Windows 本机调试入口：`scripts/windows/Start-Backend.ps1`、`Start-Frontend.ps1`
  （前台运行，Ctrl+C 停止）。
- 浏览器本地入口：`https://localsocialgame.testhut.top`；管理台为同站
  `/admin` 路由。

# 前端应用（Vue 3 + Vite + Element Plus）

该目录包含基于 Vue 3 + TypeScript 的前端工程。本阶段完成了项目脚手架、核心依赖、路由与状态管理配置，并预置了登录、注册、大厅、房间等页面占位。

## 主要特性

- 使用 Vite 5 构建，支持热更新。
- 集成 Element Plus，启用按需自动引入（unplugin-auto-import / unplugin-vue-components）。
- 采用 Vue Router + Pinia 管理路由与全局状态。
- 提供基础 API 封装与 WebSocket 客户端骨架。

## 可用脚本

```bash
npm install    # 安装依赖
npm run dev    # 本地开发，默认端口 5173
npm run build  # 产物构建
npm run preview  # 预览构建结果
```

## 目录结构

```text
src/
  api/         # REST API 封装
  pages/       # 登录/注册/大厅/房间页面
  router/      # 路由配置
  services/    # WebSocket 客户端
  store/       # Pinia 状态
  styles/      # 全局样式与主题
  types/       # TypeScript 类型定义
```

## 环境变量

复制 `.env.example` 为 `.env`，调整 `VITE_API_BASE_URL`、`VITE_WS_BASE_URL` 以对接不同环境的后端服务。

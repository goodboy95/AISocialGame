# AISocialGame 前端

本目录是 AISocialGame 的单一 React 18 + TypeScript + Vite 前端工程，不是独立的
Dyad 示例应用。

- `src/main.tsx` 按请求路径动态加载两个入口：用户路由加载 `UserApp.tsx`，
  `/admin` 与 `/admin/*` 加载 `App.tsx`。
- `src/pages/admin/` 和 `src/components/layout/AdminLayout.tsx` 构成内嵌管理台；
  管理台与用户端同属一个构建制品，但通过动态 import 分包。
- 用户端使用 i18next；管理端固定简体中文且不加载用户 locale 资源。
- API 调用统一位于 `src/services/`，管理员登录依赖后端 HttpOnly 会话 Cookie，
  不使用 LocalStorage 伪造管理员身份。

常用静态检查：

```bash
corepack pnpm install --frozen-lockfile
pnpm lint
pnpm build
```

项目级架构和运行入口见 [`../doc/structure.md`](../doc/structure.md) 与
[`../doc/operations/windows-native.md`](../doc/operations/windows-native.md)。

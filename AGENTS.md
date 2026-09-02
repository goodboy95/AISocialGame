- 项目名: AISocialGame
- 项目归属：aienie
- 项目类型：业务项目（projects）
- 前端技术栈: React 18 + TypeScript + Vite + React Router + TanStack Query + Tailwind CSS + shadcn/ui
- 后端技术栈: Java Spring boot
- 本地环境域名: localsocialgame.testhut.top
- 预发布环境域名: socialgame.testhut.top
- 生产环境目标域名（切换完成前不得作为当前入口）: socialgame.seekerhut.com
- 前端对外端口: 11030
- 后端对外端口: 11031

在linux环境下，执行sudo的密码请从SUDO_PASSWORD环境变量获取。
- 发版入口: 发版中心统一使用 ci/build-release.sh（Linux Docker 部署，运行时配置由 config-center 提供，不使用仓库本地配置）。
- 本地 Windows 调试入口: scripts/windows/Start-Backend.ps1 与 Start-Frontend.ps1（不使用 Docker）。

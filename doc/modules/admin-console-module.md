# 管理后台模块说明（v1.1）

## 目标
提供与游戏业务直接相关的运营后台，不照搬 fireflyChat 全量功能，聚焦本项目的用户/积分/AI/联通运维需求。

## 前端
- 路由：`/admin/login`、`/admin`、`/admin/users`、`/admin/billing`、`/admin/ai`、`/admin/integration`
- 认证：`useAdminAuth`，本地缓存 `aisocialgame_admin_token`，统一设置 `X-Admin-Token`。
- 布局：`AdminLayout` 侧边导航 + 内容区。

## 后端
- `AdminAuthController`：管理登录与 token 校验。
- `AdminDashboardController`：概览统计。
- `AdminIntegrationController`：外部服务联通检查。
- `AdminUserController`：用户查询、封禁、解封。
- `AdminBillingController`：余额与流水查询。
- `AdminAiController`：模型列表与测试对话。

## 安全边界
- 所有 `/api/admin/*` 业务接口都要求 `X-Admin-Token`。
- 管理账号来自配置 `app.admin.*`。
- 管理 token 仅用于后台接口，不与前台用户 token 复用。

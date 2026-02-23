# 管理后台模块说明（v1.3）

> 更新时间：2026-02-23

## 目标

提供项目运营后台，覆盖用户管理、积分运营、AI 联通检查与批量迁移能力。

## 前端

- 路由
  - `/admin/login`
  - `/admin`
  - `/admin/users`
  - `/admin/billing`
  - `/admin/ai`
  - `/admin/integration`
- 认证
  - `useAdminAuth`，本地存储 `aisocialgame_admin_token`
  - 后台接口统一携带 `X-Admin-Token`

## 后端

- `AdminAuthController`：登录、token 校验
- `AdminDashboardController`：概览统计
- `AdminIntegrationController`：外部依赖联通探测
- `AdminUserController`：用户查询/封禁/解封
- `AdminBillingController`：余额、流水、调账、冲正、迁移、兑换码
- `AdminAiController`：模型列表与测试对话

## 积分运营能力

- 用户迁移：`POST /api/admin/billing/migrate-user`
- 全量迁移：`POST /api/admin/billing/migrate-all`（可带 `batchSize`）
- 调账：`POST /api/admin/billing/adjust`
- 冲正：`POST /api/admin/billing/reversal`
- 兑换码创建：`POST /api/admin/billing/redeem-codes`

前端积分管理页已提供“全量迁移所有用户”按钮并展示执行结果（扫描数/成功数/失败数/失败明细）。

## 安全边界

- 所有 `/api/admin/*` 业务接口必须带 `X-Admin-Token`。
- 管理员账户由 `app.admin.*` 配置控制。
- 管理 token 与用户 token 隔离。
- 关键积分操作全部落地到本地账本，支持按 `requestId` 追踪。

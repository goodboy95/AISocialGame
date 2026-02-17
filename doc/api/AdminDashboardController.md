# AdminDashboardController 接口说明

基址：`/api/admin/dashboard`

## GET /summary
- 用途：读取管理台概览数据（本地用户数/房间数/帖子数/对局状态数/AI 模型数）。
- 请求头：`X-Admin-Token` (required)
- 响应 200：`AdminDashboardSummaryResponse`

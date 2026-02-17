# AdminIntegrationController 接口说明

基址：`/api/admin/integration`

## GET /services
- 用途：检查 `user-service`、`pay-service`、`ai-service` 联通状态。
- 请求头：`X-Admin-Token` (required)
- 响应 200：
```json
{
  "services": [
    { "service": "user-service", "reachable": true, "message": "ok" },
    { "service": "pay-service", "reachable": true, "message": "ok" },
    { "service": "ai-service", "reachable": true, "message": "ok" }
  ]
}
```

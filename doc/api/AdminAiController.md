# AdminAiController 接口说明

基址：`/api/admin/ai`

## GET /models
- 用途：管理端查看 AI 模型列表。
- 请求头：`X-Admin-Token` (required)
- 响应 200：`AiModelView[]`

## POST /test-chat
- 用途：管理端发起 AI 测试调用。
- 请求头：`X-Admin-Token` (required)
- 请求体：
  - `userId` (long, optional)
  - `sessionId` (string, optional)
  - `model` (string, optional)
  - `messages` (array, required)
- 响应 200：`AiChatResponse`

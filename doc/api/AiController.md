# AiController 接口说明

基址：`/api/ai`

## GET /models
- 用途：拉取 `ai-service` 模型列表（`AiGatewayService/ListModels`）。
- 请求参数：无。
- 响应 200：`AiModelView[]`

## POST /chat
- 用途：通过 `ai-service` 执行对话补全（`AiGatewayService/ChatCompletions`）。
- 请求体：
  - `model` (string, optional)
  - `messages` (array, required)
    - `role` (string)
    - `content` (string)
- 请求头：`X-Auth-Token` 可选；有登录态时使用对应外部 user/session，无登录态走系统用户。
- 响应 200：
  - `content`
  - `modelKey`
  - `promptTokens`
  - `completionTokens`

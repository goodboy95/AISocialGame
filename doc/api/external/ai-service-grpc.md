# ai-service gRPC 接口（外部依赖）

- 来源：gRPC Reflection + Consul 服务发现
- 获取时间：2026-02-16

## 服务
- `fireflychat.ai.v1.AiGatewayService`
  - `ListModels`
  - `ChatCompletions`

## 核心请求字段
- `ChatCompletionsRequest`: `request_id`, `project_key`, `user_id`, `session_id`, `model`, `messages[]`
- `ChatMessage`: `role`, `content`

## 核心响应字段
- `ListModelsResponse.models[]`: `id`, `display_name`, `provider`, `input_rate`, `output_rate`, `type`
- `ChatCompletionsResponse`: `content`, `model_key`, `prompt_tokens`, `completion_tokens`

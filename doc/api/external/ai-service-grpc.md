# ai-service gRPC 接口（外部依赖）

- 来源：gRPC Reflection + Consul 服务发现 + 本仓库 `backend/src/main/proto/ai/v1/ai_service.proto`
- 获取时间：2026-02-17

## 服务
- `fireflychat.ai.v1.AiGatewayService`
  - `ListModels`
  - `ChatCompletions`
  - `Embeddings`
  - `OcrParse`

## 核心请求字段
- `ChatCompletionsRequest`: `request_id`, `project_key`, `user_id`, `session_id`, `model`, `messages[]`
- `ChatMessage`: `role`, `content`
- `EmbeddingsRequest`: `request_id`, `project_key`, `user_id`, `session_id`, `model`, `input[]`, `normalize`
- `OcrParseRequest`: `request_id`, `project_key`, `user_id`, `session_id`, `model`, `image_url`, `image_base64`, `document_url`, `pages`, `output_type`

## 核心响应字段
- `ListModelsResponse.models[]`: `id`, `display_name`, `provider`, `input_rate`, `output_rate`, `type`
- `ChatCompletionsResponse`: `content`, `model_key`, `prompt_tokens`, `completion_tokens`
- `EmbeddingsResponse`: `model_key`, `dimensions`, `vectors[].values[]`, `prompt_tokens`
- `OcrParseResponse`: `request_id`, `model_key`, `output_type`, `content`, `raw_json`

## 说明
- 若线上 ai-service 尚未升级到含 `Embeddings` / `OcrParse` 的版本，调用将返回 gRPC 未实现错误（`UNIMPLEMENTED`），BFF 侧会透传为网关错误。

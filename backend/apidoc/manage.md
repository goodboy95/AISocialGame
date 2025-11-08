# ManageController 接口文档

- 基础路径：`/api/manage`
- 认证要求：需管理员角色（`ROLE_ADMIN`），控制器上使用 `@PreAuthorize("hasRole('ADMIN')")`，同时受全局 JWT 认证保护。
- 相关 DTO：`ManageDtos`（定义于 `backend/src/main/java/com/aisocialgame/backend/dto/ManageDtos.java`）。

## GET `/api/manage/access/`
- **说明**：校验当前用户是否为管理员。
- **返回**：
  - 成功：`200 OK`，`AdminAccess` 对象，字段 `is_admin` 为布尔值。
  - 未登录：`401 Unauthorized`。
- **逻辑要点**：`AuthService.currentUser` 读取当前用户，直接返回是否具备管理员标记。

## GET `/api/manage/ai-models/`
- **说明**：获取所有可用的 AI 模型配置列表。
- **返回**：`200 OK`，数组，元素为 `AiModelConfigView`：
  - `id`、`name`、`base_url`、`token`、`created_at`、`updated_at`。
- **逻辑要点**：`ManageService.listModelConfigs` 读取配置表。

## POST `/api/manage/ai-models/`
- **说明**：新增一个 AI 模型配置。
- **请求体**：`AiModelConfigPayload`
  ```json
  {
    "name": "string",
    "base_url": "string",
    "token": "string"
  }
  ```
- **返回**：`200 OK`，返回新建的 `AiModelConfigView`。
- **逻辑要点**：服务层会做名称、URL、token 长度校验并持久化。

## PATCH `/api/manage/ai-models/{id}/`
- **说明**：更新指定模型配置。
- **请求体**：同 `AiModelConfigPayload`。
- **返回**：`200 OK`，更新后的 `AiModelConfigView`。
- **逻辑要点**：`ManageService.updateModelConfig` 查找后覆盖字段，若不存在会抛出异常。

## DELETE `/api/manage/ai-models/{id}/`
- **说明**：删除模型配置。
- **返回**：`204 No Content`。
- **逻辑要点**：`ManageService.deleteModelConfig` 直接按 ID 移除。

## GET `/api/manage/overview/`
- **说明**：一次性返回后台概览，包括模型和卧底 AI 角色。
- **返回**：`200 OK`，`ManageOverview` 对象。
  - `models`：`AiModelConfigView` 列表。
  - `undercoverRoles`：`UndercoverAiRoleView` 列表。
- **逻辑要点**：组合 `listModelConfigs` 与 `listUndercoverRoles` 结果用于后台仪表盘。

## GET `/api/manage/undercover/roles/`
- **说明**：列出所有卧底 AI 角色配置。
- **返回**：`200 OK`，数组，元素为 `UndercoverAiRoleView`：
  - `id`、`name`、`model`（`AiModelConfigSummary`）、`personality`、`created_at`、`updated_at`。

## POST `/api/manage/undercover/roles/`
- **说明**：新增卧底 AI 角色。
- **请求体**：`UndercoverAiRolePayload`
  ```json
  {
    "name": "string",
    "model_id": 0,
    "personality": "string"
  }
  ```
- **返回**：`200 OK`，新建的 `UndercoverAiRoleView`。
- **逻辑要点**：要求模型 ID 存在，`personality` 保存人格描述/提示词片段。

## PATCH `/api/manage/undercover/roles/{id}/`
- **说明**：更新卧底 AI 角色配置。
- **请求体**：同 `UndercoverAiRolePayload`。
- **返回**：`200 OK`，更新后的 `UndercoverAiRoleView`。

## DELETE `/api/manage/undercover/roles/{id}/`
- **说明**：删除卧底 AI 角色。
- **返回**：`204 No Content`。

## GET `/api/manage/prompts/`
- **说明**：按条件筛选 AI 提示词模板。
- **查询参数**：
  - `game_type`（可选）
  - `role_key`（可选）
  - `phase_key`（可选）
- **返回**：`200 OK`，`AiPromptTemplateView` 数组。
  - 字段：`id`、`game_type`、`role_key`、`phase_key`、`content`、`created_at`、`updated_at`。
- **逻辑要点**：`ManageService.listPromptTemplates` 根据条件过滤并返回。

## GET `/api/manage/prompts/dictionary/`
- **说明**：获取提示词模板支持的游戏/阶段/角色选项。
- **返回**：`200 OK`，`AiPromptDictionary`。
  - `games`：每项包含 `key`、`label`、`phases`（`AiPromptPhaseOption` 列表）和 `roles`（`AiPromptRoleOption` 列表）。
  - `default_role_key`：默认角色键值。
- **逻辑要点**：用于后台下拉选项渲染。

## POST `/api/manage/prompts/`
- **说明**：新建提示词模板。
- **请求体**：`AiPromptTemplatePayload`
  ```json
  {
    "game_type": "string",
    "role_key": "string | null",
    "phase_key": "string",
    "content": "string"
  }
  ```
- **返回**：`200 OK`，新建的 `AiPromptTemplateView`。

## PATCH `/api/manage/prompts/{id}/`
- **说明**：更新提示词模板。
- **请求体**：同 `AiPromptTemplatePayload`。
- **返回**：`200 OK`，更新后的 `AiPromptTemplateView`。

## DELETE `/api/manage/prompts/{id}/`
- **说明**：删除提示词模板。
- **返回**：`204 No Content`。

## 权限和异常说明
- 所有接口都需要管理员身份，否则将返回 `403 Forbidden`。
- 若携带的访问令牌无效或缺失，将由全局安全配置返回 `401 Unauthorized`。

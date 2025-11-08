# MetaController 接口文档

- 基础路径：`/api/meta`
- 认证要求：全部公开访问，无需 Token。
- 相关 DTO：`MetaDtos`（定义于 `backend/src/main/java/com/aisocialgame/backend/dto/MetaDtos.java`）。

## GET `/api/meta/styles/`
- **说明**：获取后端配置的 AI 扮演风格列表，用于前端展示 AI 玩家选项。
- **返回**：`200 OK`，`AiStyleResponse` 对象。
  - `styles`：数组，每项为：
    - `key`：风格标识。
    - `label`：展示名称。
    - `description`：描述文案。
- **逻辑要点**：值来自 `app.ai-styles` 配置（`application.yml`），便于通过配置文件扩展。

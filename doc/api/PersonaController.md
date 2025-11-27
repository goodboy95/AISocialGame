# PersonaController 接口说明

基址：`/api/personas`

## GET /
- **用途**：获取可用的 AI 陪玩/人设列表。
- **响应 200**
```json
[
  { "id": "ai1", "name": "福尔摩斯", "trait": "逻辑严密", "avatar": "..." },
  ...
]
```

**说明**：前端大厅和玩法页用于填充“添加 AI”下拉/列表。*** End Patch

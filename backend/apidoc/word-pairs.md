# WordPairController 接口文档

- 基础路径：`/api/games/word-pairs`
- 认证要求：
  - `GET`/`export` 接口公开访问。
  - 创建、批量导入、更新、删除需要登录（前端后台管理常用）。
- 相关 DTO：`WordPairDtos`（定义于 `backend/src/main/java/com/aisocialgame/backend/dto/WordPairDtos.java`）。

## GET `/api/games/word-pairs/`
- **说明**：按主题、难度或关键字查询词条。
- **查询参数**：
  - `topic`（可选）：主题过滤。
  - `difficulty`（可选）：难度过滤，如 `easy`、`hard` 等。
  - `q`（可选）：模糊搜索关键词。
- **返回**：`200 OK`，`WordPairView` 数组。
  - 字段：`id`、`topic`、`civilian_word`、`undercover_word`、`difficulty`、`created_at`、`updated_at`。
- **逻辑要点**：`WordPairService.list` 根据条件查询并排序。

## POST `/api/games/word-pairs/`
- **说明**：新建词条，需登录。
- **请求体**：`WordPairPayload`
  ```json
  {
    "topic": "水果",
    "civilian_word": "苹果",
    "undercover_word": "梨",
    "difficulty": "easy"
  }
  ```
- **返回**：
  - 成功：`200 OK`，新建的 `WordPairView`。
  - 未登录：`401 Unauthorized`。
- **逻辑要点**：`WordPairService.create` 执行唯一性及必要字段校验。

## POST `/api/games/word-pairs/import/`
- **说明**：批量导入词条（登录用户）。
- **请求体**：`BulkImportPayload`
  ```json
  {
    "items": [ { "topic": "食物", "civilian_word": "米饭", "undercover_word": "粥", "difficulty": "medium" }, ... ]
  }
  ```
- **返回**：
  - 成功：`200 OK`，`BulkImportResponse`。
    - `items`：成功导入的 `WordPairView` 列表。
    - `created`：成功创建的条目数量。
  - 未登录：`401 Unauthorized`。
- **逻辑要点**：`WordPairService.bulkImport` 对每条进行验证，失败的项目会被跳过。

## GET `/api/games/word-pairs/export/`
- **说明**：导出满足条件的词条集合，用于前端下载。
- **查询参数**：与 `list` 相同。
- **返回**：`200 OK`，`ExportResponse`：
  - `items`：`WordPairView` 数组。
- **逻辑要点**：返回 JSON，可由前端转存为文件。

## PATCH `/api/games/word-pairs/{id}/`
- **说明**：更新指定词条，需登录。
- **请求体**：同 `WordPairPayload`。
- **返回**：
  - 成功：`200 OK`，更新后的 `WordPairView`。
  - 未登录：`401 Unauthorized`。
- **逻辑要点**：`WordPairService.update` 覆盖字段，确保目标存在。

## DELETE `/api/games/word-pairs/{id}/`
- **说明**：删除词条，需登录。
- **返回**：
  - 成功：`204 No Content`。
  - 未登录：`401 Unauthorized`。
- **逻辑要点**：`WordPairService.delete` 物理删除记录。

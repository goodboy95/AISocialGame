# CommunityController 接口说明

基址：`/api/community`

## GET /posts
- **用途**：获取最新社区动态（按创建时间倒序，最多 50 条）。
- **响应 200**：`CommunityPost[]`
  - 字段：`id`、`authorName`、`authorId`、`avatar`、`content`、`tags[]`、`likes`、`comments`、`createdAt`

## POST /posts
- **用途**：发布社区动态。
- **请求头**：`X-Auth-Token` 可选；未登录时可传 `X-Guest-Name` 作为游客昵称。
- **请求体**
```json
{ "content": "今晚的卧底太精彩", "tags": ["谁是卧底"] }
```
- **响应 200**：新建的 `CommunityPost`
- **错误**：400 内容为空/超长。

## POST /posts/{id}/like
- **用途**：为帖子点赞（简单计数）。
- **响应 200**：更新后的 `CommunityPost`
- **错误**：404 帖子不存在。

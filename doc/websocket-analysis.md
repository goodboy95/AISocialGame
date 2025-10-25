# WebSocket Communication Analysis

This document analyzes the WebSocket communication mechanism between the frontend and backend of the project.

## Backend Analysis

The backend uses Spring WebSocket to handle real-time communication.

- **Configuration**: [`RealtimeWebSocketConfig.java`](../backend/src/main/java/com/aisocialgame/backend/realtime/RealtimeWebSocketConfig.java) enables WebSocket and registers the handler.
- **Endpoint**: The WebSocket endpoint is exposed at `/ws/rooms/{roomId}` and `/api/ws/rooms/{roomId}`.
- **Handler**: [`RoomSocketHandler.java`](../backend/src/main/java/com/aisocialgame/backend/realtime/RoomSocketHandler.java) is the main message handler.
    - `afterConnectionEstablished`: Handles new connections, authenticates the user, and registers the client.
    - `handleTextMessage`: Processes incoming JSON messages based on the `type` field.
    - `afterConnectionClosed`: Handles disconnections and cleanup.
- **Coordinator**: `RoomSocketCoordinator` contains the core business logic for managing rooms, players, and broadcasting events.

## Frontend Analysis

The frontend uses a custom `RoomRealtimeClient` to manage the WebSocket connection.

- **Client**: [`frontend/src/services/realtime.ts`](../frontend/src/services/realtime.ts) contains the `RoomRealtimeClient` class.
- **Connection URL**: The client determines the WebSocket base URL from environment variables (`VITE_WS_BASE_URL`, `VITE_API_BASE_URL`) or the current page location.
- **Connection**: The `connect(path)` method builds the final URL (e.g., `wss://.../ws/rooms/{roomId}?token=...`) and establishes the connection.

## Interaction Flow

### Summary

1.  **Connection**: The frontend initiates a WebSocket connection to the backend's endpoint, passing a JWT token for authentication.
2.  **Authentication**: The backend's `WebSocketAccessTokenInterceptor` validates the token.
3.  **Registration**: `RoomSocketHandler` registers the new client with `RoomSocketCoordinator`. The coordinator sends an initial state snapshot to the client and broadcasts a "join" event to the room.
4.  **Communication**:
    - **Client-to-Server**: The client sends JSON messages with a `type` field. `RoomSocketHandler` delegates the message to `RoomSocketCoordinator` based on the type.
    - **Server-to-Client**: `RoomSocketCoordinator` broadcasts game state changes and other events to all clients in a room.
5.  **Disconnection**: When a client disconnects, `RoomSocketHandler` and `RoomSocketCoordinator` clean up the session and notify other players in the room.

### Sequence Diagram

```mermaid
sequenceDiagram
    participant Client as 客户端 (Frontend)
    participant Server as 服务器 (Backend)
    participant Interceptor as WebSocketAccessTokenInterceptor
    participant Handler as RoomSocketHandler
    participant Coordinator as RoomSocketCoordinator

    Client->>Server: 发起 WebSocket 连接请求 (wss://.../ws/rooms/{roomId}?token=...)
    Server->>Interceptor: 拦截请求
    Interceptor->>Interceptor: 验证 Token
    Interceptor-->>Server: 验证通过，将用户信息放入 Session
    Server->>Handler: 路由到 RoomSocketHandler
    Handler->>Handler: afterConnectionEstablished()
    Handler->>Coordinator: register(room, user, session)
    Coordinator->>Client: 发送房间初始快照
    Coordinator->>Coordinator: 广播 "成员加入" 事件给房间内其他客户端
    Coordinator-->>Handler: 
    Handler-->>Server: 
    Server-->>Client: 连接建立成功

    loop 双向通信
        Client->>Server: 发送消息 (e.g., {type: 'chat.message', ...})
        Server->>Handler: handleTextMessage()
        Handler->>Coordinator: publishPublicChat(client, message)
        Coordinator->>Coordinator: 广播消息给房间内所有客户端
        Coordinator-->>Handler: 
        Handler-->>Server: 

        Server-->>Client: 接收广播的消息
    end

    Client->>Server: 关闭连接
    Server->>Handler: afterConnectionClosed()
    Handler->>Coordinator: unregister(session)
    Coordinator->>Coordinator: 广播 "成员离开" 事件给房间内其他客户端
    Coordinator-->>Handler:
    Handler-->>Server:
    Server-->>Client: 连接关闭
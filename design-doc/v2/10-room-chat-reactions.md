# 模块 10：房间聊天与快捷表情

> 优先级：P1 | 阶段：第二阶段 | 依赖：模块 01（WebSocket） | 被依赖：无

## 1. 背景与目标

当前 Lobby 页面的聊天区域显示"暂未接入房间聊天"。游戏内的交流仅限于轮到自己时的正式发言，没有自由聊天通道。

目标：实现房间内实时聊天（等待阶段 + 游戏进行中），支持文字消息和快捷表情反应，增强社交互动感。

## 2. 功能设计

| 功能 | 说明 |
|------|------|
| 文字聊天 | 房间内所有玩家可自由发送文字消息 |
| 快捷表情 | 预设表情按钮，一键发送（不打断游戏流程） |
| 快捷短语 | 预设常用语句（"我同意"、"有点可疑"等） |
| 气泡反应 | 表情以气泡形式浮现在发送者头像旁 |
| 聊天限制 | 游戏特定阶段可限制聊天（如狼人杀夜晚） |
| 消息频率限制 | 防刷屏，每人每 3 秒最多 1 条 |

## 3. 消息模型

```java
public record ChatMessage(
    String id,
    String roomId,
    String senderId,
    String senderName,
    String senderAvatar,
    String type,          // TEXT | EMOJI | QUICK_PHRASE | SYSTEM
    String content,       // 文字内容或表情代码
    long timestamp
) {}
```

## 4. 后端实现

### 4.1 WebSocket 消息处理

```java
@Controller
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRateLimiter rateLimiter;

    @MessageMapping("/room/{roomId}/chat")
    public void handleChat(@DestinationVariable String roomId,
                           @Payload ChatMessage message,
                           Principal principal) {
        // 频率限制
        if (!rateLimiter.allowMessage(principal.getName())) {
            return; // 静默丢弃
        }

        // 阶段限制检查
        if (!isChatAllowed(roomId, message.type())) {
            return;
        }

        // 内容清理
        ChatMessage sanitized = new ChatMessage(
            UUID.randomUUID().toString(),
            roomId,
            principal.getName(),
            message.senderName(),
            message.senderAvatar(),
            message.type(),
            sanitizeContent(message.content()),
            System.currentTimeMillis()
        );

        // 广播到房间
        messagingTemplate.convertAndSend(
            "/topic/room/" + roomId + "/chat", sanitized);
    }

    private boolean isChatAllowed(String roomId, String messageType) {
        // 表情和快捷短语在任何阶段都允许
        if ("EMOJI".equals(messageType) || "QUICK_PHRASE".equals(messageType)) {
            return true;
        }
        // 文字聊天在夜晚阶段禁止（狼人杀）
        GameState state = gameStateRepository.findById(roomId).orElse(null);
        if (state != null && "NIGHT".equals(state.getPhase())) {
            return false;
        }
        return true;
    }

    private String sanitizeContent(String content) {
        if (content == null) return "";
        return content.trim().substring(0, Math.min(content.length(), 200));
    }
}
```

### 4.2 频率限制

```java
@Component
public class ChatRateLimiter {
    private final ConcurrentHashMap<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static final long MIN_INTERVAL_MS = 3000; // 3 秒

    public boolean allowMessage(String playerId) {
        long now = System.currentTimeMillis();
        Long last = lastMessageTime.get(playerId);
        if (last != null && now - last < MIN_INTERVAL_MS) {
            return false;
        }
        lastMessageTime.put(playerId, now);
        return true;
    }
}
```

## 5. 预设内容

### 5.1 快捷表情

```typescript
const QUICK_EMOJIS = [
  { code: "thumbsup", emoji: "👍", label: "赞" },
  { code: "think", emoji: "🤔", label: "可疑" },
  { code: "laugh", emoji: "😂", label: "笑" },
  { code: "shock", emoji: "😱", label: "震惊" },
  { code: "angry", emoji: "😡", label: "生气" },
  { code: "cry", emoji: "😭", label: "哭" },
  { code: "cool", emoji: "😎", label: "酷" },
  { code: "skull", emoji: "💀", label: "完了" },
];
```

### 5.2 快捷短语

```typescript
const QUICK_PHRASES: Record<string, string[]> = {
  general: [
    "我同意", "我反对", "等一下", "快点",
    "有道理", "不太对", "继续说", "好的",
  ],
  undercover: [
    "这个描述很可疑", "我觉得他是卧底",
    "我的词很好描述", "大家注意听",
  ],
  werewolf: [
    "我是好人", "查一下他", "跟票",
    "我觉得他是狼", "保护这个人", "先不急",
  ],
};
```

## 6. 前端实现

### 6.1 聊天面板组件

```tsx
// components/game/ChatPanel.tsx
const ChatPanel = ({ roomId, gameId, playerId }: Props) => {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [showEmojis, setShowEmojis] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const { client } = useGameSocket();

  // 接收消息
  useEffect(() => {
    if (!client.current) return;
    const sub = client.current.subscribe(
      `/topic/room/${roomId}/chat`,
      (msg) => {
        const chatMsg = JSON.parse(msg.body);
        setMessages(prev => [...prev.slice(-100), chatMsg]); // 保留最近 100 条
      }
    );
    return () => sub.unsubscribe();
  }, [client, roomId]);

  // 自动滚动到底部
  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  const sendMessage = (type: string, content: string) => {
    client.current?.publish({
      destination: `/app/room/${roomId}/chat`,
      body: JSON.stringify({ type, content, senderName: displayName, senderAvatar: avatar }),
    });
    if (type === "TEXT") setInput("");
  };

  return (
    <Card className="flex flex-col h-full">
      <div className="p-3 border-b text-sm font-medium flex items-center gap-2">
        <MessageSquare className="h-4 w-4" /> 聊天
      </div>

      {/* 消息列表 */}
      <ScrollArea ref={scrollRef} className="flex-1 p-3">
        <div className="space-y-2">
          {messages.map(msg => (
            <ChatBubble key={msg.id} message={msg} isMe={msg.senderId === playerId} />
          ))}
        </div>
      </ScrollArea>

      {/* 快捷表情栏 */}
      <div className="px-3 py-1 border-t flex gap-1 overflow-x-auto">
        {QUICK_EMOJIS.map(e => (
          <Button key={e.code} variant="ghost" size="sm"
            className="h-8 w-8 p-0 text-lg shrink-0"
            onClick={() => sendMessage("EMOJI", e.code)}>
            {e.emoji}
          </Button>
        ))}
      </div>

      {/* 输入区 */}
      <div className="p-3 border-t flex gap-2">
        <Input value={input} onChange={e => setInput(e.target.value)}
          placeholder="说点什么..."
          onKeyDown={e => e.key === "Enter" && input.trim() && sendMessage("TEXT", input)}
          className="text-sm" />
        <Button size="icon" disabled={!input.trim()}
          onClick={() => sendMessage("TEXT", input)}>
          <Send className="h-4 w-4" />
        </Button>
      </div>
    </Card>
  );
};
```

### 6.2 聊天气泡

```tsx
const ChatBubble = ({ message, isMe }: { message: ChatMessage; isMe: boolean }) => {
  if (message.type === "EMOJI") {
    const emoji = QUICK_EMOJIS.find(e => e.code === message.content);
    return (
      <div className={`flex items-center gap-2 ${isMe ? 'flex-row-reverse' : ''}`}>
        <Avatar className="h-6 w-6">
          <AvatarImage src={message.senderAvatar} />
          <AvatarFallback>{message.senderName[0]}</AvatarFallback>
        </Avatar>
        <motion.span
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          className="text-2xl"
        >
          {emoji?.emoji || message.content}
        </motion.span>
      </div>
    );
  }

  if (message.type === "SYSTEM") {
    return (
      <div className="text-center text-xs text-muted-foreground py-1">
        {message.content}
      </div>
    );
  }

  return (
    <div className={`flex gap-2 ${isMe ? 'flex-row-reverse' : ''}`}>
      <Avatar className="h-6 w-6 shrink-0">
        <AvatarImage src={message.senderAvatar} />
        <AvatarFallback>{message.senderName[0]}</AvatarFallback>
      </Avatar>
      <div className={`max-w-[70%] ${isMe ? 'text-right' : ''}`}>
        <div className="text-[10px] text-muted-foreground mb-0.5">
          {message.senderName}
        </div>
        <div className={`inline-block px-3 py-1.5 rounded-2xl text-sm
          ${isMe ? 'bg-blue-500 text-white' : 'bg-slate-100 text-slate-800'}`}>
          {message.content}
        </div>
      </div>
    </div>
  );
};
```

### 6.3 浮动表情气泡

在玩家头像旁显示最近发送的表情（2 秒后消失）：

```tsx
// components/game/FloatingEmoji.tsx
const FloatingEmoji = ({ emoji, position }: { emoji: string; position: { x: number; y: number } }) => (
  <motion.div
    initial={{ opacity: 1, y: 0, scale: 0.5 }}
    animate={{ opacity: 0, y: -40, scale: 1.2 }}
    transition={{ duration: 2 }}
    className="absolute text-2xl pointer-events-none z-30"
    style={{ left: position.x, top: position.y }}
  >
    {emoji}
  </motion.div>
);
```

## 7. 聊天与游戏日志的关系

| 场景 | 聊天面板 | 游戏日志 |
|------|----------|----------|
| 等待阶段 | 自由聊天 | 显示座位变动 |
| 发言阶段 | 自由聊天（不影响正式发言） | 显示正式发言 |
| 投票阶段 | 自由聊天 | 显示投票记录 |
| 夜晚阶段 | 仅表情/快捷短语 | 显示夜晚事件 |
| 结算阶段 | 自由聊天 | 显示结算信息 |

聊天消息和游戏日志是两个独立的区域，互不干扰。

## 8. 布局调整

游戏房间页面需要调整布局以容纳聊天面板：

```
桌面端：
┌──────────────────────────────────┬──────────────┐
│  游戏主区域                       │  聊天面板     │
│  (玩家列表 + 操作区 + 游戏日志)    │  (右侧固定)   │
│                                  │              │
└──────────────────────────────────┴──────────────┘

移动端：
┌──────────────────────────────────┐
│  游戏主区域                       │
│  (玩家列表 + 操作区)              │
├──────────────────────────────────┤
│  Tab: 游戏日志 | 聊天             │
│  (底部切换)                       │
└──────────────────────────────────┘
```

## 9. 数据库变更

无。聊天消息不持久化，仅通过 WebSocket 实时传输。如果未来需要聊天记录，可以加一张 `chat_messages` 表。

## 10. 测试要点

- [ ] 文字消息发送与接收
- [ ] 快捷表情发送与气泡显示
- [ ] 频率限制（3 秒内重复发送被拦截）
- [ ] 夜晚阶段文字聊天被禁止
- [ ] 消息列表自动滚动
- [ ] 多人同时发送消息的顺序一致性
- [ ] 移动端布局切换

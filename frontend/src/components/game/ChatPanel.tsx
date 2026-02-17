import { useEffect, useMemo, useRef, useState } from "react";
import { ChatMessage } from "@/types";
import { Card } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { MessageSquare, Send } from "lucide-react";

const QUICK_EMOJIS = ["👍", "🤔", "😂", "😱", "😡", "😭", "😎", "💀"];
const QUICK_PHRASES = ["我同意", "有点可疑", "等等", "继续说", "我反对", "快投票"];

interface ChatPanelProps {
  messages: ChatMessage[];
  myPlayerId?: string;
  onSend: (type: "TEXT" | "EMOJI" | "QUICK_PHRASE", content: string) => void;
}

export const ChatPanel = ({ messages, myPlayerId, onSend }: ChatPanelProps) => {
  const [input, setInput] = useState("");
  const endRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages]);

  const recent = useMemo(() => messages.slice(-100), [messages]);

  return (
    <Card className="flex h-full min-h-[420px] flex-col">
      <div className="border-b px-3 py-2 text-sm font-medium flex items-center gap-2">
        <MessageSquare className="h-4 w-4" /> 房间聊天
      </div>

      <ScrollArea className="flex-1 px-3 py-2">
        <div className="space-y-2">
          {recent.map((msg) => {
            const isMe = myPlayerId && msg.senderId === myPlayerId;
            const isEmoji = msg.type === "EMOJI";
            return (
              <div key={msg.id} className={`flex gap-2 ${isMe ? "flex-row-reverse" : ""}`}>
                <Avatar className="h-6 w-6 shrink-0">
                  <AvatarImage src={msg.senderAvatar} />
                  <AvatarFallback>{msg.senderName?.[0] || "?"}</AvatarFallback>
                </Avatar>
                <div className={`max-w-[72%] ${isMe ? "text-right" : ""}`}>
                  <div className="mb-0.5 text-[10px] text-muted-foreground">{msg.senderName}</div>
                  {isEmoji ? (
                    <span className="inline-block rounded-full bg-slate-100 px-3 py-1 text-2xl">{msg.content}</span>
                  ) : (
                    <span className={`inline-block rounded-2xl px-3 py-1.5 text-sm ${isMe ? "bg-blue-500 text-white" : "bg-slate-100 text-slate-900"}`}>
                      {msg.content}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
          <div ref={endRef} />
          {recent.length === 0 && <div className="text-sm text-muted-foreground">暂无聊天消息</div>}
        </div>
      </ScrollArea>

      <div className="border-t px-3 py-2">
        <div className="mb-2 flex gap-1 overflow-x-auto pb-1">
          {QUICK_EMOJIS.map((emoji) => (
            <Button key={emoji} variant="ghost" size="sm" className="h-8 w-8 p-0 text-lg" onClick={() => onSend("EMOJI", emoji)}>
              {emoji}
            </Button>
          ))}
        </div>

        <div className="mb-2 flex flex-wrap gap-1">
          {QUICK_PHRASES.map((phrase) => (
            <Button key={phrase} variant="outline" size="sm" className="h-7 text-xs" onClick={() => onSend("QUICK_PHRASE", phrase)}>
              {phrase}
            </Button>
          ))}
        </div>

        <div className="flex gap-2">
          <Input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="说点什么..."
            onKeyDown={(e) => {
              if (e.key === "Enter" && input.trim()) {
                onSend("TEXT", input.trim());
                setInput("");
              }
            }}
          />
          <Button
            size="icon"
            disabled={!input.trim()}
            onClick={() => {
              if (!input.trim()) return;
              onSend("TEXT", input.trim());
              setInput("");
            }}
          >
            <Send className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </Card>
  );
};

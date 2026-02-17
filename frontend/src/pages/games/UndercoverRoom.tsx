import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { gameplayApi, personaApi, roomApi } from "@/services/api";
import { useAuth } from "@/hooks/useAuth";
import { useGameSocket } from "@/hooks/useGameSocket";
import { ChatMessage, GameState } from "@/types";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ChatPanel } from "@/components/game/ChatPanel";
import { ConnectionStatusBar } from "@/components/game/ConnectionStatusBar";
import { CountdownTimer } from "@/components/game/CountdownTimer";
import { PhaseTransition } from "@/components/game/PhaseTransition";
import { Bot, CheckSquare, Play, Send, WifiOff } from "lucide-react";
import { toast } from "sonner";

const playerKey = (roomId?: string) => (roomId ? `room_player_${roomId}` : "room_player");

const UndercoverRoom = () => {
  const { roomId, gameId } = useParams();
  const queryClient = useQueryClient();
  const { displayName, token } = useAuth();
  const [playerId, setPlayerId] = useState<string | null>(() => (roomId ? localStorage.getItem(playerKey(roomId)) : null));
  const [speakContent, setSpeakContent] = useState("");
  const [selectedVote, setSelectedVote] = useState<string | null>(null);
  const [selectedAiId, setSelectedAiId] = useState<string>("");
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [showTransition, setShowTransition] = useState(false);
  const prevPhaseRef = useRef<string | undefined>();

  useEffect(() => {
    if (!roomId) {
      return;
    }
    setPlayerId(localStorage.getItem(playerKey(roomId)));
  }, [roomId]);

  const { data: personas = [] } = useQuery({
    queryKey: ["personas"],
    queryFn: personaApi.list,
  });

  const { data: room } = useQuery({
    queryKey: ["room", roomId],
    queryFn: () => roomApi.detail(gameId || "", roomId || ""),
    enabled: !!roomId && !!gameId,
  });

  const stateQuery = useQuery<GameState>({
    queryKey: ["game-state", roomId],
    queryFn: () => gameplayApi.state(gameId || "undercover", roomId || "", playerId || undefined),
    enabled: !!roomId && !!gameId,
    refetchInterval: false,
  });

  const socket = useGameSocket({
    roomId,
    playerId,
    token,
    onStateChange: () => {
      queryClient.invalidateQueries({ queryKey: ["game-state", roomId] });
    },
    onSeatChange: () => {
      queryClient.invalidateQueries({ queryKey: ["room", roomId] });
      queryClient.invalidateQueries({ queryKey: ["game-state", roomId] });
    },
    onPrivate: () => {
      queryClient.invalidateQueries({ queryKey: ["game-state", roomId] });
    },
    onChat: (msg) => {
      setChatMessages((prev) => [...prev.slice(-99), msg]);
    },
  });

  useEffect(() => {
    if (playerId && roomId) {
      stateQuery.refetch();
    }
  }, [playerId]);

  useEffect(() => {
    if (!stateQuery.data?.phase) {
      return;
    }
    if (stateQuery.data.phase !== prevPhaseRef.current && stateQuery.data.phase !== "WAITING") {
      setShowTransition(true);
      const timer = window.setTimeout(() => setShowTransition(false), 1800);
      prevPhaseRef.current = stateQuery.data.phase;
      return () => clearTimeout(timer);
    }
  }, [stateQuery.data?.phase]);

  useEffect(() => {
    if (personas.length > 0 && !selectedAiId) {
      setSelectedAiId(personas[0].id);
    }
  }, [personas, selectedAiId]);

  const joinMutation = useMutation({
    mutationFn: () => roomApi.join(gameId || "", roomId || "", displayName, playerId || undefined),
    onSuccess: (data) => {
      const pid = (data as any).selfPlayerId;
      if (pid && roomId) {
        setPlayerId(pid);
        localStorage.setItem(playerKey(roomId), pid);
        queryClient.invalidateQueries({ queryKey: ["game-state", roomId] });
      }
    },
    onError: () => toast.error("加入房间失败"),
  });

  useEffect(() => {
    if (room && !joinMutation.isSuccess && !joinMutation.isPending) {
      joinMutation.mutate();
    }
  }, [room]);

  const startMutation = useMutation({
    mutationFn: () => gameplayApi.start(gameId || "undercover", roomId || "", playerId || undefined),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["game-state", roomId] }),
    onError: (err: any) => toast.error(err?.response?.data?.message || "开局失败"),
  });

  const speakMutation = useMutation({
    mutationFn: () => gameplayApi.speak(gameId || "undercover", roomId || "", speakContent || "我已描述完毕", playerId || undefined),
    onSuccess: () => {
      setSpeakContent("");
      queryClient.invalidateQueries({ queryKey: ["game-state", roomId] });
    },
    onError: () => toast.error("发言提交失败"),
  });

  const voteMutation = useMutation({
    mutationFn: () => gameplayApi.vote(gameId || "undercover", roomId || "", selectedVote || "", false, playerId || undefined),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["game-state", roomId] }),
    onError: () => toast.error("投票失败"),
  });

  const addAiMutation = useMutation({
    mutationFn: (personaId: string) => roomApi.addAi(gameId || "", roomId || "", personaId),
    onSuccess: () => {
      toast.success("AI 已加入");
      queryClient.invalidateQueries({ queryKey: ["room", roomId] });
      queryClient.invalidateQueries({ queryKey: ["game-state", roomId] });
    },
    onError: () => toast.error("添加 AI 失败"),
  });

  const players = stateQuery.data?.players || [];
  const alivePlayers = useMemo(() => players.filter((p) => p.alive), [players]);
  const currentSpeaker = players.find((p) => p.seatNumber === stateQuery.data?.currentSeat);
  const phase = stateQuery.data?.phase || "WAITING";

  const canSpeak = phase === "DESCRIPTION" && stateQuery.data?.mySeatNumber === stateQuery.data?.currentSeat;
  const canVote = phase === "VOTING" && !!selectedVote;

  return (
    <>
      <ConnectionStatusBar connected={socket.connected} showReconnectAction={socket.showReconnectAction} onReconnect={socket.reconnect} />
      <PhaseTransition gameId={gameId} phase={phase} visible={showTransition} />

      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-xl font-bold">{room?.name || "卧底房间"}</h2>
            <p className="text-sm text-muted-foreground">
              阶段：{phase} {currentSpeaker ? `• 当前发言：${currentSpeaker.displayName}` : ""} {stateQuery.data?.round ? `• 第${stateQuery.data.round}轮` : ""}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <CountdownTimer phaseEndsAt={stateQuery.data?.phaseEndsAt} />
            <Badge variant="secondary">
              {alivePlayers.length}/{players.length} 存活
            </Badge>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 xl:grid-cols-4">
          <div className="space-y-4 xl:col-span-3">
            <Card className="p-4">
              <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                {players.map((p) => (
                  <div key={p.playerId} className={`flex items-center gap-3 rounded-lg border p-2 ${p.alive ? "border-slate-200" : "border-red-200 bg-red-50"}`}>
                    <Avatar className="h-10 w-10">
                      <AvatarImage src={p.avatar} />
                      <AvatarFallback>{p.displayName[0]}</AvatarFallback>
                    </Avatar>
                    <div className="min-w-0 flex-1">
                      <div className="truncate font-medium">{p.displayName}</div>
                      <div className="text-xs text-muted-foreground">座位 {p.seatNumber + 1}</div>
                    </div>
                    {!p.alive && <Badge variant="destructive">出局</Badge>}
                    {p.connectionStatus === "DISCONNECTED" && <WifiOff className="h-4 w-4 text-slate-400" />}
                    {p.connectionStatus === "AI_TAKEOVER" && <Badge variant="outline" className="text-amber-600">托管</Badge>}
                    {stateQuery.data?.currentSeat === p.seatNumber && phase === "DESCRIPTION" && <Badge variant="outline">发言中</Badge>}
                  </div>
                ))}
              </div>

              <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-3">
                <Card className="border-dashed p-3">
                  <div className="mb-2 text-xs text-muted-foreground">我的词语</div>
                  <div className="text-lg font-bold">{stateQuery.data?.myWord || "等待发牌"}</div>
                  <div className="mt-1 text-xs text-muted-foreground">{stateQuery.data?.myRole === "UNDERCOVER" ? "你的身份：卧底" : "谨慎描述，避免暴露身份"}</div>
                </Card>

                <Card className="p-3">
                  <div className="mb-2 flex items-center justify-between">
                    <span className="text-sm font-medium">操作区</span>
                    <Badge variant="outline">{phase}</Badge>
                  </div>
                  {phase === "WAITING" && (
                    <div className="space-y-2">
                      <p className="text-sm text-muted-foreground">满足人数后房主可以开局。</p>
                      <Button onClick={() => startMutation.mutate()} disabled={startMutation.isPending} className="w-full">
                        <Play className="mr-2 h-4 w-4" /> 开始游戏
                      </Button>
                    </div>
                  )}
                  {canSpeak && (
                    <div className="space-y-2">
                      <Input value={speakContent} onChange={(e) => setSpeakContent(e.target.value)} placeholder="输入你的描述" />
                      <Button className="w-full" onClick={() => speakMutation.mutate()} disabled={speakMutation.isPending}>
                        <Send className="mr-2 h-4 w-4" /> 提交发言
                      </Button>
                    </div>
                  )}
                  {phase === "VOTING" && (
                    <div className="space-y-2">
                      <Select value={selectedVote || undefined} onValueChange={setSelectedVote}>
                        <SelectTrigger>
                          <SelectValue placeholder="选择要投出的玩家" />
                        </SelectTrigger>
                        <SelectContent>
                          {alivePlayers
                            .filter((p) => p.playerId !== stateQuery.data?.myPlayerId)
                            .map((p) => (
                              <SelectItem key={p.playerId} value={p.playerId}>
                                {p.displayName}
                              </SelectItem>
                            ))}
                        </SelectContent>
                      </Select>
                      <Button className="w-full" disabled={!canVote || voteMutation.isPending} onClick={() => voteMutation.mutate()}>
                        <CheckSquare className="mr-2 h-4 w-4" /> 投票
                      </Button>
                    </div>
                  )}
                  {phase === "SETTLEMENT" && <div className="text-sm text-muted-foreground">对局结束，获胜方：{stateQuery.data?.winner || "未判定"}。</div>}
                  {!canSpeak && phase === "DESCRIPTION" && <div className="text-sm text-muted-foreground">等待 {currentSpeaker?.displayName || "玩家"} 发言...</div>}
                </Card>

                <Card className="space-y-3 p-3">
                  <div className="flex items-center justify-between text-sm">
                    <span>添加 AI 补位</span>
                    <Badge>
                      {room?.seats?.length ?? 0}/{room?.maxPlayers}
                    </Badge>
                  </div>
                  <Select value={selectedAiId} onValueChange={setSelectedAiId}>
                    <SelectTrigger>
                      <SelectValue placeholder="选择 AI 人设" />
                    </SelectTrigger>
                    <SelectContent>
                      {personas.map((ai) => (
                        <SelectItem key={ai.id} value={ai.id}>
                          {ai.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Button variant="secondary" onClick={() => addAiMutation.mutate(selectedAiId)} disabled={!selectedAiId}>
                    <Bot className="mr-2 h-4 w-4" /> 添加 AI
                  </Button>
                </Card>
              </div>
            </Card>

            <Card className="p-4">
              <div className="mb-3 flex items-center justify-between">
                <h3 className="font-semibold">游戏日志</h3>
                <Badge variant="outline">实时</Badge>
              </div>
              <ScrollArea className="h-64 pr-2">
                <div className="space-y-2 text-sm">
                  {(stateQuery.data?.logs || []).map((log, idx) => (
                    <div key={idx} className="flex items-center gap-2">
                      <span className="text-xs text-muted-foreground">{new Date(log.time).toLocaleTimeString()}</span>
                      <span>{log.message}</span>
                    </div>
                  ))}
                  {(!stateQuery.data?.logs || stateQuery.data.logs.length === 0) && <div className="text-sm text-muted-foreground">暂无日志，等待开局或操作。</div>}
                </div>
              </ScrollArea>
            </Card>
          </div>

          <ChatPanel
            messages={chatMessages}
            myPlayerId={stateQuery.data?.myPlayerId}
            onSend={(type, content) => {
              const sent = socket.sendChat(type, content);
              if (!sent) {
                toast.error("聊天发送失败，连接未就绪");
              }
            }}
          />
        </div>
      </div>
    </>
  );
};

export default UndercoverRoom;

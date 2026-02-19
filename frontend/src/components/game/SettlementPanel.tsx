import { useMemo } from "react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { FriendItem, GameState } from "@/types";
import { friendApi } from "@/services/v2Social";
import { toast } from "sonner";
import { UserPlus } from "lucide-react";

interface SettlementPanelProps {
  gameId?: string;
  state: GameState;
  userKey: string;
}

export const SettlementPanel = ({ gameId, state, userKey }: SettlementPanelProps) => {
  const cards = useMemo(() => state.players || [], [state.players]);

  return (
    <Card className="space-y-4 border-purple-200 bg-gradient-to-br from-violet-50 via-slate-50 to-amber-50 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-lg font-semibold">结算揭秘</h3>
          <p className="text-sm text-muted-foreground">
            {gameId} 对局结束，获胜方：{state.winner || "未判定"}
          </p>
        </div>
        <Badge className="bg-purple-600">{state.winner || "结算中"}</Badge>
      </div>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3">
        {cards.map((player) => (
          <div key={player.playerId} className="rounded-lg border bg-white/90 p-3">
            <div className="flex items-center gap-2">
              <Avatar className="h-9 w-9">
                <AvatarImage src={player.avatar} />
                <AvatarFallback>{player.displayName.slice(0, 1)}</AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <div className="truncate font-medium">{player.displayName}</div>
                <div className="text-xs text-muted-foreground">座位 {player.seatNumber + 1}</div>
              </div>
              <Badge variant={player.alive ? "secondary" : "destructive"}>{player.alive ? "存活" : "出局"}</Badge>
            </div>
            <div className="mt-2 flex flex-wrap gap-2 text-xs">
              {player.role && <Badge variant="outline">{player.role}</Badge>}
              {(player as any).word && <Badge variant="outline">{(player as any).word}</Badge>}
            </div>
            {player.playerId !== state.myPlayerId && !player.ai && (
              <Button
                size="sm"
                variant="outline"
                className="mt-3 w-full"
                onClick={() => {
                  const target: FriendItem = {
                    id: player.playerId,
                    displayName: player.displayName,
                    avatar: player.avatar,
                    online: false,
                  };
                  friendApi.sendFriendRequest(userKey, target);
                  toast.success(`已向 ${player.displayName} 发送好友请求`);
                }}
              >
                <UserPlus className="mr-1 h-3 w-3" />
                加好友
              </Button>
            )}
          </div>
        ))}
      </div>
    </Card>
  );
};

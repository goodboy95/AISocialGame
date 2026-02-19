import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { friendApi } from "@/services/v2Social";
import { FriendItem, FriendRequestItem } from "@/types";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { toast } from "sonner";
import { Eye, Plus, UserMinus, Users } from "lucide-react";

interface FriendPanelProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  userKey: string;
}

export const FriendPanel = ({ open, onOpenChange, userKey }: FriendPanelProps) => {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState("");
  const [friends, setFriends] = useState<FriendItem[]>([]);
  const [requests, setRequests] = useState<FriendRequestItem[]>([]);

  const refresh = () => {
    const panel = friendApi.getPanelData(userKey);
    setFriends(panel.friends);
    setRequests(panel.requests);
  };

  useEffect(() => {
    refresh();
    const timer = window.setInterval(refresh, 30000);
    return () => window.clearInterval(timer);
  }, [userKey]);

  const candidates = useMemo(() => friendApi.searchCandidates(userKey, keyword).slice(0, 6), [userKey, keyword, friends, requests]);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full p-0 sm:max-w-[420px]">
        <SheetHeader className="border-b px-4 py-3 text-left">
          <SheetTitle className="flex items-center gap-2">
            <Users className="h-4 w-4" />
            好友
          </SheetTitle>
          <SheetDescription>管理好友关系、处理好友请求，并可一键邀请或观战。</SheetDescription>
        </SheetHeader>

        <div className="space-y-4 p-4">
          <div className="space-y-2">
            <Input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索可添加用户..." />
            {keyword.trim() && (
              <div className="space-y-2 rounded-lg border bg-slate-50 p-2">
                {candidates.length === 0 && <div className="px-1 py-2 text-xs text-muted-foreground">没有可添加结果</div>}
                {candidates.map((candidate) => (
                  <div key={candidate.id} className="flex items-center justify-between rounded-md bg-white px-2 py-2">
                    <div className="flex items-center gap-2">
                      <Avatar className="h-8 w-8">
                        <AvatarImage src={candidate.avatar} />
                        <AvatarFallback>{candidate.displayName.slice(0, 1)}</AvatarFallback>
                      </Avatar>
                      <div>
                        <div className="text-sm font-medium">{candidate.displayName}</div>
                        <div className="text-xs text-muted-foreground">{candidate.id}</div>
                      </div>
                    </div>
                    <Button
                      size="sm"
                      onClick={() => {
                        friendApi.sendFriendRequest(userKey, candidate);
                        toast.success(`已发送好友请求给 ${candidate.displayName}`);
                        refresh();
                      }}
                    >
                      <Plus className="mr-1 h-3 w-3" />
                      添加
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </div>

          <section className="space-y-2">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold">好友请求</h3>
              <Badge variant="secondary">{requests.length}</Badge>
            </div>
            <ScrollArea className="h-[140px] rounded-md border p-2">
              <div className="space-y-2">
                {requests.length === 0 && <div className="px-1 py-2 text-xs text-muted-foreground">暂无待处理请求</div>}
                {requests.map((request) => (
                  <div key={request.id} className="space-y-2 rounded-md border bg-white p-2">
                    <div className="text-sm">
                      <span className="font-medium">{request.fromName}</span>
                      <span className="text-muted-foreground"> 请求添加你为好友</span>
                    </div>
                    <div className="flex gap-2">
                      <Button
                        size="sm"
                        className="flex-1"
                        onClick={() => {
                          friendApi.respondRequest(userKey, request.id, true);
                          toast.success("已通过好友请求");
                          refresh();
                        }}
                      >
                        通过
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        className="flex-1"
                        onClick={() => {
                          friendApi.respondRequest(userKey, request.id, false);
                          toast("已忽略该请求");
                          refresh();
                        }}
                      >
                        忽略
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </ScrollArea>
          </section>

          <section className="space-y-2">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold">我的好友</h3>
              <Badge variant="outline">{friends.length}</Badge>
            </div>
            <ScrollArea className="h-[280px] rounded-md border p-2">
              <div className="space-y-2">
                {friends.length === 0 && <div className="px-1 py-2 text-xs text-muted-foreground">尚未添加好友</div>}
                {friends.map((friend) => (
                  <div key={friend.id} className="space-y-2 rounded-md border bg-white p-2">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Avatar className="h-8 w-8">
                          <AvatarImage src={friend.avatar} />
                          <AvatarFallback>{friend.displayName.slice(0, 1)}</AvatarFallback>
                        </Avatar>
                        <div>
                          <div className="text-sm font-medium">{friend.displayName}</div>
                          <div className="text-xs text-muted-foreground">{friend.online ? "在线" : "离线"}</div>
                        </div>
                      </div>
                      <Badge variant={friend.online ? "default" : "secondary"}>{friend.online ? "在线" : "离线"}</Badge>
                    </div>
                    <div className="flex gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        className="flex-1"
                        onClick={() => toast.success(`已向 ${friend.displayName} 发送房间邀请`)}
                      >
                        邀请
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        className="flex-1"
                        disabled={!friend.currentGameId || !friend.currentRoomId}
                        onClick={() => {
                          if (!friend.currentGameId || !friend.currentRoomId) return;
                          navigate(`/spectate/${friend.currentGameId}/${friend.currentRoomId}`);
                          onOpenChange(false);
                        }}
                      >
                        <Eye className="mr-1 h-3 w-3" />
                        观战
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="px-2"
                        onClick={() => {
                          friendApi.removeFriend(userKey, friend.id);
                          toast("已删除好友");
                          refresh();
                        }}
                      >
                        <UserMinus className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </ScrollArea>
          </section>
        </div>
      </SheetContent>
    </Sheet>
  );
};

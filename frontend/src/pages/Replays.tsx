import { useMemo } from "react";
import { Link } from "react-router-dom";
import { replayApi } from "@/services/v2Social";
import { useAuth } from "@/hooks/useAuth";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { PlayCircle } from "lucide-react";

const Replays = () => {
  const { user, displayName } = useAuth();
  const userKey = useMemo(() => user?.id || `guest:${displayName}`, [user?.id, displayName]);
  const archives = replayApi.list(userKey);

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">对局回放</h1>
        <p className="text-sm text-muted-foreground">查看你保存的历史对局，复盘关键节点和投票过程。</p>
      </div>

      {archives.length === 0 ? (
        <Card>
          <CardContent className="py-10 text-center text-sm text-muted-foreground">暂无可回放对局，完成结算后会自动生成回放存档。</CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {archives.map((archive) => (
            <Card key={archive.id}>
              <CardHeader className="pb-3">
                <CardTitle className="text-base">{archive.roomName}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex flex-wrap gap-2">
                  <Badge variant="secondary">{archive.gameId}</Badge>
                  <Badge variant="outline">结果: {archive.result}</Badge>
                  <Badge variant="outline">{archive.events.length} 事件</Badge>
                </div>
                <div className="text-xs text-muted-foreground">{new Date(archive.createdAt).toLocaleString()}</div>
                <Button asChild className="w-full">
                  <Link to={`/replay/${archive.id}`}>
                    <PlayCircle className="mr-2 h-4 w-4" />
                    开始回放
                  </Link>
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};

export default Replays;

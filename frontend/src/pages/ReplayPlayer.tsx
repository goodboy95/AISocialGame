import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { replayApi } from "@/services/v2Social";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Pause, Play, SkipForward } from "lucide-react";

const ReplayPlayer = () => {
  const { archiveId } = useParams();
  const { user, displayName } = useAuth();
  const userKey = useMemo(() => user?.id || `guest:${displayName}`, [user?.id, displayName]);
  const archive = archiveId ? replayApi.get(userKey, archiveId) : undefined;
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);

  useEffect(() => {
    if (!playing || !archive) return;
    const timer = window.setInterval(() => {
      setIndex((prev) => {
        if (prev >= archive.events.length - 1) {
          setPlaying(false);
          return prev;
        }
        return prev + 1;
      });
    }, Math.max(250, 1000 / speed));
    return () => window.clearInterval(timer);
  }, [playing, speed, archive?.id]);

  if (!archive) {
    return (
      <Card className="mx-auto max-w-3xl">
        <CardContent className="py-10 text-center text-sm text-muted-foreground">未找到回放数据，请先在“对局回放”列表中选择存档。</CardContent>
      </Card>
    );
  }

  const currentEvent = archive.events[index];

  return (
    <div className="mx-auto max-w-5xl space-y-4">
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-lg">{archive.roomName}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="secondary">{archive.gameId}</Badge>
            <Badge variant="outline">结果: {archive.result}</Badge>
            <Badge variant="outline">
              {index + 1}/{Math.max(archive.events.length, 1)}
            </Badge>
          </div>
          <div className="rounded-lg border bg-slate-50 p-4">
            <div className="mb-1 text-xs text-muted-foreground">{currentEvent?.timestamp ? new Date(currentEvent.timestamp).toLocaleTimeString() : "--:--:--"}</div>
            <div className="text-base font-medium">{currentEvent?.message || "暂无事件"}</div>
          </div>
          <input
            type="range"
            className="w-full"
            min={0}
            max={Math.max(archive.events.length - 1, 0)}
            value={index}
            onChange={(event) => setIndex(Number(event.target.value))}
          />
          <div className="flex flex-wrap items-center gap-2">
            <Button onClick={() => setPlaying((v) => !v)} disabled={archive.events.length === 0}>
              {playing ? <Pause className="mr-2 h-4 w-4" /> : <Play className="mr-2 h-4 w-4" />}
              {playing ? "暂停" : "播放"}
            </Button>
            <Button variant="outline" onClick={() => setIndex((prev) => Math.min(prev + 1, archive.events.length - 1))}>
              <SkipForward className="mr-2 h-4 w-4" />
              单步
            </Button>
            <div className="ml-auto flex items-center gap-2 text-sm">
              <span>速度</span>
              <Button size="sm" variant={speed === 1 ? "default" : "outline"} onClick={() => setSpeed(1)}>
                1x
              </Button>
              <Button size="sm" variant={speed === 2 ? "default" : "outline"} onClick={() => setSpeed(2)}>
                2x
              </Button>
              <Button size="sm" variant={speed === 4 ? "default" : "outline"} onClick={() => setSpeed(4)}>
                4x
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">事件时间线</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {archive.events.map((event, eventIndex) => (
            <div
              key={event.id}
              className={`rounded-md border px-3 py-2 text-sm ${eventIndex === index ? "border-blue-200 bg-blue-50" : "border-slate-200 bg-white"}`}
              onClick={() => setIndex(eventIndex)}
            >
              <div className="text-xs text-muted-foreground">{new Date(event.timestamp).toLocaleTimeString()}</div>
              <div>{event.message}</div>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
};

export default ReplayPlayer;

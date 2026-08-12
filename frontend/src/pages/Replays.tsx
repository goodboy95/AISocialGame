import { useMemo } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { replayApi } from "@/services/v2Social";
import { serverReplayApi } from "@/services/api";
import { useAuth } from "@/hooks/useAuth";
import { useQuery } from "@tanstack/react-query";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Database, PlayCircle } from "lucide-react";

const Replays = () => {
  const { t } = useTranslation();
  const { user, displayName } = useAuth();
  const userKey = useMemo(() => user?.id || `guest:${displayName}`, [user?.id, displayName]);
  const localArchives = replayApi.list(userKey);
  const serverArchives = useQuery({
    queryKey: ["server-replays"],
    queryFn: () => serverReplayApi.my({ size: 50 }),
    retry: 1,
  });
  const archives = serverArchives.data?.items || [];
  const usingFallback = serverArchives.isError || archives.length === 0;

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">{t("replays.title")}</h1>
        <p className="text-sm text-muted-foreground">{t("replays.subtitle")}</p>
      </div>

      {!usingFallback && archives.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {archives.map((archive) => (
            <Card key={archive.id}>
              <CardHeader className="pb-3">
                <CardTitle className="text-base">{archive.roomName}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex flex-wrap gap-2">
                  <Badge variant="secondary">{archive.gameId}</Badge>
                  <Badge variant="outline">{t("replays.winner", { winner: archive.winner || t("common.undetermined") })}</Badge>
                  <Badge variant="outline">{t("replays.events", { count: archive.eventCount })}</Badge>
                </div>
                <div className="grid grid-cols-3 gap-2 rounded-md border bg-slate-50 p-2 text-center text-xs">
                  <div>
                    <div className="font-semibold">{archive.playerCount}</div>
                    <div className="text-muted-foreground">{t("replays.players")}</div>
                  </div>
                  <div>
                    <div className="font-semibold">{archive.totalRounds}</div>
                    <div className="text-muted-foreground">{t("replays.rounds")}</div>
                  </div>
                  <div>
                    <div className="font-semibold">{archive.aiQualitySummary?.traceCount ?? 0}</div>
                    <div className="text-muted-foreground">AI Trace</div>
                  </div>
                </div>
                <div className="flex items-center gap-1 text-xs text-muted-foreground">
                  <Database className="h-3.5 w-3.5" />
                  {archive.finishedAt ? new Date(archive.finishedAt).toLocaleString() : t("replays.serverArchive")}
                </div>
                <Button asChild className="w-full">
                  <Link to={`/replay/${archive.id}`}>
                    <PlayCircle className="mr-2 h-4 w-4" />
                    {t("replays.play")}
                  </Link>
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : localArchives.length === 0 ? (
        <Card>
          <CardContent className="py-10 text-center text-sm text-muted-foreground">
            {serverArchives.isLoading ? t("replays.loading") : t("replays.empty")}
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">{t("replays.fallbackNotice")}</div>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {localArchives.map((archive) => (
            <Card key={archive.id}>
              <CardHeader className="pb-3">
                <CardTitle className="text-base">{archive.roomName}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex flex-wrap gap-2">
                  <Badge variant="secondary">{archive.gameId}</Badge>
                  <Badge variant="outline">{t("replays.result", { result: archive.result })}</Badge>
                  <Badge variant="outline">{t("replays.events", { count: archive.events.length })}</Badge>
                </div>
                <div className="text-xs text-muted-foreground">{new Date(archive.createdAt).toLocaleString()}</div>
                <Button asChild className="w-full">
                  <Link to={`/replay/${archive.id}`}>
                    <PlayCircle className="mr-2 h-4 w-4" />
                    {t("replays.play")}
                  </Link>
                </Button>
              </CardContent>
            </Card>
          ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default Replays;

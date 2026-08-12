import { useMemo } from "react";
import { achievementApi } from "@/services/v2Social";
import { useAuth } from "@/hooks/useAuth";
import { useTranslation } from "react-i18next";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { Award, Lock } from "lucide-react";

const rarityClass: Record<string, string> = {
  COMMON: "bg-slate-100 text-slate-700 border-slate-200",
  RARE: "bg-blue-100 text-blue-700 border-blue-200",
  EPIC: "bg-amber-100 text-amber-700 border-amber-200",
};

const Achievements = () => {
  const { t } = useTranslation();
  const { user, displayName } = useAuth();
  const userKey = useMemo(() => user?.id || `guest:${displayName}`, [user?.id, displayName]);
  const defs = achievementApi.listDefinitions();
  const my = achievementApi.listMyAchievements(userKey);

  const merged = defs.map((def) => {
    const mine = my.find((item) => item.code === def.code);
    return {
      ...def,
      unlocked: mine?.unlocked ?? false,
      progress: mine?.progress ?? 0,
    };
  });

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">{t("achievements.title")}</h1>
        <p className="text-sm text-muted-foreground">{t("achievements.subtitle")}</p>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
        {merged.map((item) => (
          <Card key={item.code} className={item.unlocked ? "border-amber-200 bg-amber-50/30" : ""}>
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between">
                <CardTitle className="flex items-center gap-2 text-base">
                  {item.unlocked ? <Award className="h-4 w-4 text-amber-500" /> : <Lock className="h-4 w-4 text-slate-400" />}
                  {item.name}
                </CardTitle>
                <Badge variant="outline" className={rarityClass[item.rarity]}>
                  {t(`achievements.rarity.${item.rarity}`, { defaultValue: item.rarity })}
                </Badge>
              </div>
            </CardHeader>
            <CardContent className="space-y-2">
              <p className="text-sm text-muted-foreground">{item.description}</p>
              <Progress value={Math.min(100, (item.progress / item.target) * 100)} />
              <div className="flex items-center justify-between text-xs text-muted-foreground">
                <span>
                  {t("achievements.progress", { progress: item.progress, target: item.target })}
                </span>
                <span>{t("achievements.reward", { count: item.rewardCoins })}</span>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
};

export default Achievements;

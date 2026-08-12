import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Coins, Trophy, Clock, LogIn, Award, PlayCircle } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/hooks/useAuth";
import WalletPanel from "@/components/wallet/WalletPanel";
import { achievementApi, replayApi } from "@/services/v2Social";

const Profile = () => {
  const { t } = useTranslation();
  const { user, displayName, avatar, logout, redirectToSsoLogin } = useAuth();
  const [searchParams] = useSearchParams();
  const tab = searchParams.get("tab") || "wallet";
  const userKey = user?.id || `guest:${displayName}`;
  const unlockedAchievements = achievementApi.listMyAchievements(userKey).filter((item) => item.unlocked).length;
  const replayCount = replayApi.list(userKey).length;

  if (!user) {
    return (
      <Card className="max-w-3xl mx-auto">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <LogIn className="h-5 w-5" /> {t("profile.needLogin")}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm text-muted-foreground">
          <p>{t("profile.loginDesc")}</p>
          <Button onClick={() => void redirectToSsoLogin()}>{t("common.goLogin")}</Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6 md:space-y-8">
      <Card className="overflow-hidden border-none shadow-md">
        <div className="bg-gradient-to-r from-blue-600 to-indigo-600 p-6 md:p-8 text-white">
          <div className="flex flex-col md:flex-row items-center gap-4 md:gap-6">
            <Avatar className="h-20 w-20 md:h-24 md:w-24 border-4 border-white/20 shadow-lg">
              <AvatarImage src={avatar} />
              <AvatarFallback>{displayName.slice(0, 2)}</AvatarFallback>
            </Avatar>
            <div className="flex-1 text-center md:text-left space-y-1 md:space-y-2">
              <h1 className="text-2xl md:text-3xl font-bold">{displayName}</h1>
              <p className="text-blue-100 opacity-90 text-sm md:text-base">UID: {user.id.substring(0, 8)}</p>
            </div>
            <div className="flex gap-3 w-full md:w-auto justify-center">
              <Button variant="secondary" className="bg-white/10 hover:bg-white/20 text-white border-none" onClick={logout}>
                {t("user.logout")}
              </Button>
            </div>
          </div>
        </div>

        <CardContent className="pt-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-secondary/30 p-4 rounded-xl flex items-center gap-4">
              <div className="h-10 w-10 rounded-full bg-yellow-500/10 flex items-center justify-center text-yellow-600">
                <Coins className="h-5 w-5" />
              </div>
              <div>
                <div className="text-2xl font-bold">{user.coins ?? 0}</div>
                <div className="text-xs text-muted-foreground">{t("profile.coinBalance")}</div>
              </div>
            </div>
            
            <div className="bg-secondary/30 p-4 rounded-xl flex items-center gap-4">
              <div className="h-10 w-10 rounded-full bg-purple-500/10 flex items-center justify-center text-purple-600">
                <Trophy className="h-5 w-5" />
              </div>
              <div>
                <div className="text-2xl font-bold">Lv.{user.level ?? 1}</div>
                <div className="text-xs text-muted-foreground">{t("profile.level")}</div>
              </div>
            </div>

            <div className="bg-secondary/30 p-4 rounded-xl flex items-center gap-4">
              <div className="h-10 w-10 rounded-full bg-blue-500/10 flex items-center justify-center text-blue-600">
                <Clock className="h-5 w-5" />
              </div>
              <div>
                <div className="text-2xl font-bold">--</div>
                <div className="text-xs text-muted-foreground">{t("profile.playTime")}</div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <Tabs defaultValue={tab} className="w-full">
        <TabsList className="w-full grid grid-cols-5">
          <TabsTrigger value="wallet">{t("profile.tab.wallet")}</TabsTrigger>
          <TabsTrigger value="history">{t("profile.tab.history")}</TabsTrigger>
          <TabsTrigger value="stats">{t("profile.tab.stats")}</TabsTrigger>
          <TabsTrigger value="achievements">{t("profile.tab.achievements")}</TabsTrigger>
          <TabsTrigger value="replays">{t("profile.tab.replays")}</TabsTrigger>
        </TabsList>
        <TabsContent value="wallet" className="mt-6">
          <WalletPanel initialBalance={user.balance} />
        </TabsContent>
        <TabsContent value="history" className="mt-6">
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-lg">{t("profile.recentTitle")}</CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              {t("profile.recentEmpty")}
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="stats">
          <Card>
            <CardContent className="pt-6 text-sm text-muted-foreground">
              {t("profile.statsEmpty")}
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="achievements" className="mt-6">
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-lg">{t("profile.achievementsTitle")}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div className="flex items-center gap-2 text-muted-foreground">
                <Award className="h-4 w-4 text-amber-500" />
                {t("profile.unlockedCount", { count: unlockedAchievements })}
              </div>
              <Button asChild>
                <Link to="/achievements">{t("profile.viewAllAchievements")}</Link>
              </Button>
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="replays" className="mt-6">
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-lg">{t("profile.replaysTitle")}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div className="flex items-center gap-2 text-muted-foreground">
                <PlayCircle className="h-4 w-4 text-blue-500" />
                {t("profile.savedCount", { count: replayCount })}
              </div>
              <Button asChild>
                <Link to="/replays">{t("profile.replayCenter")}</Link>
              </Button>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
};

export default Profile;

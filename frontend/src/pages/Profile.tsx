import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Coins, Trophy, Clock, LogIn } from "lucide-react";
import { useSearchParams } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import WalletPanel from "@/components/wallet/WalletPanel";

const Profile = () => {
  const { user, displayName, avatar, logout, redirectToSsoLogin } = useAuth();
  const [searchParams] = useSearchParams();
  const tab = searchParams.get("tab") || "wallet";

  if (!user) {
    return (
      <Card className="max-w-3xl mx-auto">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <LogIn className="h-5 w-5" /> 需要登录
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm text-muted-foreground">
          <p>请先登录账号以查看个人资产、战绩和等级。游客不会记录长久战绩。</p>
          <Button onClick={() => void redirectToSsoLogin()}>前往登录</Button>
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
                退出登录
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
                <div className="text-xs text-muted-foreground">金币余额</div>
              </div>
            </div>
            
            <div className="bg-secondary/30 p-4 rounded-xl flex items-center gap-4">
              <div className="h-10 w-10 rounded-full bg-purple-500/10 flex items-center justify-center text-purple-600">
                <Trophy className="h-5 w-5" />
              </div>
              <div>
                <div className="text-2xl font-bold">Lv.{user.level ?? 1}</div>
                <div className="text-xs text-muted-foreground">当前等级</div>
              </div>
            </div>

            <div className="bg-secondary/30 p-4 rounded-xl flex items-center gap-4">
              <div className="h-10 w-10 rounded-full bg-blue-500/10 flex items-center justify-center text-blue-600">
                <Clock className="h-5 w-5" />
              </div>
              <div>
                <div className="text-2xl font-bold">--</div>
                <div className="text-xs text-muted-foreground">游戏时长（记录中）</div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <Tabs defaultValue={tab} className="w-full">
        <TabsList className="w-full grid grid-cols-3">
          <TabsTrigger value="wallet">我的钱包</TabsTrigger>
          <TabsTrigger value="history">对战记录</TabsTrigger>
          <TabsTrigger value="stats">数据统计</TabsTrigger>
        </TabsList>
        <TabsContent value="wallet" className="mt-6">
          <WalletPanel initialBalance={user.balance} />
        </TabsContent>
        <TabsContent value="history" className="mt-6">
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-lg">近期战绩</CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              暂无记录。完成一局游戏后这里会展示最新结果。
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="stats">
          <Card>
            <CardContent className="pt-6 text-sm text-muted-foreground">
              暂未汇总更详细的数据。完成对局后，系统会将胜场计入排行榜。
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
};

export default Profile;

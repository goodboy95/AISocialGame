import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { BookOpen, Sparkles } from "lucide-react";

const tutorialSteps = [
  "点击头像可查看玩家状态，轮到你时可在操作区提交发言。",
  "进入投票阶段后，先选择目标，再确认投票。",
  "夜晚阶段仅特定角色可操作，其余玩家等待天亮。",
  "结算阶段会展示胜负与关键事件，可直接发起加好友。",
];

const roleGuide = [
  { game: "狼人杀", role: "预言家", desc: "夜晚可查验一名玩家身份，白天需谨慎发言引导票型。" },
  { game: "狼人杀", role: "女巫", desc: "拥有解药与毒药，关键回合决定局势走向。" },
  { game: "谁是卧底", role: "平民", desc: "用描述传递线索，同时隐藏自己的词语细节。" },
  { game: "谁是卧底", role: "卧底", desc: "伪装成平民，利用语义模糊避开投票。" },
];

const Guide = () => {
  const navigate = useNavigate();
  const [selectedGame, setSelectedGame] = useState("werewolf");

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">新手引导与规则百科</h1>
        <p className="text-sm text-muted-foreground">交互流程、角色规则和练习入口统一收敛在这里。</p>
      </div>

      <Tabs defaultValue="tutorial" className="space-y-4">
        <TabsList className="grid w-full grid-cols-3">
          <TabsTrigger value="tutorial">交互教程</TabsTrigger>
          <TabsTrigger value="rules">规则百科</TabsTrigger>
          <TabsTrigger value="practice">练习模式</TabsTrigger>
        </TabsList>

        <TabsContent value="tutorial">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">首局引导流程</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {tutorialSteps.map((step, index) => (
                <div key={step} className="rounded-md border bg-slate-50 px-3 py-2 text-sm">
                  <span className="mr-2 font-semibold text-slate-500">#{index + 1}</span>
                  {step}
                </div>
              ))}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="rules">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <BookOpen className="h-4 w-4" />
                角色说明
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {roleGuide.map((item) => (
                <div key={`${item.game}-${item.role}`} className="rounded-md border px-3 py-2">
                  <div className="mb-1 flex items-center gap-2">
                    <Badge variant="secondary">{item.game}</Badge>
                    <span className="font-medium">{item.role}</span>
                  </div>
                  <p className="text-sm text-muted-foreground">{item.desc}</p>
                </div>
              ))}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="practice">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">练习模式</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-sm text-muted-foreground">练习模式会优先创建房间并填充 AI，适合快速熟悉发言、投票与阶段切换节奏。</p>
              <div className="flex gap-2">
                <Button variant={selectedGame === "werewolf" ? "default" : "outline"} onClick={() => setSelectedGame("werewolf")}>
                  狼人杀
                </Button>
                <Button variant={selectedGame === "undercover" ? "default" : "outline"} onClick={() => setSelectedGame("undercover")}>
                  谁是卧底
                </Button>
              </div>
              <Button onClick={() => navigate(selectedGame === "werewolf" ? "/create/werewolf" : "/create/undercover")}>
                <Sparkles className="mr-2 h-4 w-4" />
                前往练习
              </Button>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
};

export default Guide;

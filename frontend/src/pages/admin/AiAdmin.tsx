import { useEffect, useState } from "react";
import { adminApi } from "@/services/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { toast } from "sonner";

const AiAdmin = () => {
  const [models, setModels] = useState<any[]>([]);
  const [userId, setUserId] = useState("1");
  const [prompt, setPrompt] = useState("请给出一句社交推理游戏开场白");
  const [result, setResult] = useState<any>(null);

  useEffect(() => {
    adminApi.aiModels()
      .then(setModels)
      .catch((error) => toast.error(error?.response?.data?.message || "加载模型失败"));
  }, []);

  const runTest = async () => {
    try {
      const response = await adminApi.testChat({
        userId: Number(userId) || 1,
        messages: [{ role: "user", content: prompt }],
      });
      setResult(response);
    } catch (error: any) {
      toast.error(error?.response?.data?.message || "AI 调用失败");
    }
  };

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">AI 网关管理</h2>
      <Card>
        <CardHeader>
          <CardTitle>可用模型</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm">
          {models.map((m) => (
            <div key={m.id} className="rounded border p-2">
              <p>{m.displayName} ({m.provider})</p>
              <p className="text-xs text-slate-500">input {m.inputRate} / output {m.outputRate}</p>
            </div>
          ))}
          {!models.length && <p className="text-slate-500">暂无模型数据</p>}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>测试调用</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="test-user-id">用户 ID</Label>
            <Input id="test-user-id" value={userId} onChange={(e) => setUserId(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="prompt">Prompt</Label>
            <Textarea id="prompt" value={prompt} onChange={(e) => setPrompt(e.target.value)} />
          </div>
          <Button onClick={runTest}>发送测试请求</Button>
          {result && (
            <div className="rounded border bg-slate-50 p-3 text-sm">
              <p className="font-medium">模型：{result.modelKey}</p>
              <p className="mt-2 whitespace-pre-wrap">{result.content}</p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default AiAdmin;

import { useState } from "react";
import { adminApi } from "@/services/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { toast } from "sonner";

const BillingAdmin = () => {
  const [userId, setUserId] = useState("");
  const [balance, setBalance] = useState<any>(null);
  const [ledger, setLedger] = useState<any>(null);
  const [adjustTemp, setAdjustTemp] = useState("0");
  const [adjustPermanent, setAdjustPermanent] = useState("0");
  const [adjustReason, setAdjustReason] = useState("");
  const [reverseRequestId, setReverseRequestId] = useState("");
  const [reverseReason, setReverseReason] = useState("");

  const load = async () => {
    const id = Number(userId);
    if (!id) {
      toast.error("请输入用户 ID");
      return;
    }
    try {
      const [balanceRes, ledgerRes] = await Promise.all([
        adminApi.balance(id),
        adminApi.ledger(id, 1, 10),
      ]);
      setBalance(balanceRes);
      setLedger(ledgerRes);
    } catch (error: any) {
      toast.error(error?.response?.data?.message || "加载积分信息失败");
    }
  };

  const parseUserId = (): number | null => {
    const id = Number(userId);
    if (!id) {
      toast.error("请输入用户 ID");
      return null;
    }
    return id;
  };

  const adjustBalance = async () => {
    const id = parseUserId();
    if (!id) return;
    if (!adjustReason.trim()) {
      toast.error("请输入调整原因");
      return;
    }
    try {
      const nextBalance = await adminApi.adjustBalance({
        userId: id,
        deltaTemp: Number(adjustTemp) || 0,
        deltaPermanent: Number(adjustPermanent) || 0,
        reason: adjustReason.trim(),
      });
      setBalance(nextBalance);
      toast.success("调整成功");
      await load();
    } catch (error: any) {
      toast.error(error?.response?.data?.message || "调整失败");
    }
  };

  const reverseBalance = async () => {
    const id = parseUserId();
    if (!id) return;
    if (!reverseRequestId.trim() || !reverseReason.trim()) {
      toast.error("请填写原始 requestId 和冲正原因");
      return;
    }
    try {
      const nextBalance = await adminApi.reverseBalance({
        userId: id,
        originalRequestId: reverseRequestId.trim(),
        reason: reverseReason.trim(),
      });
      setBalance(nextBalance);
      toast.success("冲正成功");
      await load();
    } catch (error: any) {
      toast.error(error?.response?.data?.message || "冲正失败");
    }
  };

  const migrateBalance = async () => {
    const id = parseUserId();
    if (!id) return;
    try {
      const nextBalance = await adminApi.migrateUserBalance(id);
      setBalance(nextBalance);
      toast.success("迁移完成");
      await load();
    } catch (error: any) {
      toast.error(error?.response?.data?.message || "迁移失败");
    }
  };

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">积分管理</h2>
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-2">
          <Label htmlFor="billing-user-id">用户 ID</Label>
          <Input id="billing-user-id" value={userId} onChange={(e) => setUserId(e.target.value)} />
        </div>
        <Button onClick={load}>查询余额与流水</Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>客服补发/扣回</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label>临时积分增减（可负数）</Label>
              <Input value={adjustTemp} onChange={(e) => setAdjustTemp(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label>永久积分增减（可负数）</Label>
              <Input value={adjustPermanent} onChange={(e) => setAdjustPermanent(e.target.value)} />
            </div>
          </div>
          <div className="space-y-1">
            <Label>原因</Label>
            <Input value={adjustReason} onChange={(e) => setAdjustReason(e.target.value)} />
          </div>
          <Button onClick={adjustBalance}>提交调整</Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>客服冲正</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="space-y-1">
            <Label>原始 requestId</Label>
            <Input value={reverseRequestId} onChange={(e) => setReverseRequestId(e.target.value)} />
          </div>
          <div className="space-y-1">
            <Label>冲正原因</Label>
            <Input value={reverseReason} onChange={(e) => setReverseReason(e.target.value)} />
          </div>
          <Button onClick={reverseBalance}>提交冲正</Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>历史项目积分迁移</CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <p className="text-sm text-muted-foreground">从 payService 读取该用户项目积分快照并落库到本地账本（幂等）。</p>
          <Button variant="secondary" onClick={migrateBalance}>执行迁移</Button>
        </CardContent>
      </Card>

      {balance && (
        <Card>
          <CardHeader>
            <CardTitle>余额概览</CardTitle>
          </CardHeader>
          <CardContent className="text-sm space-y-1">
            <p>总积分：{balance.totalTokens}</p>
            <p>公共永久：{balance.publicPermanentTokens}</p>
            <p>项目临时：{balance.projectTempTokens}</p>
            <p>项目永久：{balance.projectPermanentTokens}</p>
          </CardContent>
        </Card>
      )}

      {ledger && (
        <Card>
          <CardHeader>
            <CardTitle>最近流水</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            {(ledger.entries || []).map((entry: any) => (
              <div key={entry.id} className="rounded border p-2">
                <p>类型：{entry.type} | 来源：{entry.source || "-"}</p>
                <p>变动：temp {entry.tokenDeltaTemp} / permanent {entry.tokenDeltaPermanent} / public {entry.tokenDeltaPublic}</p>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
};

export default BillingAdmin;

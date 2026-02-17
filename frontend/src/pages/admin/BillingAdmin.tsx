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

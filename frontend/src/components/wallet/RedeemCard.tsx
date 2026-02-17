import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

interface Props {
  redeeming: boolean;
  onRedeem: (code: string) => Promise<void>;
}

const RedeemCard = ({ redeeming, onRedeem }: Props) => {
  const [code, setCode] = useState("");

  const submit = async () => {
    const value = code.trim();
    if (!value) {
      return;
    }
    await onRedeem(value);
    setCode("");
  };

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-lg">兑换码</CardTitle>
      </CardHeader>
      <CardContent className="flex gap-2">
        <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="请输入兑换码" />
        <Button disabled={redeeming || !code.trim()} onClick={() => void submit()}>
          兑换
        </Button>
      </CardContent>
    </Card>
  );
};

export default RedeemCard;

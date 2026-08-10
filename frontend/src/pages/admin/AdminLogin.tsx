import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAdminAuth } from "@/hooks/useAdminAuth";
import { adminApi, getApiErrorMessage } from "@/services/api";
import { AdminAuthPolicy, AdminEnrollmentStart } from "@/types";
import { toast } from "sonner";

type Stage = "PASSWORD" | "TOTP" | "ENROLL" | "RECOVERY" | "REBIND" | "CODES";

const AdminLogin = () => {
  const navigate = useNavigate();
  const auth = useAdminAuth();
  const [policy, setPolicy] = useState<AdminAuthPolicy | null>(null);
  const [stage, setStage] = useState<Stage>("PASSWORD");
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [challengeId, setChallengeId] = useState("");
  const [enrollment, setEnrollment] = useState<AdminEnrollmentStart | null>(null);
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    adminApi.policy().then(setPolicy).catch(() => setPolicy(null));
  }, []);

  const run = async (action: () => Promise<void>) => {
    setLoading(true);
    try {
      await action();
    } catch (error) {
      toast.error(getApiErrorMessage(error, "管理员认证失败"));
    } finally {
      setLoading(false);
    }
  };

  const submitPassword = () => run(async () => {
    const result = await auth.login(username, password);
    setPassword("");
    if (result.state === "AUTHENTICATED") {
      navigate("/admin");
      return;
    }
    if (!result.challengeId) throw new Error("登录 challenge 缺失");
    setChallengeId(result.challengeId);
    setCode("");
    if (result.state === "ENROLLMENT_REQUIRED") {
      setEnrollment(await auth.startEnrollment(result.challengeId));
      setStage("ENROLL");
    } else {
      setStage("TOTP");
    }
  });

  const submitTotp = () => run(async () => {
    const result = await auth.verifyTotp(challengeId, code);
    if (result.state === "AUTHENTICATED") navigate("/admin");
  });

  const submitEnrollment = () => run(async () => {
    const result = await auth.confirmEnrollment(challengeId, code);
    setRecoveryCodes(result.recoveryCodes ?? []);
    setStage("CODES");
  });

  const submitRecovery = () => run(async () => {
    const result = await auth.verifyRecovery(challengeId, code);
    if (result.sessionScope !== "RECOVERY_REBIND_ONLY") throw new Error("恢复会话创建失败");
    const next = await auth.startRebind();
    setEnrollment(next);
    setChallengeId(next.challengeId);
    setCode("");
    setStage("REBIND");
  });

  const submitRebind = () => run(async () => {
    const result = await auth.confirmRebind(challengeId, code);
    setRecoveryCodes(result.recoveryCodes ?? []);
    setStage("CODES");
  });

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (stage === "PASSWORD") void submitPassword();
    else if (stage === "TOTP") void submitTotp();
    else if (stage === "ENROLL") void submitEnrollment();
    else if (stage === "RECOVERY") void submitRecovery();
    else if (stage === "REBIND") void submitRebind();
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>AISocialGame 管理台</CardTitle>
          <CardDescription>
            {policy ? `${policy.env} / ${policy.authMode}` : "正在读取认证策略"}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {stage === "CODES" ? (
            <div className="space-y-4">
              <p className="text-sm font-medium">恢复码仅显示一次，请保存到受保护的位置。</p>
              <pre className="max-h-56 overflow-auto rounded bg-slate-950 p-3 text-sm text-white">{recoveryCodes.join("\n")}</pre>
              <Button className="w-full" onClick={() => navigate("/admin")}>我已安全保存</Button>
            </div>
          ) : (
            <form className="space-y-4" onSubmit={submit}>
              {stage === "PASSWORD" ? <>
                <div className="space-y-2"><Label htmlFor="username">账号</Label><Input id="username" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required /></div>
                <div className="space-y-2"><Label htmlFor="password">密码</Label><Input id="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required /></div>
              </> : <>
                {(stage === "ENROLL" || stage === "REBIND") && enrollment && <div className="space-y-2 rounded border p-3 text-sm"><p>请在验证器中添加以下密钥：</p><code className="break-all">{enrollment.manualKey}</code></div>}
                <div className="space-y-2"><Label htmlFor="code">{stage === "RECOVERY" ? "恢复码" : "6 位动态验证码"}</Label><Input id="code" inputMode={stage === "RECOVERY" ? "text" : "numeric"} autoComplete="one-time-code" value={code} onChange={(e) => setCode(e.target.value)} required /></div>
              </>}
              <Button type="submit" className="w-full" disabled={loading}>{loading ? "验证中..." : stage === "PASSWORD" ? "继续" : "验证"}</Button>
              {stage === "TOTP" && <Button type="button" variant="ghost" className="w-full" onClick={() => { setCode(""); setStage("RECOVERY"); }}>使用恢复码</Button>}
              {stage !== "PASSWORD" && <Button type="button" variant="outline" className="w-full" onClick={() => { setStage("PASSWORD"); setCode(""); setChallengeId(""); setEnrollment(null); }}>重新登录</Button>}
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default AdminLogin;

import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Gamepad2 } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/hooks/useAuth";

const Register = () => {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);
  const [nickname, setNickname] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const { register } = useAuth();

  const handleRegister = (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    if (password !== confirm) {
      toast.error("两次密码不一致");
      setIsLoading(false);
      return;
    }

    register(email, password, nickname)
      .then(() => {
        toast.success("注册成功！");
        navigate("/");
      })
      .catch(err => {
        toast.error(err?.response?.data?.message || "注册失败");
      })
      .finally(() => setIsLoading(false));
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4 py-8">
      <Card className="w-full max-w-md border-slate-200 shadow-lg">
        <CardHeader className="space-y-1 text-center">
          <div className="flex justify-center mb-4">
            <div className="bg-primary text-primary-foreground p-3 rounded-xl">
              <Gamepad2 className="h-8 w-8" />
            </div>
          </div>
          <CardTitle className="text-2xl font-bold">创建账号</CardTitle>
          <CardDescription>
            加入 NexusPlay，开启你的社交推理之旅
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleRegister} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="username">用户名</Label>
              <Input id="username" placeholder="例如：逻辑大师" required value={nickname} onChange={(e) => setNickname(e.target.value)} />
              <p className="text-[10px] text-slate-500">这将是你在游戏中显示的昵称</p>
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="email">电子邮箱</Label>
              <Input id="email" type="email" placeholder="name@example.com" required value={email} onChange={(e) => setEmail(e.target.value)} />
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="password">密码</Label>
              <Input id="password" type="password" required value={password} onChange={(e) => setPassword(e.target.value)} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="confirm-password">确认密码</Label>
              <Input id="confirm-password" type="password" required value={confirm} onChange={(e) => setConfirm(e.target.value)} />
            </div>
            
            <div className="flex items-start space-x-2 pt-2">
              <Checkbox id="terms" required className="mt-0.5" />
              <Label htmlFor="terms" className="text-sm font-normal text-slate-500 leading-tight">
                我已阅读并同意 NexusPlay 的
                <Link to="/terms" className="text-blue-600 hover:underline mx-1">服务条款</Link>
                和
                <Link to="/privacy" className="text-blue-600 hover:underline mx-1">隐私政策</Link>
              </Label>
            </div>

            <Button type="submit" className="w-full bg-blue-600 hover:bg-blue-700 mt-2" disabled={isLoading}>
              {isLoading ? "注册中..." : "创建账号"}
            </Button>
          </form>
        </CardContent>
        <CardFooter className="flex justify-center border-t p-4 bg-slate-50/50 rounded-b-xl">
          <p className="text-sm text-slate-500">
            已有账号?{" "}
            <Link to="/login" className="text-blue-600 hover:underline font-medium">
              直接登录
            </Link>
          </p>
        </CardFooter>
      </Card>
    </div>
  );
};

export default Register;

import { Outlet, Link, useLocation, useNavigate } from "react-router-dom";
import { Gamepad2, User, LayoutGrid, Coins, Menu, Home, Compass, Trophy } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { useAuth } from "@/hooks/useAuth";

const MainLayout = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout, displayName } = useAuth();

  const isActive = (path: string) => {
    return location.pathname === path ? "text-primary" : "text-muted-foreground";
  };

  const isMobileActive = (path: string) => {
    return location.pathname === path ? "text-blue-600" : "text-slate-400";
  };

  return (
    <div className="min-h-screen bg-background flex flex-col pb-16 md:pb-0">
      {/* Header (Desktop & Mobile Top Bar) */}
      <header className="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div className="container flex h-14 md:h-16 items-center justify-between px-4">
          {/* Logo Area */}
          <div className="flex items-center gap-8">
            <Link to="/" className="flex items-center gap-2 font-bold text-lg md:text-xl">
              <div className="bg-primary text-primary-foreground p-1.5 rounded-lg">
                <Gamepad2 className="h-4 w-4 md:h-5 md:w-5" />
              </div>
              <span>Nexus<span className="text-primary">Play</span></span>
            </Link>

            {/* Desktop Nav */}
            <nav className="hidden md:flex items-center gap-6 text-sm font-medium">
              <Link to="/" className={`transition-colors hover:text-primary ${isActive("/")}`}>
                游戏大厅
              </Link>
              <Link to="/community" className={`transition-colors hover:text-primary ${isActive("/community")}`}>
                社区广场
              </Link>
              <Link to="/rankings" className={`transition-colors hover:text-primary ${isActive("/rankings")}`}>
                排行榜
              </Link>
            </nav>
          </div>

          {/* Right Side Actions */}
          <div className="flex items-center gap-2 md:gap-4">
            <div className="flex items-center gap-1.5 bg-secondary/50 px-2 py-1 md:px-3 md:py-1.5 rounded-full text-xs md:text-sm font-medium">
              <Coins className="h-3 w-3 md:h-4 md:w-4 text-yellow-500" />
              <span>{user?.coins ?? 0}</span>
            </div>

            {user ? (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" className="relative h-8 w-8 rounded-full">
                    <Avatar className="h-7 w-7 md:h-8 md:w-8">
                      <AvatarImage src={user.avatar} alt={displayName} />
                      <AvatarFallback>{displayName.slice(0, 2)}</AvatarFallback>
                    </Avatar>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent className="w-56" align="end" forceMount>
                  <DropdownMenuLabel className="font-normal">
                    <div className="flex flex-col space-y-1">
                      <p className="text-sm font-medium leading-none">{displayName}</p>
                      <p className="text-xs leading-none text-muted-foreground">
                        uid: {user.id.substring(0, 6)}
                      </p>
                    </div>
                  </DropdownMenuLabel>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <Link to="/profile" className="cursor-pointer w-full flex items-center">
                      <User className="mr-2 h-4 w-4" />
                      个人中心
                    </Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem>
                    <LayoutGrid className="mr-2 h-4 w-4" />
                    我的房间
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem className="text-red-600" onClick={logout}>
                    退出登录
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : (
              <Button variant="outline" onClick={() => navigate('/login')}>
                登录
              </Button>
            )}
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 container py-4 md:py-6 px-4">
        <Outlet />
      </main>

      {/* Mobile Bottom Navigation (Scheme 1A) */}
      <div className="md:hidden fixed bottom-0 left-0 right-0 bg-white border-t border-slate-200 z-50 pb-safe">
        <div className="grid grid-cols-4 h-16">
          <Link to="/" className={`flex flex-col items-center justify-center gap-1 ${isMobileActive("/")}`}>
            <Home className="h-6 w-6" />
            <span className="text-[10px] font-medium">大厅</span>
          </Link>
          <Link to="/community" className={`flex flex-col items-center justify-center gap-1 ${isMobileActive("/community")}`}>
            <Compass className="h-6 w-6" />
            <span className="text-[10px] font-medium">社区</span>
          </Link>
          <Link to="/rankings" className={`flex flex-col items-center justify-center gap-1 ${isMobileActive("/rankings")}`}>
            <Trophy className="h-6 w-6" />
            <span className="text-[10px] font-medium">排行</span>
          </Link>
          <Link to="/profile" className={`flex flex-col items-center justify-center gap-1 ${isMobileActive("/profile")}`}>
            <User className="h-6 w-6" />
            <span className="text-[10px] font-medium">我的</span>
          </Link>
        </div>
      </div>
    </div>
  );
};

export default MainLayout;

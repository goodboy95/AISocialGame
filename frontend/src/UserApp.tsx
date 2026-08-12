import type { ReactNode } from "react";
import { I18nextProvider, useTranslation } from "react-i18next";
import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { SafeErrorBoundary } from "@/components/SafeErrorBoundary";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { AuthProvider } from "./hooks/useAuth";
import { AdminAuthProvider } from "./hooks/useAdminAuth";
import MainLayout from "./components/layout/MainLayout";
import i18n from "./i18n/config";
import Index from "./pages/Index";
import CreateRoom from "./pages/CreateRoom";
import RoomList from "./pages/RoomList";
import Lobby from "./pages/Lobby";
import Profile from "./pages/Profile";
import Community from "./pages/Community";
import AiChat from "./pages/AiChat";
import Rankings from "./pages/Rankings";
import NotFound from "./pages/NotFound";
import SsoCallback from "./pages/SsoCallback";
import Achievements from "./pages/Achievements";
import Replays from "./pages/Replays";
import ReplayPlayer from "./pages/ReplayPlayer";
import Guide from "./pages/Guide";
import SpectatorRoom from "./pages/SpectatorRoom";

/**
 * 用户入口：非 /admin 路由统一由 I18nextProvider 包裹。
 * 由 main.tsx 动态 import，与 admin 入口（App.tsx）分离，
 * admin 侧不加载本模块及任何 i18n 代码。
 */

/** 用户端错误边界：错误文案随当前语言渲染（admin 侧仍使用 SafeErrorBoundary 默认简中兜底）。 */
export const UserErrorBoundary = ({ children }: { children: ReactNode }) => {
  const { t } = useTranslation();
  return (
    <SafeErrorBoundary
      texts={{
        title: t("error.boundary.title"),
        description: t("error.boundary.desc"),
        retry: t("error.boundary.retry"),
      }}
    >
      {children}
    </SafeErrorBoundary>
  );
};

const queryClient = new QueryClient();

const UserApp = () => (
  <QueryClientProvider client={queryClient}>
    <AuthProvider>
      <AdminAuthProvider>
        <TooltipProvider>
          <Toaster />
          <Sonner />
          <I18nextProvider i18n={i18n}>
            <BrowserRouter>
              <Routes>
                {/* SSO callback */}
                <Route path="/sso/callback" element={<SsoCallback />} />

                {/* Main App Routes */}
                <Route element={<MainLayout />}>
                  <Route path="/" element={<Index />} />
                  <Route path="/game/:gameId" element={<RoomList />} />
                  <Route path="/create/:gameId" element={<CreateRoom />} />
                  <Route path="/room/:gameId/:roomId" element={<Lobby />} />
                  <Route path="/profile" element={<Profile />} />
                  <Route path="/community" element={<Community />} />
                  <Route path="/ai-chat" element={<AiChat />} />
                  <Route path="/rankings" element={<Rankings />} />
                  <Route path="/achievements" element={<Achievements />} />
                  <Route path="/replays" element={<Replays />} />
                  <Route path="/replay/:archiveId" element={<ReplayPlayer />} />
                  <Route path="/guide" element={<Guide />} />
                  <Route path="/spectate/:gameId/:roomId" element={<SpectatorRoom />} />
                </Route>

                {/* 404 */}
                <Route path="*" element={<NotFound />} />
              </Routes>
            </BrowserRouter>
          </I18nextProvider>
        </TooltipProvider>
      </AdminAuthProvider>
    </AuthProvider>
  </QueryClientProvider>
);

export default UserApp;

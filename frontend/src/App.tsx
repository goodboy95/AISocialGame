import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { AuthProvider } from "./hooks/useAuth";
import { AdminAuthProvider } from "./hooks/useAdminAuth";
import MainLayout from "./components/layout/MainLayout";
import AdminLayout from "./components/layout/AdminLayout";
import Index from "./pages/Index";
import CreateRoom from "./pages/CreateRoom";
import RoomList from "./pages/RoomList";
import Lobby from "./pages/Lobby";
import Profile from "./pages/Profile";
import Community from "./pages/Community";
import Rankings from "./pages/Rankings";
import NotFound from "./pages/NotFound";
import Login from "./pages/Login";
import Register from "./pages/Register";
import AdminLogin from "./pages/admin/AdminLogin";
import Dashboard from "./pages/admin/Dashboard";
import UserAdmin from "./pages/admin/UserAdmin";
import BillingAdmin from "./pages/admin/BillingAdmin";
import AiAdmin from "./pages/admin/AiAdmin";
import IntegrationAdmin from "./pages/admin/IntegrationAdmin";

const queryClient = new QueryClient();

const App = () => (
  <QueryClientProvider client={queryClient}>
    <AuthProvider>
      <AdminAuthProvider>
        <TooltipProvider>
          <Toaster />
          <Sonner />
          <BrowserRouter>
            <Routes>
              {/* Auth Routes (No Layout) */}
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />
              <Route path="/admin/login" element={<AdminLogin />} />

              {/* Main App Routes */}
              <Route element={<MainLayout />}>
                <Route path="/" element={<Index />} />
                <Route path="/game/:gameId" element={<RoomList />} />
                <Route path="/create/:gameId" element={<CreateRoom />} />
                <Route path="/room/:gameId/:roomId" element={<Lobby />} />
                <Route path="/profile" element={<Profile />} />
                <Route path="/community" element={<Community />} />
                <Route path="/rankings" element={<Rankings />} />
              </Route>

              {/* Admin Routes */}
              <Route element={<AdminLayout />}>
                <Route path="/admin" element={<Dashboard />} />
                <Route path="/admin/users" element={<UserAdmin />} />
                <Route path="/admin/billing" element={<BillingAdmin />} />
                <Route path="/admin/ai" element={<AiAdmin />} />
                <Route path="/admin/integration" element={<IntegrationAdmin />} />
              </Route>

              {/* 404 */}
              <Route path="*" element={<NotFound />} />
            </Routes>
          </BrowserRouter>
        </TooltipProvider>
      </AdminAuthProvider>
    </AuthProvider>
  </QueryClientProvider>
);

export default App;

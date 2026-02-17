import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { authApi, setAuthToken } from "@/services/api";
import { AuthResponse, User } from "@/types";

interface AuthContextValue {
  user: User | null;
  token: string | null;
  loading: boolean;
  login: (account: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string, nickname: string) => Promise<void>;
  logout: () => void;
  displayName: string;
  avatar: string;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const LOCAL_TOKEN_KEY = "aisocialgame_token";
const LOCAL_GUEST_KEY = "aisocialgame_guest_name";

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(LOCAL_TOKEN_KEY));
  const [loading, setLoading] = useState<boolean>(!!token);

  useEffect(() => {
    if (token) {
      setAuthToken(token);
      authApi
        .me()
        .then(setUser)
        .catch(() => {
          logout();
        })
        .finally(() => setLoading(false));
    } else {
      setAuthToken(undefined);
    }
  }, [token]);

  const login = async (account: string, password: string) => {
    setLoading(true);
    const res: AuthResponse = await authApi.login(account, password);
    localStorage.setItem(LOCAL_TOKEN_KEY, res.token);
    setToken(res.token);
    setUser(res.user);
    setLoading(false);
  };

  const register = async (username: string, email: string, password: string, nickname: string) => {
    setLoading(true);
    const res: AuthResponse = await authApi.register({ username, email, password, nickname });
    localStorage.setItem(LOCAL_TOKEN_KEY, res.token);
    setToken(res.token);
    setUser(res.user);
    setLoading(false);
  };

  const logout = () => {
    localStorage.removeItem(LOCAL_TOKEN_KEY);
    setUser(null);
    setToken(null);
    setAuthToken(undefined);
  };

  const displayName = useMemo(() => {
    if (user?.nickname) return user.nickname;
    const cached = localStorage.getItem(LOCAL_GUEST_KEY);
    if (cached) return cached;
    const guest = `游客${Math.floor(Math.random() * 9000 + 1000)}`;
    localStorage.setItem(LOCAL_GUEST_KEY, guest);
    return guest;
  }, [user]);

  const avatar = useMemo(() => {
    if (user?.avatar) return user.avatar;
    const name = displayName || "guest";
    return `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(name)}`;
  }, [user, displayName]);

  const value: AuthContextValue = {
    user,
    token,
    loading,
    login,
    register,
    logout,
    displayName,
    avatar,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
};

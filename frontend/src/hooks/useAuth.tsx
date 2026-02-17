import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { authApi, setAuthToken } from "@/services/api";
import { AuthResponse, SsoCallbackData, User } from "@/types";

interface AuthContextValue {
  user: User | null;
  token: string | null;
  loading: boolean;
  redirectToSsoLogin: () => Promise<void>;
  redirectToSsoRegister: () => Promise<void>;
  ssoCallback: (payload: SsoCallbackData) => Promise<void>;
  logout: () => void;
  displayName: string;
  avatar: string;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export const LOCAL_TOKEN_KEY = "aisocialgame_token";
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

  const applyAuthResponse = (res: AuthResponse) => {
    localStorage.setItem(LOCAL_TOKEN_KEY, res.token);
    setToken(res.token);
    setUser(res.user);
  };

  const ssoCallback = async (payload: SsoCallbackData) => {
    setLoading(true);
    try {
      const res: AuthResponse = await authApi.ssoCallback(payload);
      applyAuthResponse(res);
    } finally {
      setLoading(false);
    }
  };

  const redirectToSsoLogin = async () => {
    const urls = await authApi.getSsoUrl();
    window.location.href = urls.loginUrl;
  };

  const redirectToSsoRegister = async () => {
    const urls = await authApi.getSsoUrl();
    window.location.href = urls.registerUrl;
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
    redirectToSsoLogin,
    redirectToSsoRegister,
    ssoCallback,
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

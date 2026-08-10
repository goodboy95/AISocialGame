import { createContext, useContext, useEffect, useState } from "react";
import { adminApi } from "@/services/api";
import { AdminAuthResponse, AdminEnrollmentStart, AdminLoginResult } from "@/types";

interface AdminAuthContextValue {
  admin: AdminAuthResponse | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<AdminLoginResult>;
  verifyTotp: (challengeId: string, code: string) => Promise<AdminLoginResult>;
  startEnrollment: (challengeId: string) => Promise<AdminEnrollmentStart>;
  confirmEnrollment: (challengeId: string, code: string) => Promise<AdminLoginResult>;
  verifyRecovery: (challengeId: string, code: string) => Promise<AdminLoginResult>;
  startRebind: () => Promise<AdminEnrollmentStart>;
  confirmRebind: (challengeId: string, code: string) => Promise<AdminLoginResult>;
  logout: () => Promise<void>;
}

const AdminAuthContext = createContext<AdminAuthContextValue | null>(null);

export const AdminAuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [admin, setAdmin] = useState<AdminAuthResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    adminApi.me().then(setAdmin).catch(() => setAdmin(null)).finally(() => setLoading(false));
  }, []);

  const applyAuthenticated = async (result: AdminLoginResult) => {
    if (result.state === "AUTHENTICATED") {
      setAdmin(await adminApi.me());
    }
    return result;
  };

  const login = async (username: string, password: string) => applyAuthenticated(await adminApi.login(username, password));
  const verifyTotp = async (challengeId: string, code: string) => applyAuthenticated(await adminApi.verifyTotp(challengeId, code));
  const confirmEnrollment = async (challengeId: string, code: string) => applyAuthenticated(await adminApi.confirmEnrollment(challengeId, code));
  const verifyRecovery = async (challengeId: string, code: string) => applyAuthenticated(await adminApi.verifyRecovery(challengeId, code));
  const confirmRebind = async (challengeId: string, code: string) => applyAuthenticated(await adminApi.confirmRebind(challengeId, code));

  const logout = async () => {
    try {
      await adminApi.logout();
    } finally {
      setAdmin(null);
    }
  };

  const value = {
    admin,
    loading,
    login,
    verifyTotp,
    startEnrollment: adminApi.startEnrollment,
    confirmEnrollment,
    verifyRecovery,
    startRebind: adminApi.startRebind,
    confirmRebind,
    logout,
  };

  return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>;
};

export const useAdminAuth = () => {
  const ctx = useContext(AdminAuthContext);
  if (!ctx) {
    throw new Error("useAdminAuth must be used within AdminAuthProvider");
  }
  return ctx;
};

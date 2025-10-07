import { defineStore } from "pinia";
import type { UserProfile } from "../types/user";
import {
  fetchProfile,
  loginWithPassword,
  registerAccount,
  refreshToken as refreshTokenApi
} from "../api/auth";
import http from "../api/http";
import type { AxiosError } from "axios";

interface AuthState {
  profile: UserProfile | null;
  accessToken: string | null;
  refreshToken: string | null;
}

const STORAGE_KEY = "aisocialgame.auth.v1";

type PersistedAuth = {
  accessToken: string | null;
  refreshToken: string | null;
};

function loadPersistedAuth(): PersistedAuth {
  if (typeof window === "undefined") {
    return { accessToken: null, refreshToken: null };
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return { accessToken: null, refreshToken: null };
    }
    const parsed = JSON.parse(raw) as Partial<PersistedAuth>;
    const accessToken =
      typeof parsed.accessToken === "string" && parsed.accessToken.length > 0
        ? parsed.accessToken
        : null;
    const refreshToken =
      typeof parsed.refreshToken === "string" && parsed.refreshToken.length > 0
        ? parsed.refreshToken
        : null;
    return { accessToken, refreshToken };
  } catch (error) {
    console.warn("Failed to load auth tokens from storage", error);
    return { accessToken: null, refreshToken: null };
  }
}

function persistTokens(tokens: PersistedAuth) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    if (!tokens.accessToken && !tokens.refreshToken) {
      window.localStorage.removeItem(STORAGE_KEY);
      return;
    }
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
  } catch (error) {
    console.warn("Failed to persist auth tokens", error);
  }
}

function isUnauthorizedError(error: unknown): boolean {
  const maybeError = error as AxiosError | undefined;
  return Boolean(maybeError?.response && maybeError.response.status === 401);
}

const persistedTokens = loadPersistedAuth();

if (persistedTokens.accessToken) {
  http.defaults.headers.common.Authorization = `Bearer ${persistedTokens.accessToken}`;
}

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => ({
    profile: null,
    accessToken: persistedTokens.accessToken,
    refreshToken: persistedTokens.refreshToken
  }),
  actions: {
    setTokens(accessToken: string | null, refreshToken: string | null) {
      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
      if (accessToken) {
        http.defaults.headers.common.Authorization = `Bearer ${accessToken}`;
      } else {
        delete http.defaults.headers.common.Authorization;
      }
      persistTokens({ accessToken, refreshToken });
    },
    async initialize() {
      if (!this.accessToken) {
        this.profile = null;
        return;
      }
      http.defaults.headers.common.Authorization = `Bearer ${this.accessToken}`;
      try {
        this.profile = await fetchProfile(this.accessToken);
        return;
      } catch (error) {
        if (!isUnauthorizedError(error)) {
          console.error("Failed to restore profile", error);
          return;
        }
        const refreshed = await this.refreshSession();
        if (!refreshed || !this.accessToken) {
          this.logout();
          return;
        }
        this.profile = await fetchProfile(this.accessToken);
      }
    },
    async login(username: string, password: string) {
      const { access, refresh } = await loginWithPassword({ username, password });
      this.setTokens(access, refresh);
      this.profile = await fetchProfile(access);
    },
    async register(payload: { username: string; email: string; password: string; displayName?: string }) {
      await registerAccount(payload);
      await this.login(payload.username, payload.password);
    },
    async loadProfile() {
      if (!this.accessToken) {
        return;
      }
      try {
        http.defaults.headers.common.Authorization = `Bearer ${this.accessToken}`;
        this.profile = await fetchProfile(this.accessToken);
      } catch (error) {
        if (!isUnauthorizedError(error)) {
          throw error;
        }
        const refreshed = await this.refreshSession();
        if (!refreshed || !this.accessToken) {
          throw error;
        }
        this.profile = await fetchProfile(this.accessToken);
      }
    },
    async refreshSession(): Promise<boolean> {
      if (!this.refreshToken) {
        return false;
      }
      try {
        const { access, refresh } = await refreshTokenApi(this.refreshToken);
        this.setTokens(access, refresh);
        return true;
      } catch (error) {
        this.setTokens(null, null);
        return false;
      }
    },
    logout() {
      this.profile = null;
      this.setTokens(null, null);
    }
  }
});

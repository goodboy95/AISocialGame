import { defineStore } from "pinia";
import type { UserProfile } from "../types/user";
import {
  fetchProfile,
  loginWithPassword,
  registerAccount,
  refreshToken as refreshTokenApi,
  logout as logoutApi
} from "../api/auth";
import http from "../api/http";
import type { AxiosError } from "axios";

interface AuthState {
  profile: UserProfile | null;
  accessToken: string | null;
  refreshToken: string | null;
  initialized: boolean;
  lastRefreshedAt: number | null;
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
let initializePromise: Promise<void> | null = null;
const REFRESH_COOLDOWN_MS = 1000;

if (persistedTokens.accessToken) {
  http.defaults.headers.common.Authorization = `Bearer ${persistedTokens.accessToken}`;
}

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => ({
    profile: null,
    accessToken: persistedTokens.accessToken,
    refreshToken: persistedTokens.refreshToken,
    initialized: false,
    lastRefreshedAt: null
  }),
  actions: {
    setTokens(accessToken: string | null | undefined, refreshToken: string | null | undefined) {
      const normalizedAccess = accessToken ?? null;
      const normalizedRefresh = refreshToken ?? null;
      this.accessToken = normalizedAccess;
      this.refreshToken = normalizedRefresh;
      if (normalizedAccess) {
        http.defaults.headers.common.Authorization = `Bearer ${normalizedAccess}`;
        this.lastRefreshedAt = Date.now();
      } else {
        delete http.defaults.headers.common.Authorization;
        this.lastRefreshedAt = null;
      }
      persistTokens({ accessToken: normalizedAccess, refreshToken: normalizedRefresh });
    },
    async initialize() {
      if (this.initialized) {
        return;
      }
      if (initializePromise) {
        await initializePromise;
        return;
      }
      initializePromise = (async () => {
        if (!this.refreshToken) {
          this.profile = null;
          this.setTokens(null, null);
          return;
        }
        const refreshed = await this.refreshSession({ force: true });
        if (!refreshed || !this.accessToken) {
          this.profile = null;
          this.setTokens(null, null);
          return;
        }
        try {
          this.profile = await fetchProfile(this.accessToken);
        } catch (error) {
          if (!isUnauthorizedError(error)) {
            console.error("Failed to restore profile", error);
            return;
          }
          const retried = await this.refreshSession({ force: true });
          if (!retried || !this.accessToken) {
            this.profile = null;
            this.setTokens(null, null);
            return;
          }
          this.profile = await fetchProfile(this.accessToken);
        }
      })();
      try {
        await initializePromise;
      } finally {
        this.initialized = true;
        initializePromise = null;
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
    async refreshSession(options: { force?: boolean } = {}): Promise<boolean> {
      if (!this.refreshToken) {
        return false;
      }
      try {
        const now = Date.now();
        if (
          !options.force &&
          this.lastRefreshedAt !== null &&
          now - this.lastRefreshedAt < REFRESH_COOLDOWN_MS
        ) {
          return true;
        }
        const currentRefreshToken = this.refreshToken;
        const { access, refresh } = await refreshTokenApi(currentRefreshToken);
        const nextRefresh =
          typeof refresh === "string" && refresh.length > 0 ? refresh : currentRefreshToken;
        this.setTokens(access, nextRefresh);
        return true;
      } catch (error) {
        this.profile = null;
        this.setTokens(null, null);
        return false;
      }
    },
    async logout() {
      const refresh = this.refreshToken;
      if (refresh) {
        try {
          await logoutApi(refresh);
        } catch (error) {
          console.warn("Failed to revoke refresh token on logout", error);
        }
      }
      this.profile = null;
      this.setTokens(null, null);
      this.initialized = false;
    }
  }
});

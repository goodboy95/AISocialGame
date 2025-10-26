import { defineStore } from 'pinia';
import http from '../api/http';
import { fetchProfile, refreshToken as refreshTokenApi } from '../api/auth';
import type { UserProfile } from '../types/user';

interface AuthState {
  profile: UserProfile | null;
  accessToken: string | null;
  refreshToken: string | null;
  initialized: boolean;
  initializing: boolean;
  error: string | null;
  lastRefreshedAt: number | null;
}

const STORAGE_KEY = 'aisocialgame.auth.v1';
const REFRESH_COOLDOWN_MS = 1000;

function loadPersistedTokens(): { accessToken: string | null; refreshToken: string | null } {
  if (typeof window === 'undefined') {
    return { accessToken: null, refreshToken: null };
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return { accessToken: null, refreshToken: null };
    }
    const parsed = JSON.parse(raw) as Partial<{ accessToken: string; refreshToken: string }>;
    return {
      accessToken: typeof parsed.accessToken === 'string' ? parsed.accessToken : null,
      refreshToken: typeof parsed.refreshToken === 'string' ? parsed.refreshToken : null
    };
  } catch (error) {
    console.warn('Failed to read auth tokens from storage', error);
    return { accessToken: null, refreshToken: null };
  }
}

function persistTokens(accessToken: string | null, refreshToken: string | null) {
  if (typeof window === 'undefined') {
    return;
  }
  if (!accessToken && !refreshToken) {
    window.localStorage.removeItem(STORAGE_KEY);
    return;
  }
  window.localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      accessToken,
      refreshToken
    })
  );
}

export const useAuthStore = defineStore('manage-auth', {
  state: (): AuthState => ({
    profile: null,
    accessToken: null,
    refreshToken: null,
    initialized: false,
    initializing: false,
    error: null,
    lastRefreshedAt: null
  }),
  actions: {
    setTokens(accessToken: string | null, refreshToken: string | null) {
      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
      if (accessToken) {
        http.defaults.headers.common.Authorization = Bearer ;
        this.lastRefreshedAt = Date.now();
      } else {
        delete http.defaults.headers.common.Authorization;
        this.lastRefreshedAt = null;
      }
      persistTokens(accessToken, refreshToken);
    },
    async initialize() {
      if (this.initialized || this.initializing) {
        return;
      }
      this.initializing = true;
      const tokens = loadPersistedTokens();
      this.setTokens(tokens.accessToken, tokens.refreshToken);

      if (!this.refreshToken) {
        this.error = '请先登录主站账号后再访问管理后台。';
        this.initialized = true;
        this.initializing = false;
        return;
      }
      if (!this.accessToken) {
        const refreshed = await this.refreshSession({ force: true });
        if (!refreshed || !this.accessToken) {
          this.error = '登录状态已失效，请重新登录主站。';
          this.initialized = true;
          this.initializing = false;
          return;
        }
      }

      try {
        this.profile = await fetchProfile(this.accessToken!);
        if (!this.profile.is_admin) {
          this.error = '当前账号没有管理员权限。';
          this.profile = null;
        }
      } catch (error) {
        console.error('Failed to load profile', error);
        const refreshed = await this.refreshSession({ force: true });
        if (!refreshed || !this.accessToken) {
          this.error = '登录状态已失效，请重新登录主站。';
          this.initialized = true;
          this.initializing = false;
          return;
        }
        try {
          this.profile = await fetchProfile(this.accessToken!);
          if (!this.profile.is_admin) {
            this.error = '当前账号没有管理员权限。';
            this.profile = null;
          }
        } catch (fetchError) {
          console.error('Failed to load profile after refresh', fetchError);
          this.error = '无法获取用户信息，请稍后重试。';
        }
      } finally {
        this.initialized = true;
        this.initializing = false;
      }
    },
    async refreshSession(options: { force?: boolean } = {}): Promise<boolean> {
      if (!this.refreshToken) {
        return false;
      }
      try {
        const now = Date.now();
        if (!options.force && this.lastRefreshedAt && now - this.lastRefreshedAt < REFRESH_COOLDOWN_MS) {
          return true;
        }
        const { access, refresh } = await refreshTokenApi(this.refreshToken);
        const nextRefresh = refresh && refresh.length > 0 ? refresh : this.refreshToken;
        this.setTokens(access, nextRefresh);
        return true;
      } catch (error) {
        console.error('Failed to refresh session', error);
        this.setTokens(null, null);
        this.profile = null;
        return false;
      }
    },
    resetAndRetry() {
      this.initialized = false;
      this.initializing = false;
      this.error = null;
      this.profile = null;
      this.initialize().catch((error) => {
        console.error('Failed to re-run initialization', error);
      });
    }
  }
});

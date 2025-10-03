import { defineStore } from "pinia";
import type { UserProfile } from "../types/user";
import { fetchProfile, loginWithPassword, registerAccount } from "../api/auth";

interface AuthState {
  profile: UserProfile | null;
  accessToken: string | null;
  refreshToken: string | null;
}

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => ({
    profile: null,
    accessToken: null,
    refreshToken: null
  }),
  actions: {
    async login(username: string, password: string) {
      const { access, refresh } = await loginWithPassword({ username, password });
      this.accessToken = access;
      this.refreshToken = refresh;
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
      this.profile = await fetchProfile(this.accessToken);
    },
    logout() {
      this.profile = null;
      this.accessToken = null;
      this.refreshToken = null;
    }
  }
});

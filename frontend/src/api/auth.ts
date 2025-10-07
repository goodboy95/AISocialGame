import http from "./http";
import type { UserProfile } from "../types/user";

interface TokenResponse {
  access: string;
  refresh?: string;
}

export async function loginWithPassword(payload: { username: string; password: string }): Promise<TokenResponse> {
  const { data } = await http.post<TokenResponse>("/auth/token/", payload);
  return data;
}

export async function refreshToken(refresh: string): Promise<TokenResponse> {
  const { data } = await http.post<TokenResponse>("/auth/token/refresh/", { refresh });
  return data;
}

export async function logout(refresh: string): Promise<void> {
  await http.post("/auth/logout/", { refresh });
}

export async function registerAccount(payload: {
  username: string;
  email: string;
  password: string;
  displayName?: string;
}): Promise<void> {
  await http.post("/auth/register/", {
    username: payload.username,
    email: payload.email,
    password: payload.password,
    display_name: payload.displayName
  });
}

export async function fetchProfile(accessToken: string): Promise<UserProfile> {
  const { data } = await http.get<UserProfile>("/auth/me/", {
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  });
  return data;
}

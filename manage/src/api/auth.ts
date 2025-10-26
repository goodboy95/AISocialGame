import http from './http';
import type { UserProfile } from '../types/user';

interface TokenResponse {
  access: string;
  refresh?: string;
}

export async function refreshToken(refresh: string): Promise<TokenResponse> {
  const { data } = await http.post<TokenResponse>('/auth/token/refresh/', { refresh });
  return data;
}

export async function fetchProfile(accessToken: string): Promise<UserProfile> {
  const { data } = await http.get<UserProfile>('/auth/me/', {
    headers: {
      Authorization: Bearer 
    }
  });
  return data;
}

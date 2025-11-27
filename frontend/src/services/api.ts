import axios from "axios";
import { AuthResponse, Game, Persona, Room, GameState, CommunityPost, PlayerStats } from "@/types";

const api = axios.create({
  // Default to nginx proxy prefix; callers should use relative paths (no leading /api)
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 8000,
});

export const setAuthToken = (token?: string) => {
  if (token) {
    api.defaults.headers.common["X-Auth-Token"] = token;
  } else {
    delete api.defaults.headers.common["X-Auth-Token"];
  }
};

export const authApi = {
  async login(email: string, password: string): Promise<AuthResponse> {
    const res = await api.post("/auth/login", { email, password });
    return res.data;
  },
  async register(email: string, password: string, nickname: string): Promise<AuthResponse> {
    const res = await api.post("/auth/register", { email, password, nickname });
    return res.data;
  },
  async me(): Promise<AuthResponse["user"]> {
    const res = await api.get("/auth/me");
    return res.data;
  },
};

export const gameApi = {
  async list(): Promise<Game[]> {
    const res = await api.get("/games");
    return res.data;
  },
  async detail(id: string): Promise<Game> {
    const res = await api.get(`/games/${id}`);
    return res.data;
  },
};

export const roomApi = {
  async list(gameId: string): Promise<Room[]> {
    const res = await api.get(`/games/${gameId}/rooms`);
    return res.data;
  },
  async create(gameId: string, payload: Record<string, any>): Promise<Room> {
    const res = await api.post(`/games/${gameId}/rooms`, payload);
    return res.data;
  },
  async detail(gameId: string, roomId: string): Promise<Room> {
    const res = await api.get(`/games/${gameId}/rooms/${roomId}`);
    return res.data;
  },
  async join(gameId: string, roomId: string, displayName: string, playerId?: string): Promise<Room> {
    const res = await api.post(
      `/games/${gameId}/rooms/${roomId}/join`,
      { displayName },
      { headers: playerId ? { "X-Player-Id": playerId } : undefined }
    );
    return res.data;
  },
  async addAi(gameId: string, roomId: string, personaId: string): Promise<Room> {
    const res = await api.post(`/games/${gameId}/rooms/${roomId}/ai`, { personaId });
    return res.data;
  },
};

export const personaApi = {
  async list(): Promise<Persona[]> {
    const res = await api.get("/personas");
    return res.data;
  },
};

export const gameplayApi = {
  async state(gameId: string, roomId: string, playerId?: string): Promise<GameState> {
    const res = await api.get(`/games/${gameId}/rooms/${roomId}/state`, {
      headers: playerId ? { "X-Player-Id": playerId } : undefined,
    });
    return res.data;
  },
  async start(gameId: string, roomId: string, playerId?: string): Promise<GameState> {
    const res = await api.post(
      `/games/${gameId}/rooms/${roomId}/start`,
      {},
      { headers: playerId ? { "X-Player-Id": playerId } : undefined }
    );
    return res.data;
  },
  async speak(gameId: string, roomId: string, content: string, playerId?: string): Promise<GameState> {
    const res = await api.post(
      `/games/${gameId}/rooms/${roomId}/speak`,
      { content },
      { headers: playerId ? { "X-Player-Id": playerId } : undefined }
    );
    return res.data;
  },
  async vote(gameId: string, roomId: string, targetPlayerId: string, abstain = false, playerId?: string): Promise<GameState> {
    const res = await api.post(
      `/games/${gameId}/rooms/${roomId}/vote`,
      { targetPlayerId, abstain },
      { headers: playerId ? { "X-Player-Id": playerId } : undefined }
    );
    return res.data;
  },
  async nightAction(gameId: string, roomId: string, payload: { action: string; targetPlayerId?: string; useHeal?: boolean }, playerId?: string): Promise<GameState> {
    const res = await api.post(
      `/games/${gameId}/rooms/${roomId}/night-action`,
      payload,
      { headers: playerId ? { "X-Player-Id": playerId } : undefined }
    );
    return res.data;
  },
};

export const communityApi = {
  async list(): Promise<CommunityPost[]> {
    const res = await api.get("/community/posts");
    return res.data;
  },
  async create(content: string, tags: string[], guestName?: string): Promise<CommunityPost> {
    const res = await api.post(
      "/community/posts",
      { content, tags },
      { headers: guestName ? { "X-Guest-Name": guestName } : undefined }
    );
    return res.data;
  },
  async like(id: string): Promise<CommunityPost> {
    const res = await api.post(`/community/posts/${id}/like`);
    return res.data;
  },
};

export const rankingApi = {
  async list(gameId = "total"): Promise<PlayerStats[]> {
    const res = await api.get("/rankings", { params: { gameId } });
    return res.data;
  },
};

export default api;

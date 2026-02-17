import axios from "axios";
import {
  AdminAuthResponse,
  AdminIntegrationStatus,
  AiChatResponse,
  AiMessage,
  AiModel,
  AuthResponse,
  CommunityPost,
  Game,
  GameState,
  Persona,
  PlayerStats,
  Room,
  User
} from "@/types";

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
  async login(account: string, password: string): Promise<AuthResponse> {
    const res = await api.post("/auth/login", { account, password });
    return res.data;
  },
  async register(payload: { username: string; email: string; password: string; nickname: string }): Promise<AuthResponse> {
    const res = await api.post("/auth/register", payload);
    return res.data;
  },
  async me(): Promise<User> {
    const res = await api.get("/auth/me");
    return res.data;
  },
};

export const aiApi = {
  async listModels(): Promise<AiModel[]> {
    const res = await api.get("/ai/models");
    return res.data;
  },
  async chat(messages: AiMessage[], model?: string): Promise<AiChatResponse> {
    const res = await api.post("/ai/chat", { messages, model });
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

const adminApiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api",
  timeout: 8000,
});

export const setAdminToken = (token?: string) => {
  if (token) {
    adminApiClient.defaults.headers.common["X-Admin-Token"] = token;
  } else {
    delete adminApiClient.defaults.headers.common["X-Admin-Token"];
  }
};

export const adminApi = {
  async login(username: string, password: string): Promise<AdminAuthResponse> {
    const res = await adminApiClient.post("/admin/auth/login", { username, password });
    return res.data;
  },
  async me(): Promise<AdminAuthResponse> {
    const res = await adminApiClient.get("/admin/auth/me");
    return res.data;
  },
  async dashboardSummary(): Promise<{ localUsers: number; localRooms: number; localPosts: number; localGameStates: number; aiModels: number }> {
    const res = await adminApiClient.get("/admin/dashboard/summary");
    return res.data;
  },
  async integrationServices(): Promise<AdminIntegrationStatus> {
    const res = await adminApiClient.get("/admin/integration/services");
    return res.data;
  },
  async getUser(userId: number) {
    const res = await adminApiClient.get(`/admin/users/${userId}`);
    return res.data;
  },
  async banUser(userId: number, payload: { reason: string; permanent: boolean; expiresAt?: string }) {
    const res = await adminApiClient.post(`/admin/users/${userId}/ban`, payload);
    return res.data;
  },
  async unbanUser(userId: number, reason: string) {
    const res = await adminApiClient.post(`/admin/users/${userId}/unban`, { reason });
    return res.data;
  },
  async balance(userId: number) {
    const res = await adminApiClient.get("/admin/billing/balance", { params: { userId } });
    return res.data;
  },
  async ledger(userId: number, page = 1, size = 20) {
    const res = await adminApiClient.get("/admin/billing/ledger", { params: { userId, page, size } });
    return res.data;
  },
  async aiModels(): Promise<AiModel[]> {
    const res = await adminApiClient.get("/admin/ai/models");
    return res.data;
  },
  async testChat(payload: { userId?: number; sessionId?: string; model?: string; messages: AiMessage[] }): Promise<AiChatResponse> {
    const res = await adminApiClient.post("/admin/ai/test-chat", payload);
    return res.data;
  },
};

export default api;

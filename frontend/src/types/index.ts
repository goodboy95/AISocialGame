export interface GameConfigOption {
  id: string;
  label: string;
  type: "select" | "number" | "boolean" | "text";
  defaultValue: any;
  options?: { label: string; value: any }[];
  min?: number;
  max?: number;
}

export type GameStatus = "ACTIVE" | "MAINTENANCE" | "COMING_SOON";

export interface Game {
  id: string;
  name: string;
  description: string;
  coverUrl: string;
  tags: string[];
  minPlayers: number;
  maxPlayers: number;
  status: GameStatus | "active" | "maintenance" | "coming_soon";
  onlineCount: number;
  configSchema: GameConfigOption[];
}

export interface User {
  id: string;
  externalUserId?: number;
  username?: string;
  nickname: string;
  email?: string;
  avatar: string;
  coins?: number;
  level?: number;
  balance?: {
    publicPermanentTokens: number;
    projectTempTokens: number;
    projectPermanentTokens: number;
    totalTokens: number;
    projectTempExpiresAt?: string;
  };
}

export interface Persona {
  id: string;
  name: string;
  trait: string;
  avatar: string;
}

export interface RoomSeat {
  seatNumber: number;
  playerId: string;
  displayName: string;
  ai: boolean;
  personaId?: string;
  avatar?: string;
  ready: boolean;
  host: boolean;
}

export interface Room {
  id: string;
  gameId: string;
  name: string;
  status: "WAITING" | "PLAYING" | "waiting" | "playing";
  maxPlayers: number;
  isPrivate: boolean;
  commMode?: string;
  config?: Record<string, any>;
  seats: RoomSeat[];
  selfPlayerId?: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface SsoUrlResponse {
  loginUrl: string;
  registerUrl: string;
}

export interface SsoCallbackData {
  accessToken: string;
  userId: number;
  username: string;
  sessionId: string;
}

export interface AiMessage {
  role: string;
  content: string;
}

export interface AiChatResponse {
  content: string;
  modelKey: string;
  promptTokens: number;
  completionTokens: number;
}

export interface AiEmbeddingsResponse {
  modelKey: string;
  dimensions: number;
  embeddings: number[][];
  promptTokens: number;
}

export interface AiOcrParams {
  imageUrl?: string;
  imageBase64?: string;
  documentUrl?: string;
  model?: string;
  pages?: string;
  outputType?: "TEXT" | "MARKDOWN" | "JSON";
}

export interface AiOcrResponse {
  requestId: string;
  modelKey: string;
  outputType: string;
  content: string;
  rawJson?: string;
}

export interface AiModel {
  id: number;
  displayName: string;
  provider: string;
  inputRate: number;
  outputRate: number;
  type: string;
}

export interface AdminAuthResponse {
  token: string;
  username: string;
  displayName: string;
}

export interface AdminIntegrationStatus {
  services: {
    service: string;
    reachable: boolean;
    message: string;
  }[];
}

export interface CheckinResponse {
  success: boolean;
  tokensGranted: number;
  alreadyCheckedIn: boolean;
  errorMessage?: string;
  balance: {
    publicPermanentTokens: number;
    projectTempTokens: number;
    projectPermanentTokens: number;
    totalTokens: number;
    projectTempExpiresAt?: string;
  };
}

export interface CheckinStatusResponse {
  checkedInToday: boolean;
  lastCheckinDate?: string;
  tokensGrantedToday: number;
}

export interface UsageRecord {
  requestId: string;
  modelKey: string;
  promptTokens: number;
  completionTokens: number;
  billedTokens: number;
  createdAt?: string;
}

export interface LedgerEntry {
  id: string;
  type: string;
  tokens: number;
  reason?: string;
  createdAt?: string;
}

export interface RedeemResponse {
  success: boolean;
  tokensGranted: number;
  creditType: string;
  errorMessage?: string;
  balance: {
    publicPermanentTokens: number;
    projectTempTokens: number;
    projectPermanentTokens: number;
    totalTokens: number;
    projectTempExpiresAt?: string;
  };
}

export interface RedemptionRecord {
  code: string;
  tokensGranted: number;
  creditType: string;
  redeemedAt?: string;
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export interface GameLogEntry {
  type: string;
  message: string;
  time: string;
}

export interface GamePlayerStateView {
  playerId: string;
  displayName: string;
  seatNumber: number;
  ai: boolean;
  personaId?: string;
  avatar?: string;
  alive: boolean;
  role?: string;
  word?: string;
}

export interface PendingAction {
  type: string;
  description: string;
  deadlineSeconds: number;
}

export interface GameState {
  roomId: string;
  gameId: string;
  phase: string;
  round: number;
  currentSeat?: number;
  currentSpeakerName?: string;
  winner?: string;
  myPlayerId?: string;
  mySeatNumber?: number;
  myWord?: string;
  myRole?: string;
  phaseEndsAt?: string;
  players: GamePlayerStateView[];
  logs: GameLogEntry[];
  extra?: Record<string, any>;
  votes?: Record<string, string>;
  pendingAction?: PendingAction | null;
}

export interface CommunityPost {
  id: string;
  authorName: string;
  authorId?: string;
  avatar?: string;
  content: string;
  tags: string[];
  likes: number;
  comments: number;
  createdAt: string;
}

export interface PlayerStats {
  id: string;
  playerId: string;
  gameId: string;
  displayName: string;
  avatar?: string;
  gamesPlayed: number;
  wins: number;
  score: number;
}

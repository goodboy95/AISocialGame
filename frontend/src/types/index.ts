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
  nickname: string;
  email?: string;
  avatar: string;
  coins?: number;
  level?: number;
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

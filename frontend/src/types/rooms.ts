export interface RoomOwner {
  id: number;
  username: string;
  displayName: string;
}

export interface RoomPlayer {
  id: number;
  userId: number | null;
  username: string | null;
  displayName: string;
  seatNumber: number;
  isHost: boolean;
  isAi: boolean;
  isActive: boolean;
  joinedAt: string;
  role: string;
  word: string;
  isAlive: boolean;
  hasUsedSkill: boolean;
  aiStyle?: string | null;
}

export interface UndercoverAssignmentView {
  playerId: number;
  displayName: string;
  isAi: boolean;
  isAlive: boolean;
  role: string | null;
  word: string | null;
  aiStyle?: string | null;
}

export interface UndercoverSpeech {
  player_id: number;
  content: string;
  is_ai: boolean;
  timestamp: string;
}

export interface UndercoverVoteSummary {
  submitted: number;
  required: number;
  tally: Record<string, number>;
  selfTarget?: number;
}

export interface UndercoverStateView {
  phase: string;
  round: number;
  current_player_id: number | null;
  assignments: UndercoverAssignmentView[];
  speeches: UndercoverSpeech[];
  voteSummary: UndercoverVoteSummary;
  word_pair: {
    topic?: string;
    difficulty?: string;
    selfWord?: string;
  };
  aiVoteReveals?: { playerId: number; targetId: number; timestamp: string }[];
  winner?: string;
}

export interface WerewolfAssignmentView {
  playerId: number;
  displayName: string;
  isAi: boolean;
  isAlive: boolean;
  role: string | null;
  aiStyle?: string | null;
}

export interface WerewolfPrivateInfo {
  role?: string | null;
  wolves?: {
    allies: { playerId: number; displayName: string; isAlive: boolean; isAi: boolean }[];
    selectedTarget: number | null;
  };
  seer?: {
    history: { player_id: number; role: string; timestamp: string }[];
    lastResult: { player_id: number; role: string; timestamp: string } | null;
  };
  witch?: {
    antidoteAvailable: boolean;
    poisonAvailable: boolean;
    pendingKill: number | null;
  };
}

export interface WerewolfStateView {
  phase: string;
  stage: string;
  round: number;
  current_player_id: number | null;
  assignments: WerewolfAssignmentView[];
  speeches: UndercoverSpeech[];
  voteSummary: UndercoverVoteSummary;
  last_result: {
    nightKilled?: number[];
    lynched?: number[];
    saved?: number | null;
  };
  private: WerewolfPrivateInfo;
  winner?: string;
}

export type GameStateView = UndercoverStateView | WerewolfStateView;

export interface GameSessionSnapshot<TState = Record<string, unknown>> {
  id: number;
  engine: string;
  phase: string;
  round: number;
  currentPlayerId: number | null;
  status: string;
  startedAt: string;
  updatedAt: string;
  deadlineAt: string | null;
  timer: SessionTimer | null;
  state: TState;
}

export interface SessionTimer {
  phase: string;
  duration: number;
  expiresAt: string;
  defaultAction?: Record<string, unknown>;
  description?: string;
  metadata?: Record<string, unknown>;
}

export interface RoomListItem {
  id: number;
  name: string;
  code: string;
  owner: RoomOwner;
  status: string;
  statusDisplay: string;
  phase: string;
  phaseDisplay: string;
  maxPlayers: number;
  currentRound: number;
  isPrivate: boolean;
  playerCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface RoomDetail extends RoomListItem {
  config: Record<string, unknown>;
  players: RoomPlayer[];
  isMember: boolean;
  isOwner: boolean;
  gameSession: GameSessionSnapshot<GameStateView> | null;
}

export interface ChatMessage {
  id: string;
  content: string;
  timestamp: string;
  sender: {
    id: number;
    username: string;
    displayName: string;
  } | null;
  type: "chat" | "system";
  channel?: "public" | "private" | "faction";
  event?: string;
  context?: Record<string, unknown>;
}

export interface DirectMessage {
  id: string;
  roomId: number;
  sessionId: number | null;
  channel: "private" | "faction";
  content: string;
  timestamp: string;
  sender: {
    id: number;
    displayName: string;
  };
  targetPlayerId?: number;
  faction?: string;
  recipients?: number[];
}

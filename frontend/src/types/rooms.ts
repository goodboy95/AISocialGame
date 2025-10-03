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
}

export interface UndercoverAssignmentView {
  playerId: number;
  displayName: string;
  isAi: boolean;
  isAlive: boolean;
  role: string | null;
  word: string | null;
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
  winner?: string;
}

export interface GameSessionSnapshot<TState = Record<string, unknown>> {
  id: number;
  engine: string;
  phase: string;
  round: number;
  currentPlayerId: number | null;
  status: string;
  startedAt: string;
  updatedAt: string;
  state: TState;
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
  gameSession: GameSessionSnapshot<UndercoverStateView> | null;
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
  event?: string;
}

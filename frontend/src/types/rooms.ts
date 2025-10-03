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

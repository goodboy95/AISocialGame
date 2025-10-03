import { defineStore } from "pinia";
import type { ChatMessage, RoomDetail, RoomListItem } from "../types/rooms";
import {
  createRoom as createRoomApi,
  fetchRooms as fetchRoomsApi,
  getRoomDetail,
  joinRoom as joinRoomApi,
  joinRoomByCode,
  leaveRoom as leaveRoomApi,
  startRoom as startRoomApi
} from "../api/rooms";
import { GameSocket } from "../services/websocket";
import { useAuthStore } from "./user";

interface RoomState {
  rooms: RoomListItem[];
  total: number;
  loading: boolean;
  currentRoom: RoomDetail | null;
  messages: ChatMessage[];
  socket: GameSocket | null;
  socketConnected: boolean;
}

function normalizePlayer(player: any) {
  return {
    id: player.id,
    userId: player.user_id ?? null,
    username: player.username ?? null,
    displayName: player.display_name,
    seatNumber: player.seat_number,
    isHost: player.is_host,
    isAi: player.is_ai,
    isActive: player.is_active,
    joinedAt: player.joined_at,
    role: player.role,
    word: player.word,
    isAlive: player.is_alive
  };
}

function normalizeRoom(data: any): RoomListItem {
  return {
    id: data.id,
    name: data.name,
    code: data.code,
    owner: {
      id: data.owner.id,
      username: data.owner.username,
      displayName: data.owner.display_name
    },
    status: data.status,
    statusDisplay: data.status_display,
    phase: data.phase,
    phaseDisplay: data.phase_display,
    maxPlayers: data.max_players,
    currentRound: data.current_round,
    isPrivate: data.is_private,
    playerCount: data.player_count,
    createdAt: data.created_at,
    updatedAt: data.updated_at
  };
}

function normalizeRoomDetail(data: any): RoomDetail {
  return {
    ...normalizeRoom(data),
    config: data.config ?? {},
    players: Array.isArray(data.players) ? data.players.map(normalizePlayer) : [],
    isMember: data.is_member ?? false,
    isOwner: data.is_owner ?? false
  };
}

function systemMessage(message: string, event?: string): ChatMessage {
  return {
    id: crypto.randomUUID(),
    content: message,
    timestamp: new Date().toISOString(),
    sender: null,
    type: "system",
    event
  };
}

export const useRoomsStore = defineStore("rooms", {
  state: (): RoomState => ({
    rooms: [],
    total: 0,
    loading: false,
    currentRoom: null,
    messages: [],
    socket: null,
    socketConnected: false
  }),
  actions: {
    async fetchRooms(params: { search?: string; status?: string; isPrivate?: boolean; page?: number } = {}) {
      this.loading = true;
      try {
        const response = await fetchRoomsApi(params);
        this.rooms = response.results.map(normalizeRoom);
        this.total = response.count;
      } finally {
        this.loading = false;
      }
    },
    async createRoom(payload: { name: string; maxPlayers: number; isPrivate: boolean }) {
      const detail = await createRoomApi(payload);
      this.currentRoom = normalizeRoomDetail(detail);
      this.messages = [];
      return this.currentRoom;
    },
    async loadRoomDetail(roomId: number) {
      const detail = await getRoomDetail(roomId);
      this.currentRoom = normalizeRoomDetail(detail);
      return this.currentRoom;
    },
    async joinRoom(roomId: number) {
      const detail = await joinRoomApi(roomId);
      this.currentRoom = normalizeRoomDetail(detail);
      return this.currentRoom;
    },
    async joinRoomWithCode(code: string) {
      const detail = await joinRoomByCode(code);
      this.currentRoom = normalizeRoomDetail(detail);
      return this.currentRoom;
    },
    async leaveRoom(roomId: number) {
      const detail = await leaveRoomApi(roomId);
      this.currentRoom = normalizeRoomDetail(detail);
      return this.currentRoom;
    },
    async startRoom(roomId: number) {
      const detail = await startRoomApi(roomId);
      this.currentRoom = normalizeRoomDetail(detail);
      this.messages.push(systemMessage("房主发起了游戏", "room_started"));
      return this.currentRoom;
    },
    appendChatMessage(message: ChatMessage) {
      this.messages.push(message);
    },
    connectSocket(roomId: number) {
      const auth = useAuthStore();
      if (!auth.accessToken) {
        throw new Error("需要登录后才可以建立实时连接");
      }
      this.disconnectSocket();
      const wsBase = import.meta.env.VITE_WS_BASE_URL ?? "ws://localhost:8000/ws";
      const socket = new GameSocket({ token: auth.accessToken, url: wsBase });
      const raw = socket.connect(`/rooms/${roomId}/`);
      raw.onopen = () => {
        this.socketConnected = true;
      };
      raw.onclose = () => {
        this.socketConnected = false;
      };
      raw.onerror = () => {
        this.socketConnected = false;
      };
      raw.onmessage = (event) => {
        const data = JSON.parse(event.data);
        if (data.type === "system.sync") {
          this.currentRoom = normalizeRoomDetail(data.payload);
        } else if (data.type === "system.broadcast") {
          if (data.payload?.room) {
            const previous = this.currentRoom?.isMember ?? false;
            this.currentRoom = normalizeRoomDetail(data.payload.room);
            if (previous) {
              this.currentRoom.isMember = true;
            }
          }
          if (data.payload?.message) {
            this.appendChatMessage({
              id: crypto.randomUUID(),
              content: data.payload.message,
              timestamp: data.payload.timestamp ?? new Date().toISOString(),
              sender: data.payload.actor
                ? {
                    id: data.payload.actor.id,
                    username: data.payload.actor.username ?? "",
                    displayName: data.payload.actor.display_name ?? ""
                  }
                : null,
              type: "system",
              event: data.payload.event
            });
          }
        } else if (data.type === "chat.message") {
          this.appendChatMessage({
            id: data.payload.id,
            content: data.payload.content,
            timestamp: data.payload.timestamp,
            sender: {
              id: data.payload.sender.id,
              username: data.payload.sender.username,
              displayName: data.payload.sender.display_name
            },
            type: "chat"
          });
        }
      };
      this.socket = socket;
    },
    sendChat(content: string) {
      const raw = this.socket?.getRawInstance();
      if (raw && raw.readyState === WebSocket.OPEN) {
        raw.send(JSON.stringify({ type: "chat.message", payload: { content } }));
      }
    },
    disconnectSocket() {
      this.socket?.close();
      this.socket = null;
      this.socketConnected = false;
    },
    resetMessages() {
      this.messages = [];
    }
  }
});

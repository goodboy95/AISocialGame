import { defineStore } from "pinia";
import type {
  ChatMessage,
  GameSessionSnapshot,
  GameStateView,
  RoomDetail,
  RoomListItem,
  UndercoverAssignmentView,
  UndercoverStateView,
  WerewolfAssignmentView,
  WerewolfStateView
} from "../types/rooms";
import {
  createRoom as createRoomApi,
  fetchRooms as fetchRoomsApi,
  getRoomDetail,
  joinRoom as joinRoomApi,
  joinRoomByCode,
  leaveRoom as leaveRoomApi,
  startRoom as startRoomApi,
  addAiPlayer as addAiPlayerApi,
} from "../api/rooms";
import { GameSocket } from "../services/websocket";
import { i18n } from "../i18n";
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
    isAlive: player.is_alive,
    hasUsedSkill: player.has_used_skill ?? false,
    aiStyle: player.ai_style ?? null
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
    isOwner: data.is_owner ?? false,
    gameSession: data.game_session ? normalizeGameSession(data.game_session) : null
  };
}

function normalizeGameSession(data: any): GameSessionSnapshot<GameStateView> {
  const engine = data.engine;
  const rawState = data.state ?? {};
  const state: GameStateView = engine === "werewolf"
    ? normalizeWerewolfState(rawState)
    : normalizeUndercoverState(rawState);
  return {
    id: data.id,
    engine: data.engine,
    phase: data.phase,
    round: data.round,
    currentPlayerId: data.currentPlayerId ?? null,
    status: data.status,
    startedAt: data.startedAt,
    updatedAt: data.updatedAt,
    state
  };
}

function normalizeUndercoverState(state: any): UndercoverStateView {
  const assignments: UndercoverAssignmentView[] = Array.isArray(state.assignments)
    ? state.assignments.map((item: any) => ({
        playerId: item.playerId,
        displayName: item.displayName,
        isAi: Boolean(item.isAi),
        isAlive: Boolean(item.isAlive),
        role: item.role ?? null,
        word: item.word ?? null,
        aiStyle: item.aiStyle ?? null
      }))
    : [];
  return {
    phase: state.phase ?? "preparing",
    round: state.round ?? 1,
    current_player_id: state.current_player_id ?? null,
    assignments,
    speeches: Array.isArray(state.speeches) ? state.speeches : [],
    voteSummary: state.voteSummary ?? { submitted: 0, required: assignments.length, tally: {} },
    word_pair: state.word_pair ?? {},
    winner: state.winner ?? undefined
  };
}

function normalizeWerewolfState(state: any): WerewolfStateView {
  const assignments: WerewolfAssignmentView[] = Array.isArray(state.assignments)
    ? state.assignments.map((item: any) => ({
        playerId: item.playerId,
        displayName: item.displayName,
        isAi: Boolean(item.isAi),
        isAlive: Boolean(item.isAlive),
        role: item.role ?? null,
        aiStyle: item.aiStyle ?? null
      }))
    : [];
  const voteSummaryRaw = state.voteSummary ?? { submitted: 0, required: assignments.length, tally: {} };
  const tallyEntries = voteSummaryRaw.tally ?? {};
  const lastResultRaw = state.last_result ?? {};
  const privateRaw = state.private ?? {};
  return {
    phase: state.phase ?? "night",
    stage: state.stage ?? "night.wolves",
    round: state.round ?? 1,
    current_player_id: state.current_player_id ?? null,
    assignments,
    speeches: Array.isArray(state.speeches) ? state.speeches : [],
    voteSummary: {
      submitted: voteSummaryRaw.submitted ?? 0,
      required: voteSummaryRaw.required ?? assignments.length,
      tally: Object.fromEntries(
        Object.entries(tallyEntries).map(([key, value]) => [Number(key), Number(value)])
      ),
      selfTarget: voteSummaryRaw.selfTarget ?? undefined
    },
    last_result: {
      nightKilled: Array.isArray(lastResultRaw.nightKilled) ? lastResultRaw.nightKilled : [],
      lynched: Array.isArray(lastResultRaw.lynched) ? lastResultRaw.lynched : [],
      saved: lastResultRaw.saved ?? null
    },
    private: {
      role: privateRaw.role ?? null,
      wolves: privateRaw.wolves
        ? {
            allies: Array.isArray(privateRaw.wolves.allies)
              ? privateRaw.wolves.allies.map((ally: any) => ({
                  playerId: ally.playerId,
                  displayName: ally.displayName,
                  isAlive: Boolean(ally.isAlive),
                  isAi: Boolean(ally.isAi)
                }))
              : [],
            selectedTarget: privateRaw.wolves.selectedTarget ?? null
          }
        : undefined,
      seer: privateRaw.seer
        ? {
            history: Array.isArray(privateRaw.seer.history) ? privateRaw.seer.history : [],
            lastResult: privateRaw.seer.lastResult ?? null
          }
        : undefined,
      witch: privateRaw.witch
        ? {
            antidoteAvailable: Boolean(privateRaw.witch.antidoteAvailable),
            poisonAvailable: Boolean(privateRaw.witch.poisonAvailable),
            pendingKill: privateRaw.witch.pendingKill ?? null
          }
        : undefined
    },
    winner: state.winner ?? undefined
  };
}

function translate(key: string, values?: Record<string, unknown>): string {
  return i18n.global.t(key, values) as string;
}

function systemMessage(message: string, event?: string, context?: Record<string, unknown>): ChatMessage {
  return {
    id: crypto.randomUUID(),
    content: message,
    timestamp: new Date().toISOString(),
    sender: null,
    type: "system",
    event,
    context
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
      this.messages.push(systemMessage(translate("room.systemJoined"), "room_started"));
      return this.currentRoom;
    },
    async addAiPlayer(roomId: number, payload: { style?: string; displayName?: string }) {
      const detail = await addAiPlayerApi(roomId, payload);
      this.currentRoom = normalizeRoomDetail(detail);
      this.messages.push(systemMessage(translate("room.messages.aiAdded"), "ai_player_added"));
      return this.currentRoom;
    },
    appendChatMessage(message: ChatMessage) {
      this.messages.push(message);
    },
    connectSocket(roomId: number) {
      const auth = useAuthStore();
      if (!auth.accessToken) {
        throw new Error(translate("room.messages.loginRequired"));
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
      raw.onmessage = async (event) => {
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
              event: data.payload.event,
              context: data.payload.context ?? undefined
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
        } else if (data.type === "game.event") {
          if (data.payload?.room) {
            const previous = this.currentRoom?.isMember ?? false;
            this.currentRoom = normalizeRoomDetail(data.payload.room);
            if (previous && this.currentRoom) {
              this.currentRoom.isMember = true;
            }
          }
          if (this.currentRoom && data.payload?.session) {
            this.currentRoom.gameSession = normalizeGameSession(data.payload.session);
          }
          if (this.currentRoom) {
            try {
              const latest = await getRoomDetail(this.currentRoom.id);
              this.currentRoom = normalizeRoomDetail(latest);
            } catch (error) {
              console.error("Failed to refresh room detail", error);
            }
          }
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
    sendGameEvent(event: string, payload: Record<string, unknown> = {}) {
      const raw = this.socket?.getRawInstance();
      if (raw && raw.readyState === WebSocket.OPEN) {
        raw.send(
          JSON.stringify({
            type: "game.event",
            payload: { event, payload }
          })
        );
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

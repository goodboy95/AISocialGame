import { defineStore } from "pinia";
import type {
  ChatMessage,
  DirectMessage,
  GameSessionSnapshot,
  GameStateView,
  RoomDetail,
  RoomListItem,
  RoomOwner,
  RoomPlayer,
  SessionTimer,
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
  kickPlayer as kickPlayerApi,
  deleteRoom as deleteRoomApi,
} from "../api/rooms";
import { RoomRealtimeClient } from "../services/realtime";
import { i18n } from "../i18n";
import { useAuthStore } from "./user";

interface RoomState {
  rooms: RoomListItem[];
  total: number;
  loading: boolean;
  currentRoom: RoomDetail | null;
  messages: ChatMessage[];
  directMessages: DirectMessage[];
  socket: RoomRealtimeClient | null;
  socketConnected: boolean;
}

function pickValue<T>(source: any, ...keys: string[]): T | undefined {
  if (!source || typeof source !== "object") {
    return undefined;
  }
  for (const key of keys) {
    if (key in source) {
      const value = source[key];
      if (value !== undefined) {
        return value as T;
      }
    }
  }
  return undefined;
}

function normalizeOwner(owner: any): RoomOwner {
  if (!owner || typeof owner !== "object") {
    return {
      id: 0,
      username: "",
      displayName: ""
    };
  }
  const username = pickValue<string>(owner, "username") ?? "";
  return {
    id: Number(owner.id ?? 0),
    username,
    displayName:
      pickValue<string>(owner, "display_name", "displayName") ?? username
  };
}

function normalizePlayer(player: any): RoomPlayer {
  return {
    id: Number(player.id ?? 0),
    userId: pickValue<number | null>(player, "user_id", "userId") ?? null,
    username: pickValue<string | null>(player, "username") ?? null,
    displayName: pickValue<string>(player, "display_name", "displayName") ?? "",
    seatNumber: Number(pickValue<number>(player, "seat_number", "seatNumber") ?? 0),
    isHost: Boolean(pickValue<boolean>(player, "is_host", "isHost")),
    isAi: Boolean(pickValue<boolean>(player, "is_ai", "isAi")),
    isActive: Boolean(pickValue<boolean>(player, "is_active", "isActive")),
    joinedAt: pickValue<string>(player, "joined_at", "joinedAt") ?? "",
    role: pickValue<string>(player, "role") ?? "",
    word: pickValue<string>(player, "word") ?? "",
    isAlive: Boolean(pickValue<boolean>(player, "is_alive", "isAlive") ?? true),
    hasUsedSkill: Boolean(pickValue<boolean>(player, "has_used_skill", "hasUsedSkill") ?? false),
    aiStyle: pickValue<string | null>(player, "ai_style", "aiStyle") ?? null
  };
}

function normalizeRoom(data: any): RoomListItem {
  const owner = normalizeOwner(pickValue<any>(data, "owner"));
  return {
    id: Number(data.id ?? 0),
    name: pickValue<string>(data, "name") ?? "",
    code: pickValue<string>(data, "code") ?? "",
    owner,
    status: pickValue<string>(data, "status") ?? "",
    statusDisplay: pickValue<string>(data, "status_display", "statusDisplay") ?? "",
    phase: pickValue<string>(data, "phase") ?? "",
    phaseDisplay: pickValue<string>(data, "phase_display", "phaseDisplay") ?? "",
    engine: pickValue<string>(data, "engine") ?? "undercover",
    maxPlayers: Number(pickValue<number>(data, "max_players", "maxPlayers") ?? 0),
    currentRound: Number(pickValue<number>(data, "current_round", "currentRound") ?? 0),
    isPrivate: Boolean(pickValue<boolean>(data, "is_private", "isPrivate") ?? false),
    playerCount: Number(pickValue<number>(data, "player_count", "playerCount") ?? 0),
    createdAt: pickValue<string>(data, "created_at", "createdAt") ?? "",
    updatedAt: pickValue<string>(data, "updated_at", "updatedAt") ?? ""
  };
}

function normalizeRoomDetail(data: any): RoomDetail {
  const playersRaw = pickValue<any[]>(data, "players");
  return {
    ...normalizeRoom(data),
    config: pickValue<Record<string, unknown>>(data, "config") ?? {},
    players: Array.isArray(playersRaw) ? playersRaw.map(normalizePlayer) : [],
    isMember: Boolean(pickValue<boolean>(data, "is_member", "isMember") ?? false),
    isOwner: Boolean(pickValue<boolean>(data, "is_owner", "isOwner") ?? false),
    gameSession: (() => {
      const rawSession = pickValue<any>(data, "game_session", "gameSession");
      return rawSession ? normalizeGameSession(rawSession) : null;
    })()
  };
}

function normalizeGameSession(data: any): GameSessionSnapshot<GameStateView> {
  const engine = String(pickValue<string>(data, "engine") ?? "undercover");
  const rawState = pickValue<Record<string, unknown>>(data, "state") ?? {};
  const state: GameStateView = engine === "werewolf"
    ? normalizeWerewolfState(rawState)
    : normalizeUndercoverState(rawState);
  return {
    id: Number(data.id ?? 0),
    engine,
    phase: String(pickValue<string>(data, "phase") ?? ""),
    round: Number(pickValue<number>(data, "round") ?? 0),
    currentPlayerId: pickValue<number | null>(data, "currentPlayerId", "current_player_id") ?? null,
    status: String(pickValue<string>(data, "status") ?? ""),
    startedAt: pickValue<string>(data, "startedAt", "started_at") ?? "",
    updatedAt: pickValue<string>(data, "updatedAt", "updated_at") ?? "",
    deadlineAt: pickValue<string | null>(data, "deadlineAt", "deadline_at") ?? null,
    timer: normalizeSessionTimer(pickValue<any>(data, "timer")),
    state
  };
}

function normalizeSessionTimer(timer: any): SessionTimer | null {
  if (!timer || typeof timer !== "object") {
    return null;
  }
  return {
    phase: String(pickValue<string>(timer, "phase") ?? ""),
    duration: Number(pickValue<number>(timer, "duration") ?? 0),
    expiresAt: pickValue<string>(timer, "expiresAt", "expires_at") ?? "",
    defaultAction: pickValue<Record<string, unknown>>(timer, "defaultAction", "default_action") ?? undefined,
    description: pickValue<string>(timer, "description") ?? undefined,
    metadata: pickValue<Record<string, unknown>>(timer, "metadata") ?? undefined
  };
}

function normalizeUndercoverState(state: any): UndercoverStateView {
  const rawAssignments = pickValue<any[]>(state, "assignments") ?? [];
  const assignments: UndercoverAssignmentView[] = Array.isArray(rawAssignments)
    ? rawAssignments.map((item: any) => ({
        playerId: Number(pickValue<number>(item, "playerId", "player_id") ?? 0),
        displayName: pickValue<string>(item, "displayName", "display_name") ?? "",
        isAi: Boolean(pickValue<boolean>(item, "isAi", "is_ai")),
        isAlive: Boolean(pickValue<boolean>(item, "isAlive", "is_alive")),
        role: pickValue<string | null>(item, "role") ?? null,
        word: pickValue<string | null>(item, "word") ?? null,
        aiStyle: pickValue<string | null>(item, "aiStyle", "ai_style") ?? null
      }))
    : [];
  const aiVoteRevealsRaw = (() => {
    const camel = pickValue<any[]>(state, "aiVoteReveals");
    if (Array.isArray(camel)) {
      return camel;
    }
    const snake = pickValue<any[]>(state, "ai_vote_reveals");
    return Array.isArray(snake) ? snake : [];
  })();
  const aiVoteReveals = aiVoteRevealsRaw
    .map((item: any) => {
      const playerId = Number(pickValue<number>(item, "playerId", "player_id") ?? NaN);
      const targetId = Number(pickValue<number>(item, "targetId", "target_id") ?? NaN);
      const timestamp = pickValue<string>(item, "timestamp") ?? "";
      if (!Number.isFinite(playerId) || !Number.isFinite(targetId)) {
        return null;
      }
      return { playerId, targetId, timestamp };
    })
    .filter((item: any): item is { playerId: number; targetId: number; timestamp: string } => Boolean(item));
  return {
    phase: String(pickValue<string>(state, "phase") ?? "preparing"),
    round: Number(pickValue<number>(state, "round") ?? 1),
    current_player_id: pickValue<number | null>(state, "current_player_id", "currentPlayerId") ?? null,
    assignments,
    speeches: (() => {
      const rawSpeeches = pickValue<any[]>(state, "speeches");
      return Array.isArray(rawSpeeches)
        ? (rawSpeeches as UndercoverStateView["speeches"])
        : [];
    })(),
    voteSummary: ((): UndercoverStateView["voteSummary"] => {
      const raw = pickValue<any>(state, "voteSummary", "vote_summary");
      if (!raw || typeof raw !== "object") {
        return { submitted: 0, required: assignments.length, tally: {} };
      }
      return {
        submitted: Number(pickValue<number>(raw, "submitted") ?? 0),
        required: Number(pickValue<number>(raw, "required") ?? assignments.length),
        tally: ((): Record<string, number> => {
          const rawTally = pickValue<Record<string, number>>(raw, "tally") ?? {};
          return Object.fromEntries(
            Object.entries(rawTally).map(([key, value]) => [key, Number(value)])
          );
        })(),
        selfTarget: pickValue<number>(raw, "selfTarget", "self_target") ?? undefined
      };
    })(),
    word_pair: pickValue<any>(state, "word_pair", "wordPair") ?? {},
    aiVoteReveals,
    winner: pickValue<string>(state, "winner") ?? undefined
  };
}

function normalizeWerewolfState(state: any): WerewolfStateView {
  const rawAssignments = pickValue<any[]>(state, "assignments") ?? [];
  const assignments: WerewolfAssignmentView[] = Array.isArray(rawAssignments)
    ? rawAssignments.map((item: any) => ({
        playerId: Number(pickValue<number>(item, "playerId", "player_id") ?? 0),
        displayName: pickValue<string>(item, "displayName", "display_name") ?? "",
        isAi: Boolean(pickValue<boolean>(item, "isAi", "is_ai")),
        isAlive: Boolean(pickValue<boolean>(item, "isAlive", "is_alive")),
        role: pickValue<string | null>(item, "role") ?? null,
        aiStyle: pickValue<string | null>(item, "aiStyle", "ai_style") ?? null
      }))
    : [];
  const voteSummaryRaw = pickValue<any>(state, "voteSummary", "vote_summary")
    ?? { submitted: 0, required: assignments.length, tally: {} };
  const tallyEntries = pickValue<Record<string, number>>(voteSummaryRaw, "tally") ?? {};
  const lastResultRaw = pickValue<any>(state, "last_result", "lastResult") ?? {};
  const privateRaw = pickValue<any>(state, "private") ?? {};
  return {
    phase: String(pickValue<string>(state, "phase") ?? "night"),
    stage: String(pickValue<string>(state, "stage") ?? "night.wolves"),
    round: Number(pickValue<number>(state, "round") ?? 1),
    current_player_id: pickValue<number | null>(state, "current_player_id", "currentPlayerId") ?? null,
    assignments,
    speeches: (() => {
      const rawSpeeches = pickValue<any[]>(state, "speeches");
      return Array.isArray(rawSpeeches)
        ? (rawSpeeches as UndercoverStateView["speeches"])
        : [];
    })(),
    voteSummary: {
      submitted: Number(pickValue<number>(voteSummaryRaw, "submitted") ?? 0),
      required: Number(pickValue<number>(voteSummaryRaw, "required") ?? assignments.length),
      tally: Object.fromEntries(
        Object.entries(tallyEntries).map(([key, value]) => [Number(key), Number(value)])
      ),
      selfTarget: pickValue<number>(voteSummaryRaw, "selfTarget", "self_target") ?? undefined
    },
    last_result: {
      nightKilled: Array.isArray(pickValue<any[]>(lastResultRaw, "nightKilled", "night_killed"))
        ? (pickValue<any[]>(lastResultRaw, "nightKilled", "night_killed") as number[])
        : [],
      lynched: Array.isArray(pickValue<any[]>(lastResultRaw, "lynched"))
        ? (pickValue<any[]>(lastResultRaw, "lynched") as number[])
        : [],
      saved: pickValue<number | null>(lastResultRaw, "saved") ?? null
    },
    private: {
      role: pickValue<string | null>(privateRaw, "role") ?? null,
      wolves: (() => {
        const wolvesRaw = pickValue<any>(privateRaw, "wolves");
        if (!wolvesRaw) {
          return undefined;
        }
        const alliesRaw = pickValue<any[]>(wolvesRaw, "allies") ?? [];
        return {
          allies: Array.isArray(alliesRaw)
            ? alliesRaw.map((ally: any) => ({
                  playerId: Number(pickValue<number>(ally, "playerId", "player_id") ?? 0),
                  displayName: pickValue<string>(ally, "displayName", "display_name") ?? "",
                  isAlive: Boolean(pickValue<boolean>(ally, "isAlive", "is_alive")),
                  isAi: Boolean(pickValue<boolean>(ally, "isAi", "is_ai"))
                }))
              : [],
          selectedTarget: pickValue<number | null>(wolvesRaw, "selectedTarget", "selected_target") ?? null
        };
      })(),
      seer: (() => {
        const seerRaw = pickValue<any>(privateRaw, "seer");
        if (!seerRaw) {
          return undefined;
        }
        const historyRaw = pickValue<any[]>(seerRaw, "history");
        return {
          history: Array.isArray(historyRaw)
            ? (historyRaw as WerewolfPrivateInfo["seer"]!["history"])
            : [],
          lastResult: pickValue<any>(seerRaw, "lastResult", "last_result") ?? null
        };
      })(),
      witch: (() => {
        const witchRaw = pickValue<any>(privateRaw, "witch");
        if (!witchRaw) {
          return undefined;
        }
        return {
          antidoteAvailable: Boolean(pickValue<boolean>(witchRaw, "antidoteAvailable", "antidote_available")),
          poisonAvailable: Boolean(pickValue<boolean>(witchRaw, "poisonAvailable", "poison_available")),
          pendingKill: pickValue<number | null>(witchRaw, "pendingKill", "pending_kill") ?? null
        };
      })()
    },
    winner: pickValue<string>(state, "winner") ?? undefined
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
    channel: "public",
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
    directMessages: [],
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
    async createRoom(payload: { name: string; maxPlayers: number; isPrivate: boolean; engine: "undercover" | "werewolf" }) {
      const detail = await createRoomApi(payload);
      this.currentRoom = normalizeRoomDetail(detail);
      this.messages = [];
      this.directMessages = [];
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
    async kickPlayer(roomId: number, playerId: number) {
      const detail = await kickPlayerApi(roomId, playerId);
      this.currentRoom = normalizeRoomDetail(detail);
      return this.currentRoom;
    },
    async dissolveRoom(roomId: number) {
      await deleteRoomApi(roomId);
      if (this.currentRoom?.id === roomId) {
        this.disconnectSocket();
        this.resetMessages();
        this.currentRoom = null;
      }
      this.rooms = this.rooms.filter((roomItem) => roomItem.id !== roomId);
      if (this.total > 0) {
        this.total -= 1;
      }
    },
    appendChatMessage(message: ChatMessage) {
      this.messages.push({ ...message, channel: message.channel ?? "public" });
    },
    appendDirectMessage(message: DirectMessage) {
      const existingPendingIndex = this.directMessages.findIndex(
        (item) =>
          item.pending &&
          item.channel === message.channel &&
          item.sender.id === message.sender.id &&
          item.targetPlayerId === message.targetPlayerId &&
          item.content === message.content
      );
      if (existingPendingIndex !== -1) {
        this.directMessages.splice(existingPendingIndex, 1, { ...message });
        return;
      }
      if (this.directMessages.some((item) => item.id === message.id)) {
        return;
      }
      this.directMessages.push(message);
    },
    async connectSocket(roomId: number) {
      const auth = useAuthStore();
      if (!auth.accessToken) {
        throw new Error(translate("room.messages.loginRequired"));
      }
      const refreshed = await auth.refreshSession();
      if (!refreshed || !auth.accessToken) {
        throw new Error(translate("room.messages.loginRequired"));
      }
      this.disconnectSocket();
      this.socketConnected = false;
      const wsBase = import.meta.env.VITE_WS_BASE_URL as string | undefined;
      const socket = new RoomRealtimeClient({ token: auth.accessToken, baseUrl: wsBase });
      let raw: WebSocket;
      try {
        raw = socket.connect(`/rooms/${roomId}`);
      } catch (error) {
        console.error("Failed to establish room websocket connection", error);
        throw error;
      }
      raw.onopen = () => {
        this.socketConnected = true;
      };
      raw.onclose = (event) => {
        this.socketConnected = false;
        if (event.code !== 1000) {
          console.warn("Room websocket closed abnormally", event);
        }
      };
      raw.onerror = (event) => {
        this.socketConnected = false;
        console.error("Room websocket error", event);
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
            type: "chat",
            channel: "public"
          });
        } else if (data.type === "chat.direct") {
          const payload = data.payload ?? {};
          this.appendDirectMessage({
            id: payload.id ?? crypto.randomUUID(),
            roomId: this.currentRoom?.id ?? 0,
            sessionId: payload.sessionId ?? null,
            channel: payload.channel ?? "private",
            content: payload.content ?? "",
            timestamp: payload.timestamp ?? new Date().toISOString(),
            sender: {
              id: payload.sender?.id ?? 0,
              displayName: payload.sender?.displayName ?? payload.sender?.display_name ?? ""
            },
            targetPlayerId: payload.targetPlayerId ?? undefined,
            faction: payload.faction ?? undefined,
            recipients: payload.recipients ?? undefined
          });
        } else if (data.type === "game.event") {
          const payload = data.payload ?? {};
          const eventType = payload.event ?? "";
          if (eventType === "undercover.speech_stream") {
            this.applyUndercoverSpeechStream(payload.payload ?? {});
            return;
          }
          if (eventType === "undercover.vote_cast") {
            this.applyUndercoverVoteReveal(payload.payload ?? {});
            return;
          }
          if (payload.room) {
            const previous = this.currentRoom?.isMember ?? false;
            this.currentRoom = normalizeRoomDetail(payload.room);
            if (previous && this.currentRoom) {
              this.currentRoom.isMember = true;
            }
          }
          if (this.currentRoom && payload.session) {
            this.currentRoom.gameSession = normalizeGameSession(payload.session);
          }
          if (this.currentRoom && (payload.room || payload.session)) {
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
      const raw = this.socket?.getRaw();
      if (raw && raw.readyState === WebSocket.OPEN) {
        raw.send(JSON.stringify({ type: "chat.message", payload: { content } }));
      }
    },
    sendPrivateMessage(targetPlayerId: number, content: string) {
      const raw = this.socket?.getRaw();
      const auth = useAuthStore();
      const now = new Date().toISOString();
      const selfPlayer = this.currentRoom?.players.find(
        (player) => player.userId && player.userId === auth.profile?.id
      );
      if (selfPlayer) {
        this.appendDirectMessage({
          id: crypto.randomUUID(),
          roomId: this.currentRoom?.id ?? 0,
          sessionId: this.currentRoom?.gameSession?.id ?? null,
          channel: "private",
          content,
          timestamp: now,
          sender: {
            id: selfPlayer.id,
            displayName: selfPlayer.displayName
          },
          targetPlayerId,
          pending: true
        });
      }
      if (raw && raw.readyState === WebSocket.OPEN) {
        raw.send(
          JSON.stringify({
            type: "chat.private",
            payload: { content, targetPlayerId }
          })
        );
      }
    },
    sendFactionMessage(content: string, faction?: string) {
      const raw = this.socket?.getRaw();
      if (raw && raw.readyState === WebSocket.OPEN) {
        raw.send(
          JSON.stringify({
            type: "chat.faction",
            payload: { content, faction }
          })
        );
      }
    },
    sendGameEvent(event: string, payload: Record<string, unknown> = {}) {
      const raw = this.socket?.getRaw();
      if (raw && raw.readyState === WebSocket.OPEN) {
        raw.send(
          JSON.stringify({
            type: "game.event",
            payload: { event, payload }
          })
        );
      }
    },
    applyUndercoverSpeechStream(payload: Record<string, unknown>) {
      const session = this.currentRoom?.gameSession;
      if (!session || session.engine !== "undercover") {
        return;
      }
      const state = session.state as UndercoverStateView;
      if (!Array.isArray(state.speeches)) {
        state.speeches = [];
      }
      const playerId = Number((payload as any).playerId ?? (payload as any).player_id);
      const timestamp = (payload as any).timestamp ?? "";
      if (!Number.isFinite(playerId) || !timestamp) {
        return;
      }
      const content = String((payload as any).content ?? "");
      const isAi = Boolean((payload as any).isAi ?? (payload as any).is_ai ?? false);
      const existingIndex = state.speeches.findIndex(
        (speech) => speech.player_id === playerId && speech.timestamp === timestamp
      );
      if (existingIndex === -1) {
        state.speeches.push({
          player_id: playerId,
          content,
          is_ai: isAi,
          timestamp,
        });
      } else {
        state.speeches[existingIndex] = {
          ...state.speeches[existingIndex],
          content,
        };
      }
    },
    applyUndercoverVoteReveal(payload: Record<string, unknown>) {
      const session = this.currentRoom?.gameSession;
      if (!session || session.engine !== "undercover") {
        return;
      }
      const state = session.state as UndercoverStateView;
      const playerId = Number((payload as any).playerId ?? (payload as any).player_id);
      const targetId = Number((payload as any).targetId ?? (payload as any).target_id);
      if (!Number.isFinite(playerId) || !Number.isFinite(targetId)) {
        return;
      }
      const timestamp = (payload as any).timestamp ?? new Date().toISOString();
      if (!Array.isArray(state.aiVoteReveals)) {
        state.aiVoteReveals = [];
      }
      const existingIndex = state.aiVoteReveals.findIndex((entry) => entry.playerId === playerId);
      const entry = { playerId, targetId, timestamp };
      if (existingIndex === -1) {
        state.aiVoteReveals.push(entry);
      } else {
        state.aiVoteReveals[existingIndex] = entry;
      }
    },
    disconnectSocket() {
      this.socket?.close();
      this.socket = null;
      this.socketConnected = false;
    },
    resetMessages() {
      this.messages = [];
      this.directMessages = [];
    }
  }
});

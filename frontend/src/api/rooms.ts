import http from "./http";
import type {
  RoomDetail,
  RoomListItem
} from "../types/rooms";

export interface CreateRoomPayload {
  name: string;
  maxPlayers: number;
  isPrivate: boolean;
  config?: Record<string, unknown>;
}

export interface RoomListQuery {
  search?: string;
  status?: string;
  isPrivate?: boolean;
  page?: number;
}

export interface PaginatedRooms {
  count: number;
  next: string | null;
  previous: string | null;
  results: RoomListItem[];
}

export interface AddAiPayload {
  style?: string;
  displayName?: string;
}

export async function fetchRooms(params: RoomListQuery = {}): Promise<PaginatedRooms> {
  const { data } = await http.get<PaginatedRooms>("/rooms/", {
    params: {
      search: params.search,
      status: params.status,
      is_private: params.isPrivate,
      page: params.page
    }
  });
  return data;
}

export async function createRoom(payload: CreateRoomPayload): Promise<RoomDetail> {
  const { data } = await http.post<RoomDetail>("/rooms/", {
    name: payload.name,
    max_players: payload.maxPlayers,
    is_private: payload.isPrivate,
    config: payload.config ?? {}
  });
  return data;
}

export async function getRoomDetail(roomId: number): Promise<RoomDetail> {
  const { data } = await http.get<RoomDetail>(`/rooms/${roomId}/`);
  return data;
}

export async function joinRoom(roomId: number): Promise<RoomDetail> {
  const { data } = await http.post<RoomDetail>(`/rooms/${roomId}/join/`);
  return data;
}

export async function joinRoomByCode(code: string): Promise<RoomDetail> {
  const { data } = await http.post<RoomDetail>("/rooms/join-by-code/", { code });
  return data;
}

export async function leaveRoom(roomId: number): Promise<RoomDetail> {
  const { data } = await http.post<RoomDetail>(`/rooms/${roomId}/leave/`);
  return data;
}

export async function startRoom(roomId: number): Promise<RoomDetail> {
  const { data } = await http.post<RoomDetail>(`/rooms/${roomId}/start/`);
  return data;
}

export async function addAiPlayer(roomId: number, payload: AddAiPayload): Promise<RoomDetail> {
  const { data } = await http.post<RoomDetail>(`/rooms/${roomId}/add-ai/`, {
    style: payload.style,
    display_name: payload.displayName,
  });
  return data;
}

const roomLegacyKey = (roomId: string) => `room_player_${roomId}`;
const roomScopedKey = (roomId: string, userKey: string) => `${roomLegacyKey(roomId)}_${userKey}`;

const quickMatchLegacyGameKey = (gameId: string) => `quick_match_player_${gameId}`;
const quickMatchScopedGameKey = (gameId: string, userKey: string) => `${quickMatchLegacyGameKey(gameId)}_${userKey}`;
const quickMatchLegacyGlobalKey = "aisocial_quick_match_player";

export const buildPlayerStorageUserKey = (userId?: string | null, displayName?: string | null) => {
  if (userId && userId.trim().length > 0) {
    return `user:${userId.trim()}`;
  }
  const guest = (displayName || "guest").trim();
  return `guest:${guest || "guest"}`;
};

export const getRoomPlayerId = (roomId: string, userKey: string) => {
  const scoped = localStorage.getItem(roomScopedKey(roomId, userKey));
  if (scoped) {
    return scoped;
  }
  const legacy = localStorage.getItem(roomLegacyKey(roomId));
  if (legacy) {
    localStorage.setItem(roomScopedKey(roomId, userKey), legacy);
    return legacy;
  }
  return null;
};

export const setRoomPlayerId = (roomId: string, userKey: string, playerId: string) => {
  localStorage.setItem(roomScopedKey(roomId, userKey), playerId);
};

export const getQuickMatchPlayerId = (gameId: string, userKey: string) => {
  const scoped = localStorage.getItem(quickMatchScopedGameKey(gameId, userKey));
  if (scoped) {
    return scoped;
  }

  const legacyGame = localStorage.getItem(quickMatchLegacyGameKey(gameId));
  if (legacyGame) {
    localStorage.setItem(quickMatchScopedGameKey(gameId, userKey), legacyGame);
    return legacyGame;
  }

  const legacyGlobal = localStorage.getItem(quickMatchLegacyGlobalKey);
  if (legacyGlobal) {
    localStorage.setItem(quickMatchScopedGameKey(gameId, userKey), legacyGlobal);
    return legacyGlobal;
  }
  return null;
};

export const setQuickMatchPlayerId = (gameId: string, userKey: string, playerId: string) => {
  localStorage.setItem(quickMatchScopedGameKey(gameId, userKey), playerId);
};

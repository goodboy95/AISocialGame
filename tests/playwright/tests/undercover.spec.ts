import { expect, test } from "@playwright/test";
import WebSocket from "ws";

const API_BASE = "http://localhost:8100/api";
const ROOMS_ENDPOINT = `${API_BASE}/rooms`;
const WS_BASE = "ws://localhost:8100/api/ws";

async function registerAndLogin(request: any) {
  const unique = Date.now();
  const username = `pw_${unique}`;
  const password = `Passw0rd!${unique}`;
  await request.post(`${API_BASE}/auth/register/`, {
    data: {
      username,
      password,
      display_name: `Playwright ${unique}`,
      email: `${username}@example.com`,
    },
  });
  const tokenResponse = await request.post(`${API_BASE}/auth/token/`, {
    data: { username, password },
  });
  expect(tokenResponse.ok()).toBeTruthy();
  const tokens = await tokenResponse.json();
  const headers = { Authorization: `Bearer ${tokens.access}` };
  const profileResp = await request.get(`${API_BASE}/auth/me/`, { headers });
  expect(profileResp.ok()).toBeTruthy();
  const profile = await profileResp.json();
  return { headers, access: tokens.access as string, userId: profile.id as number };
}

function waitForCondition(checker: () => boolean, timeoutMs = 90_000): Promise<void> {
  const start = Date.now();
  return new Promise((resolve, reject) => {
    const tick = () => {
      if (checker()) {
        resolve();
        return;
      }
      if (Date.now() - start > timeoutMs) {
        reject(new Error("timeout while waiting for realtime condition"));
        return;
      }
      setTimeout(tick, 250);
    };
    tick();
  });
}

test.describe.configure({ mode: "serial" });

test("undercover discussion → voting → result", async ({ request }) => {
  const { headers, access, userId } = await registerAndLogin(request);

  const createResp = await request.post(`${ROOMS_ENDPOINT}/`, {
    headers,
    data: { name: "Playwright Room", max_players: 6, is_private: false },
  });
  expect(createResp.ok()).toBeTruthy();
  const createdRoom = await createResp.json();
  const roomId = createdRoom.id as number;

  for (let i = 0; i < 3; i += 1) {
    const aiResp = await request.post(`${ROOMS_ENDPOINT}/${roomId}/add-ai/`, {
      headers,
      data: { style: "balanced" },
    });
    expect(aiResp.ok()).toBeTruthy();
  }

  const startResp = await request.post(`${ROOMS_ENDPOINT}/${roomId}/start/`, { headers });
  expect(startResp.ok()).toBeTruthy();
  const startedRoom = await startResp.json();
  const selfPlayer = (startedRoom.players as any[]).find((player) => player.user_id === userId);
  expect(selfPlayer).toBeTruthy();
  const selfPlayerId: number = selfPlayer.id;
  let latestSession: any = startedRoom.gameSession;

  const ws = new WebSocket(`${WS_BASE}/rooms/${roomId}?token=${access}`);
  await new Promise<void>((resolve, reject) => {
    ws.once("open", () => resolve());
    ws.once("error", (error) => reject(error));
  });

  ws.on("message", (event) => {
    try {
      const message = JSON.parse(event.toString());
      if (message.type === "system.sync" && message.payload) {
        const payload = message.payload;
        latestSession = payload.game_session ?? payload.gameSession ?? latestSession;
      } else if (message.type === "system.broadcast" && message.payload?.room) {
        const roomPayload = message.payload.room;
        latestSession = roomPayload.game_session ?? roomPayload.gameSession ?? latestSession;
      } else if (message.type === "game.event") {
        const payload = message.payload ?? {};
        if (payload.session) {
          latestSession = payload.session;
        }
      }
    } catch (error) {
      console.warn("Failed to parse websocket payload", error);
    }
  });

  await waitForCondition(() => (latestSession?.state?.current_player_id ?? null) === selfPlayerId);
  ws.send(
    JSON.stringify({
      type: "game.event",
      payload: { event: "submit_speech", payload: { content: "Playwright 自动发言" } },
    })
  );

  await waitForCondition(() => (latestSession?.state?.phase ?? "") === "voting");
  const assignments: any[] = latestSession?.state?.assignments ?? [];
  const voteTarget =
    assignments.find((player) => (player.isAi ?? player.is_ai) && (player.isAlive ?? player.is_alive) && (player.playerId ?? player.player_id) !== selfPlayerId) ??
    assignments.find((player) => (player.playerId ?? player.player_id) !== selfPlayerId);
  expect(voteTarget).toBeTruthy();
  ws.send(
    JSON.stringify({
      type: "game.event",
      payload: { event: "submit_vote", payload: { target_id: voteTarget.playerId ?? voteTarget.player_id } },
    })
  );

  await waitForCondition(() => (latestSession?.state?.phase ?? "") === "result");
  expect(latestSession?.state?.winner).toBeTruthy();
  ws.close();
});

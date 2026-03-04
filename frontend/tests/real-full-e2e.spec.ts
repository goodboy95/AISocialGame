import { APIRequestContext, Browser, BrowserContext, Page, expect, test } from "@playwright/test";
import * as crypto from "crypto";
import * as fs from "fs";

type E2EAccount = {
  username: string;
  password: string;
  email: string;
  displayName: string;
  appToken: string;
  userId: number;
};

const IS_REAL_E2E = process.env.REAL_E2E === "1";
const APP_BASE_URL = process.env.PLAYWRIGHT_BASE_URL || "https://aisocialgame.seekerhut.com";
const USER_SERVICE_BASE_URL = process.env.E2E_USER_SERVICE_BASE_URL || "https://userservice.seekerhut.com";
const USER_SSO_LOGIN_URL = process.env.E2E_USER_SSO_LOGIN_URL || `${USER_SERVICE_BASE_URL}/sso/login`;
const USER_SSO_REGISTER_URL = process.env.E2E_USER_SSO_REGISTER_URL || `${USER_SERVICE_BASE_URL}/sso/register`;
const USER_SSO_EMAIL_CODE_URL = process.env.E2E_USER_SSO_EMAIL_CODE_URL || `${USER_SERVICE_BASE_URL}/sso/email-code`;
const USER_ADMIN_EMAIL_LOGS_URL = process.env.E2E_USER_ADMIN_EMAIL_LOGS_URL || `${USER_SERVICE_BASE_URL}/api/admin/email/logs`;
const SSO_CALLBACK_URL = process.env.E2E_SSO_CALLBACK_URL || `${APP_BASE_URL}/sso/callback`;
const ADMIN_USERNAME = process.env.E2E_ADMIN_USERNAME || process.env.APP_ADMIN_USERNAME || "admin";
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || process.env.APP_ADMIN_PASSWORD || "admin123";

test.describe.configure({ mode: "serial" });
test.skip(!IS_REAL_E2E, "Set REAL_E2E=1 to run full real integration flow.");

const cachedEnvFiles = new Map<string, Record<string, string>>();

function loadEnvFile(path: string): Record<string, string> {
  if (cachedEnvFiles.has(path)) {
    return cachedEnvFiles.get(path)!;
  }
  const out: Record<string, string> = {};
  if (!fs.existsSync(path)) {
    cachedEnvFiles.set(path, out);
    return out;
  }
  const content = fs.readFileSync(path, "utf-8");
  for (const raw of content.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const idx = line.indexOf("=");
    if (idx <= 0) continue;
    const key = line.slice(0, idx).trim();
    let value = line.slice(idx + 1).trim();
    if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\""))) {
      value = value.slice(1, -1);
    }
    out[key] = value;
  }
  cachedEnvFiles.set(path, out);
  return out;
}

function resolveEnv(name: string, fallback = ""): string {
  if (process.env[name] && process.env[name]!.trim()) {
    return process.env[name]!.trim();
  }
  const userEnv = loadEnvFile("/home/pi/aienie-services/user-service/env.txt");
  if (userEnv[name]?.trim()) return userEnv[name].trim();
  return fallback;
}

function base64Url(input: string | Buffer): string {
  return Buffer.from(input).toString("base64url");
}

function signHs256Jwt(secret: string, payload: Record<string, unknown>): string {
  const header = { alg: "HS256", typ: "JWT" };
  const encodedHeader = base64Url(JSON.stringify(header));
  const encodedPayload = base64Url(JSON.stringify(payload));
  const body = `${encodedHeader}.${encodedPayload}`;
  const sig = crypto.createHmac("sha256", secret).update(body).digest("base64url");
  return `${body}.${sig}`;
}

function randomSuffix(len = 8): string {
  return crypto.randomBytes(Math.ceil(len / 2)).toString("hex").slice(0, len);
}

async function loginViaSso(
  request: APIRequestContext,
  username: string,
  password: string,
  state = `pw-${Date.now()}-${randomSuffix(6)}`,
): Promise<{ accessToken: string; userId: number; username: string; sessionId: string }> {
  const response = await request.post(USER_SSO_LOGIN_URL, {
    form: {
      redirect: SSO_CALLBACK_URL,
      state,
      username,
      password,
      keepDays: "3",
    },
    failOnStatusCode: false,
    maxRedirects: 0,
  });
  expect(response.status(), `SSO login status for ${username}`).toBe(302);
  const location = response.headers()["location"] || "";
  expect(location, `SSO callback location for ${username}`).toContain("access_token=");
  const hash = location.includes("#") ? location.split("#")[1] : "";
  const params = new URLSearchParams(hash);
  return {
    accessToken: params.get("access_token") || "",
    userId: Number(params.get("user_id") || "0"),
    username: params.get("username") || "",
    sessionId: params.get("session_id") || "",
  };
}

async function exchangeSsoToAppToken(
  request: APIRequestContext,
  payload: { accessToken: string; userId: number; username: string; sessionId: string },
): Promise<string> {
  const response = await request.post("/api/auth/sso-callback", {
    data: payload,
    failOnStatusCode: false,
  });
  expect(response.status()).toBe(200);
  const data = await response.json();
  expect(data?.token).toBeTruthy();
  return data.token as string;
}

async function fetchRegisterCode(request: APIRequestContext, email: string): Promise<string> {
  const userJwtSecret = resolveEnv("JWT_SECRET");
  const capBypassToken = resolveEnv("CAP_BYPASS_TOKEN", "local-cap-bypass-token");
  expect(userJwtSecret, "Missing user-service JWT_SECRET").toBeTruthy();

  const now = Math.floor(Date.now() / 1000);
  const adminToken = signHs256Jwt(userJwtSecret, {
    sub: "playwright-admin",
    uid: 0,
    role: "ADMIN",
    sid: `pw-admin-${randomSuffix(8)}`,
    displayName: "Playwright Admin",
    avatarUrl: "",
    iat: now,
    exp: now + 3600,
  });

  let sent = false;
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const sendResp = await request.post(USER_SSO_EMAIL_CODE_URL, {
      form: {
        email,
        purpose: "register",
        capToken: capBypassToken,
      },
      headers: {
        "User-Agent": `Playwright-E2E-${Date.now()}-${attempt}-${randomSuffix(4)}`,
      },
      failOnStatusCode: false,
    });
    if (sendResp.status() === 200) {
      const sendData = await sendResp.json();
      if (sendData?.ok) {
        sent = true;
        break;
      }
      const error = String(sendData?.error || "");
      if (error.includes("发送太频繁")) {
        await new Promise((resolve) => setTimeout(resolve, 3500));
        continue;
      }
      throw new Error(`send email code failed: ${JSON.stringify(sendData)}`);
    }
    await new Promise((resolve) => setTimeout(resolve, 1500));
  }
  expect(sent, `unable to send email code for ${email}`).toBeTruthy();

  for (let i = 0; i < 30; i += 1) {
    const logsResp = await request.get(`${USER_ADMIN_EMAIL_LOGS_URL}?email=${encodeURIComponent(email)}&page=0&size=10`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      failOnStatusCode: false,
    });
    if (logsResp.status() === 200) {
      const logsData = await logsResp.json();
      const first = logsData?.items?.[0];
      if (first?.id) {
        const codeResp = await request.get(`${USER_ADMIN_EMAIL_LOGS_URL}/${first.id}/code`, {
          headers: { Authorization: `Bearer ${adminToken}` },
          failOnStatusCode: false,
        });
        if (codeResp.status() === 200) {
          const code = (await codeResp.text()).trim();
          if (code.length >= 4) {
            return code;
          }
        }
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }

  throw new Error(`Unable to fetch register code for ${email}`);
}

async function registerAccount(request: APIRequestContext, username: string, password: string, email: string, displayName: string): Promise<void> {
  const emailCode = await fetchRegisterCode(request, email);
  const response = await request.post(USER_SSO_REGISTER_URL, {
    form: {
      username,
      email,
      emailCode,
      password,
      displayName,
      redirect: SSO_CALLBACK_URL,
      state: `pw-register-${randomSuffix(8)}`,
    },
    failOnStatusCode: false,
    maxRedirects: 0,
  });
  if (response.status() === 302) {
    return;
  }
  const html = await response.text();
  throw new Error(`register failed(${response.status()}): ${html.slice(0, 400)}`);
}

async function createAccount(request: APIRequestContext, index: number): Promise<E2EAccount> {
  const suffix = `${Date.now()}${randomSuffix(6)}`;
  const username = `pwuser${index}${suffix}`.toLowerCase();
  const password = `Pwe2e!${randomSuffix(10)}A`;
  const email = `${username}@example.com`;
  const displayName = `测试玩家${index}`;
  await registerAccount(request, username, password, email, displayName);
  const sso = await loginViaSso(request, username, password);
  const appToken = await exchangeSsoToAppToken(request, sso);
  return { username, password, email, displayName, appToken, userId: sso.userId };
}

async function openLoggedInPage(browser: Browser, account: E2EAccount): Promise<{ context: BrowserContext; page: Page }> {
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  await context.addInitScript((token: string) => {
    localStorage.setItem("aisocialgame_token", token);
    localStorage.setItem("aisocial_tutorial_room-undercover", "done");
    localStorage.setItem("aisocial_tutorial_room-werewolf", "done");
  }, account.appToken);
  const page = await context.newPage();
  await page.goto("/");
  await page.waitForLoadState("networkidle");
  return { context, page };
}

async function parseSeatCount(page: Page): Promise<{ current: number; max: number }> {
  const text = (await page.getByTestId("game-ai-seat-count").first().textContent()) || "";
  const match = text.match(/(\d+)\s*\/\s*(\d+)/);
  if (!match) {
    return { current: 0, max: 0 };
  }
  return { current: Number(match[1]), max: Number(match[2]) };
}

async function fillAiSeatsToFull(page: Page): Promise<void> {
  for (let i = 0; i < 20; i += 1) {
    const { current, max } = await parseSeatCount(page);
    if (max > 0 && current >= max) {
      return;
    }
    const addAiBtn = page.getByTestId("game-add-ai-btn");
    if (await addAiBtn.isVisible()) {
      if (await addAiBtn.isEnabled()) {
        await addAiBtn.click();
      }
    }
    await page.waitForTimeout(350);
  }
}

function extractRoomId(url: string): string {
  const match = url.match(/\/room\/[^/]+\/([^/?#]+)/);
  if (!match) {
    throw new Error(`Cannot parse room id from URL: ${url}`);
  }
  return match[1];
}

async function createRoomFromUi(page: Page, gameId: "undercover" | "werewolf", namePrefix: string): Promise<string> {
  await page.goto(`/create/${gameId}`);
  const roomNameInput = page.locator("#roomName");
  if (!(await roomNameInput.isVisible({ timeout: 30_000 }).catch(() => false))) {
    await page.goto(`/game/${gameId}`);
    await page.getByRole("button", { name: /创建房间/ }).click();
  }
  await expect(roomNameInput).toBeVisible({ timeout: 30_000 });
  const sixPlayerBtn = page.getByRole("button", { name: /6人局/ });
  if (await sixPlayerBtn.count()) {
    await sixPlayerBtn.first().click();
  }
  const roomName = `${namePrefix}-${Date.now()}`;
  await roomNameInput.fill(roomName);
  await page.getByRole("button", { name: "创建并入座" }).click();
  await expect(page).toHaveURL(new RegExp(`/room/${gameId}/`), { timeout: 30_000 });
  await expect(page.getByTestId("game-phase-text")).toBeVisible({ timeout: 30_000 });
  return extractRoomId(page.url());
}

async function ensureJoined(page: Page, account: E2EAccount): Promise<void> {
  for (let round = 0; round < 6; round += 1) {
    const deadline = Date.now() + 10_000;
    while (Date.now() < deadline) {
      const selfCardCount = await page.locator('[data-testid="game-player-card"][data-is-me="true"]').count();
      if (selfCardCount > 0) {
        return;
      }
      const usernameCount = await page.getByText(account.username).count();
      const displayNameCount = await page.getByText(account.displayName).count();
      if (usernameCount > 0 || displayNameCount > 0) {
        return;
      }
      await page.waitForTimeout(400);
    }
    await page.reload();
    await page.waitForLoadState("networkidle");
  }
  throw new Error(`join room timeout for ${account.username}`);
}

function e2eLog(message: string): void {
  console.log(`[real-e2e ${new Date().toISOString()}] ${message}`);
}

type ProgressSnapshot = {
  settled: boolean;
  phase: string;
  round: number;
  logCount: number;
  voteEnabled: boolean;
};

async function readProgress(page: Page): Promise<ProgressSnapshot> {
  const settled = (await page.getByTestId("game-settlement-panel").count().catch(() => 0)) > 0;
  const phaseText = (await page.getByTestId("game-phase-text").first().textContent().catch(() => "")) || "";
  const phase = (phaseText.match(/阶段：([A-Z_]+)/)?.[1] || "").trim();
  const round = Number(phaseText.match(/第(\d+)轮/)?.[1] || "0");
  const logCount = await page.getByTestId("game-log-item").count().catch(() => 0);
  const voteBtn = page.getByTestId("game-vote-submit-btn");
  const voteEnabled = await voteBtn
    .isVisible()
    .then(async (visible) => (visible ? voteBtn.isEnabled() : false))
    .catch(() => false);
  return { settled, phase, round, logCount, voteEnabled };
}

function progressKey(p: ProgressSnapshot): string {
  return `${p.phase}|${p.round}|${p.logCount}|${p.voteEnabled ? "1" : "0"}|${p.settled ? "1" : "0"}`;
}

async function doVoteIfPossible(page: Page): Promise<boolean> {
  const voteBtn = page.getByTestId("game-vote-submit-btn");
  for (let attempt = 0; attempt < 6; attempt += 1) {
    const strictCandidates = page.locator('[data-testid="game-player-card"][data-is-me="false"][data-alive="true"]');
    const strictCount = await strictCandidates.count();
    let attemptedClick = false;
    if (strictCount > 0) {
      const target = strictCandidates.nth(attempt % Math.min(strictCount, 6));
      await target.scrollIntoViewIfNeeded().catch(() => {});
      await target.click({ force: true, timeout: 2_000 }).catch(() => {});
      attemptedClick = true;
      await page.waitForTimeout(220);
      await target.click({ force: true, timeout: 2_000 }).catch(() => {});
    } else {
      const fallbackCandidates = page.locator("div.cursor-pointer").filter({ hasText: /座位/ });
      const fallbackCount = await fallbackCandidates.count();
      if (fallbackCount > 0) {
        const target = fallbackCandidates.nth(attempt % Math.min(fallbackCount, 6));
        await target.scrollIntoViewIfNeeded().catch(() => {});
        await target.click({ force: true, timeout: 2_000 }).catch(() => {});
        attemptedClick = true;
        await page.waitForTimeout(220);
        await target.click({ force: true, timeout: 2_000 }).catch(() => {});
      }
    }

    if (attemptedClick && (await voteBtn.isVisible().catch(() => false)) && (await voteBtn.isEnabled().catch(() => false))) {
      await voteBtn.click().catch(() => {});
      return true;
    }
    await page.waitForTimeout(260);
  }
  return false;
}

async function doWerewolfNightAction(page: Page): Promise<void> {
  const skipHeal = page.getByRole("button", { name: "放弃解药" });
  if (await skipHeal.count()) {
    if (await skipHeal.first().isVisible() && await skipHeal.first().isEnabled()) {
      await skipHeal.first().click();
      return;
    }
  }

  const submitNight = page.getByTestId("game-night-submit-btn");
  if (await submitNight.count() && await submitNight.first().isVisible()) {
    if (!(await submitNight.first().isEnabled())) {
      const combo = page.locator('button[role="combobox"]').first();
      if (await combo.count()) {
        await combo.click({ force: true });
        const option = page.getByRole("option").first();
        if (await option.count()) {
          await option.click({ force: true });
        }
      }
    }
    if (await submitNight.first().isEnabled()) {
      await submitNight.first().click();
      return;
    }
  }

  const poisonBtn = page.getByTestId("game-night-poison-btn");
  if (await poisonBtn.count() && await poisonBtn.first().isVisible()) {
    if (!(await poisonBtn.first().isEnabled())) {
      const combo = page.locator('button[role="combobox"]').first();
      if (await combo.count()) {
        await combo.click({ force: true });
        const option = page.getByRole("option").first();
        if (await option.count()) {
          await option.click({ force: true });
        }
      }
    }
    if (await poisonBtn.first().isEnabled()) {
      await poisonBtn.first().click();
    }
  }
}

async function actInGame(page: Page, gameId: "undercover" | "werewolf", isHost: boolean): Promise<void> {
  const settlement = page.getByTestId("game-settlement-panel");
  if (await settlement.count()) {
    return;
  }

  const phaseText = (await page.getByTestId("game-phase-text").first().textContent()) || "";
  const phase = (phaseText.match(/阶段：([A-Z_]+)/)?.[1] || "").trim();

  if (phase === "WAITING" && isHost) {
    await fillAiSeatsToFull(page);
    const startBtn = page.getByTestId("game-start-btn");
    if (await startBtn.isVisible() && await startBtn.isEnabled()) {
      await startBtn.click();
    }
    return;
  }

  if (gameId === "undercover") {
    if (phase === "DESCRIPTION") {
      const speakInput = page.getByTestId("game-speak-input");
      const speakBtn = page.getByTestId("game-speak-submit-btn");
      if (await speakInput.count() && await speakBtn.count() && await speakBtn.isVisible() && await speakBtn.isEnabled()) {
        await speakInput.fill(`卧底回合发言-${randomSuffix(4)}`);
        await speakBtn.click();
      }
      return;
    }
    if (phase === "VOTING") {
      await doVoteIfPossible(page);
    }
    return;
  }

  if (phase === "NIGHT") {
    await doWerewolfNightAction(page);
    return;
  }
  if (phase === "DAY_DISCUSS") {
    const speakInput = page.getByTestId("game-speak-input");
    const speakBtn = page.getByTestId("game-speak-submit-btn");
    if (await speakInput.count() && await speakBtn.count() && await speakBtn.isVisible() && await speakBtn.isEnabled()) {
      await speakInput.fill(`狼人杀发言-${randomSuffix(4)}`);
      await speakBtn.click();
    }
    return;
  }
  if (phase === "DAY_VOTE") {
    await doVoteIfPossible(page);
  }
}

async function runGameToSettlement(pages: Page[], gameId: "undercover" | "werewolf", hostIndex = 0, timeoutMs = 420_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  const lastProgressKeys = pages.map(() => "");
  const lastProgressAt = pages.map(() => Date.now());
  const lastPhase = pages.map(() => "");

  while (Date.now() < deadline) {
    let settled = 0;
    for (let i = 0; i < pages.length; i += 1) {
      const page = pages[i];
      const before = await readProgress(page);
      if (before.settled) {
        settled += 1;
        continue;
      }

      const beforeKey = progressKey(before);
      if (before.phase && before.phase !== lastPhase[i]) {
        lastPhase[i] = before.phase;
        e2eLog(`${gameId} page#${i + 1} -> phase=${before.phase} round=${before.round} logs=${before.logCount}`);
      }
      if (beforeKey !== lastProgressKeys[i]) {
        lastProgressKeys[i] = beforeKey;
        lastProgressAt[i] = Date.now();
      }

      await actInGame(page, gameId, i === hostIndex);

      const after = await readProgress(page);
      const afterKey = progressKey(after);
      if (afterKey !== lastProgressKeys[i]) {
        lastProgressKeys[i] = afterKey;
        lastProgressAt[i] = Date.now();
      }
      if (after.settled) {
        settled += 1;
        continue;
      }

      const stalledMs = Date.now() - lastProgressAt[i];
      if (stalledMs >= 25_000) {
        e2eLog(`${gameId} page#${i + 1} stalled ${stalledMs}ms at phase=${after.phase || "UNKNOWN"}, forcing recovery`);
        if (after.phase === "VOTING" || after.phase === "DAY_VOTE") {
          const voted = await doVoteIfPossible(page);
          if (voted) {
            lastProgressAt[i] = Date.now();
            await page.waitForTimeout(900);
            continue;
          }
        }
        await page.reload({ waitUntil: "networkidle" });
        const refreshed = await readProgress(page);
        lastProgressKeys[i] = progressKey(refreshed);
        lastProgressAt[i] = Date.now();
      }
    }
    if (settled === pages.length) {
      return;
    }
    await Promise.all(pages.map((p) => p.waitForTimeout(550)));
  }
  throw new Error(`Game ${gameId} did not settle within ${timeoutMs}ms`);
}

async function loginAdminAndCreateRedeemCode(page: Page, tokens = 3000): Promise<string> {
  await page.goto("/admin/login");
  await page.getByLabel("账号").fill(ADMIN_USERNAME);
  await page.getByLabel("密码").fill(ADMIN_PASSWORD);
  await page.getByRole("button", { name: /登录管理台/ }).click();
  await expect(page).toHaveURL(/\/admin/);

  await page.goto("/admin/integration");
  await expect(page.getByText("微服务联通状态")).toBeVisible({ timeout: 30_000 });

  await page.goto("/admin/ai");
  await expect(page.getByText("AI 网关管理")).toBeVisible({ timeout: 30_000 });
  const aiTestBtn = page.getByRole("button", { name: "发送测试请求" });
  if (await aiTestBtn.count()) {
    await aiTestBtn.click();
  }

  await page.goto("/admin/billing");
  await expect(page.getByText("积分管理")).toBeVisible({ timeout: 30_000 });
  await page.getByTestId("admin-redeem-tokens-input").fill(String(tokens));
  await page.getByTestId("admin-create-redeem-code-btn").click();
  const created = page.getByTestId("admin-created-redeem-code");
  await expect(created).toBeVisible({ timeout: 30_000 });
  const text = (await created.textContent()) || "";
  const codeMatch = text.match(/兑换码：([A-Za-z0-9_-]+)/);
  expect(codeMatch?.[1], `Cannot parse redeem code from: ${text}`).toBeTruthy();
  return codeMatch![1];
}

let accounts: E2EAccount[] = [];

test.beforeAll(async ({ playwright }) => {
  const req = await playwright.request.newContext({
    baseURL: APP_BASE_URL,
    ignoreHTTPSErrors: true,
  });
  try {
    accounts = [];
    for (let i = 1; i <= 3; i += 1) {
      accounts.push(await createAccount(req, i));
    }
  } finally {
    await req.dispose();
  }
});

test("全功能模块回归（登录、钱包、兑换、AI、社区、排行、成就、回放、管理台）", async ({ browser }) => {
  test.setTimeout(360_000);
  const { context, page } = await openLoggedInPage(browser, accounts[0]);
  try {
    await expect(page.getByText("热门游戏")).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("button", { name: "敬请期待" }).first()).toBeVisible({ timeout: 30_000 });

    const redeemCode = await loginAdminAndCreateRedeemCode(page, 5000);

    await page.goto("/profile?tab=wallet");
    await expect(page.getByText("余额概览")).toBeVisible({ timeout: 30_000 });
    const checkinBtn = page.getByRole("button", { name: /签到领积分|今日已签到/ }).first();
    if (await checkinBtn.count() && await checkinBtn.isEnabled()) {
      await checkinBtn.click();
    }
    await page.getByPlaceholder("请输入兑换码").fill(redeemCode);
    await page.getByRole("button", { name: /^兑换$/ }).click();
    await page.getByPlaceholder("输入兑换数量（1:1）").fill("100");
    await page.getByRole("button", { name: "立即兑换" }).click();
    await expect(page.getByText("通用积分兑换记录")).toBeVisible({ timeout: 30_000 });

    await page.goto("/community");
    await expect(page.getByPlaceholder("分享你的游戏趣事...")).toBeVisible({ timeout: 30_000 });
    const postContent = `Playwright社区测试-${Date.now()}`;
    await page.getByPlaceholder("分享你的游戏趣事...").fill(postContent);
    await page.getByRole("button", { name: /发布/ }).click();
    await page.waitForTimeout(1500);
    await expect(page.getByText("推荐")).toBeVisible({ timeout: 30_000 });

    await page.goto("/ai-chat");
    await expect(page.getByText("AI 流式对话")).toBeVisible({ timeout: 30_000 });
    await page.getByPlaceholder("输入问题，按“发送”后将通过 SSE 逐字返回").fill("请输出一句测试文案");
    await page.getByRole("button", { name: "发送" }).click();
    await expect(page.locator(".text-left .inline-block").first()).toBeVisible({ timeout: 60_000 });

    await page.goto("/rankings");
    await expect(page.getByText("全服排行榜")).toBeVisible({ timeout: 30_000 });
    await page.goto("/achievements");
    await expect(page.getByText("成就中心")).toBeVisible({ timeout: 30_000 });
    await page.goto("/replays");
    await expect(page.getByText("对局回放")).toBeVisible({ timeout: 30_000 });
    await page.goto("/guide");
    await expect(page.getByText("新手引导与规则百科")).toBeVisible({ timeout: 30_000 });
  } finally {
    await context.close();
  }
});

test("谁是卧底：单人玩家 + AI 完整流程到结算", async ({ browser }) => {
  test.setTimeout(480_000);
  const { context, page } = await openLoggedInPage(browser, accounts[0]);
  try {
    await createRoomFromUi(page, "undercover", "PW-卧底单人");
    await ensureJoined(page, accounts[0]);
    await runGameToSettlement([page], "undercover");
    await expect(page.getByTestId("game-settlement-panel")).toBeVisible({ timeout: 30_000 });
  } finally {
    await context.close();
  }
});

test("谁是卧底：三人玩家 + AI 完整流程到结算（含观战）", async ({ browser }) => {
  test.setTimeout(600_000);
  const p1 = await openLoggedInPage(browser, accounts[0]);
  const p2 = await openLoggedInPage(browser, accounts[1]);
  const p3 = await openLoggedInPage(browser, accounts[2]);
  const spectatorCtx = await browser.newContext({ ignoreHTTPSErrors: true });
  const spectator = await spectatorCtx.newPage();
  try {
    const roomId = await createRoomFromUi(p1.page, "undercover", "PW-卧底三人");
    await p2.page.goto(`/room/undercover/${roomId}`);
    await p3.page.goto(`/room/undercover/${roomId}`);
    await ensureJoined(p1.page, accounts[0]);
    await ensureJoined(p2.page, accounts[1]);
    await ensureJoined(p3.page, accounts[2]);

    const hostStart = p1.page.getByTestId("game-start-btn");
    if (await hostStart.isVisible()) {
      await fillAiSeatsToFull(p1.page);
      if (await hostStart.isEnabled()) {
        await hostStart.click();
      }
    }

    await spectator.goto(`/spectate/undercover/${roomId}`);
    await expect(spectator.getByText("观战模式")).toBeVisible({ timeout: 30_000 });

    await runGameToSettlement([p1.page, p2.page, p3.page], "undercover");
    await expect(p1.page.getByTestId("game-settlement-panel")).toBeVisible({ timeout: 30_000 });
    await expect(p2.page.getByTestId("game-settlement-panel")).toBeVisible({ timeout: 30_000 });
    await expect(p3.page.getByTestId("game-settlement-panel")).toBeVisible({ timeout: 30_000 });
  } finally {
    await spectatorCtx.close();
    await p1.context.close();
    await p2.context.close();
    await p3.context.close();
  }
});

test("狼人杀：单人玩家 + AI 完整流程到结算", async ({ browser }) => {
  test.setTimeout(600_000);
  const { context, page } = await openLoggedInPage(browser, accounts[0]);
  try {
    await createRoomFromUi(page, "werewolf", "PW-狼人单人");
    await ensureJoined(page, accounts[0]);
    await runGameToSettlement([page], "werewolf");
    await expect(page.getByTestId("game-settlement-panel")).toBeVisible({ timeout: 30_000 });
  } finally {
    await context.close();
  }
});

test("狼人杀：三人玩家 + AI 完整流程到结算", async ({ browser }) => {
  test.setTimeout(780_000);
  const p1 = await openLoggedInPage(browser, accounts[0]);
  const p2 = await openLoggedInPage(browser, accounts[1]);
  const p3 = await openLoggedInPage(browser, accounts[2]);
  try {
    const roomId = await createRoomFromUi(p1.page, "werewolf", "PW-狼人三人");
    await p2.page.goto(`/room/werewolf/${roomId}`);
    await p3.page.goto(`/room/werewolf/${roomId}`);
    await ensureJoined(p1.page, accounts[0]);
    await ensureJoined(p2.page, accounts[1]);
    await ensureJoined(p3.page, accounts[2]);

    await fillAiSeatsToFull(p1.page);
    const startBtn = p1.page.getByTestId("game-start-btn");
    if (await startBtn.isVisible() && await startBtn.isEnabled()) {
      await startBtn.click();
    }

    await runGameToSettlement([p1.page, p2.page, p3.page], "werewolf");
    await expect(p1.page.getByTestId("game-settlement-panel")).toBeVisible({ timeout: 30_000 });
    await expect(p2.page.getByTestId("game-settlement-panel")).toBeVisible({ timeout: 30_000 });
    await expect(p3.page.getByTestId("game-settlement-panel")).toBeVisible({ timeout: 30_000 });
  } finally {
    await p1.context.close();
    await p2.context.close();
    await p3.context.close();
  }
});

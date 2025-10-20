export interface RealtimeOptions {
  baseUrl?: string;
  token?: string;
}

function sanitizeUrl(url: string) {
  return url.replace(/\/$/, "");
}

function isLocalHostname(hostname: string): boolean {
  const normalized = hostname.toLowerCase();
  return ["localhost", "127.0.0.1", "::1"].includes(normalized) || normalized.endsWith(".local");
}

function ensureWsPath(parsed: URL): URL {
  const pathname = parsed.pathname || "/";
  if (/\/ws\/?$/i.test(pathname)) {
    parsed.pathname = pathname.endsWith("/") ? pathname.slice(0, -1) : pathname;
  } else {
    const trimmed = pathname.replace(/\/$/, "");
    parsed.pathname = `${trimmed}/ws`;
  }
  return parsed;
}

function normalizeCandidate(candidate: string): string | null {
  if (!candidate.trim()) {
    return null;
  }
  try {
    const parsed = new URL(candidate);
    if (typeof window !== "undefined") {
      const pageHost = window.location.hostname;
      if (!isLocalHostname(pageHost) && isLocalHostname(parsed.hostname)) {
        console.warn(
          `Ignoring realtime base url '${candidate}' because it points to a local host while the page is served from '${pageHost}'.`
        );
        return null;
      }
    }
    ensureWsPath(parsed);
    return sanitizeUrl(parsed.toString());
  } catch (error) {
    console.warn(`Ignoring invalid realtime base url '${candidate}'`, error);
    return null;
  }
}

function determineBaseUrl(options: RealtimeOptions = {}): string {
  const candidates = [options.baseUrl, import.meta.env.VITE_WS_BASE_URL as string | undefined].filter(
    (value): value is string => Boolean(value && value.trim())
  );

  for (const candidate of candidates) {
    const normalized = normalizeCandidate(candidate);
    if (normalized) {
      return normalized;
    }
  }

  const apiBase = import.meta.env.VITE_API_BASE_URL as string | undefined;
  if (apiBase && apiBase.trim()) {
    try {
      const parsed = new URL(apiBase);
      parsed.protocol = parsed.protocol === "https:" ? "wss:" : "ws:";
      const suffix = parsed.pathname.endsWith("/api") ? parsed.pathname.slice(0, -4) : parsed.pathname;
      parsed.pathname = `${suffix.replace(/\/$/, "")}/ws`;
      const normalized = normalizeCandidate(parsed.toString());
      if (normalized) {
        return normalized;
      }
    } catch (error) {
      console.warn("Unable to derive websocket base from VITE_API_BASE_URL", error);
    }
  }

  if (typeof window !== "undefined" && window.location) {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${window.location.host}/ws`;
  }

  return "ws://socialgame.seekerhut.com/ws";
}

function buildUrl(base: string, path: string, token?: string) {
  const normalizedBase = base.endsWith("/") ? base : `${base}/`;
  const normalizedPath = path.startsWith("/") ? path.slice(1) : path;
  const target = new URL(normalizedPath, normalizedBase);
  if (token) {
    target.searchParams.set("token", token);
  }
  return target.toString();
}

export class RoomRealtimeClient {
  private socket: WebSocket | null = null;
  private readonly baseUrl: string;
  private readonly token?: string;

  constructor(options: RealtimeOptions = {}) {
    this.baseUrl = determineBaseUrl(options);
    this.token = options.token;
  }

  connect(path: string) {
    const target = buildUrl(this.baseUrl, path, this.token);
    this.socket = new WebSocket(target);
    return this.socket;
  }

  close() {
    this.socket?.close();
    this.socket = null;
  }

  getRaw() {
    return this.socket;
  }

  isOpen() {
    return this.socket?.readyState === WebSocket.OPEN;
  }
}

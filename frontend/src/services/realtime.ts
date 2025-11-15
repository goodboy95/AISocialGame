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

function resolveBaseForRelativeUrls(): string {
  if (typeof window !== "undefined" && window.location) {
    return window.location.origin;
  }
  return "http://localhost";
}

function parseUrl(candidate: string): URL {
  if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(candidate)) {
    return new URL(candidate);
  }
  return new URL(candidate, resolveBaseForRelativeUrls());
}

function normalizeCandidate(candidate?: string | null): string | null {
  if (!candidate) {
    return null;
  }
  const trimmed = candidate.trim();
  if (!trimmed) {
    return null;
  }
  try {
    const parsed = parseUrl(trimmed);
    if (parsed.protocol === "http:" || parsed.protocol === "https:") {
      parsed.protocol = parsed.protocol === "https:" ? "wss:" : "ws:";
    }
    if (typeof window !== "undefined" && window.location) {
      const isSecurePage = window.location.protocol === "https:";
      if (isSecurePage && parsed.protocol === "ws:") {
        parsed.protocol = "wss:";
      }
      const pageHost = window.location.hostname;
      if (!isLocalHostname(pageHost) && isLocalHostname(parsed.hostname)) {
        console.warn(
          `Ignoring realtime base url '${candidate}' because it points to a local host while the page is served from '${pageHost}'.`
        );
        return null;
      }
    }
    if (parsed.protocol !== "ws:" && parsed.protocol !== "wss:") {
      parsed.protocol = parsed.protocol.endsWith("s:") ? "wss:" : "ws:";
    }
    ensureWsPath(parsed);
    return sanitizeUrl(parsed.toString());
  } catch (error) {
    console.warn(`Ignoring invalid realtime base url '${candidate}'`, error);
    return null;
  }
}

function applyApiPrefixIfNeeded(url: string, apiBase?: string | null): string {
  if (!apiBase || !apiBase.trim()) {
    return url;
  }
  try {
    const apiParsed = parseUrl(apiBase.trim());
    const prefix = apiParsed.pathname.replace(/\/$/, "");
    if (!prefix || prefix === "/") {
      return url;
    }
    const parsed = new URL(url);
    if (parsed.hostname !== apiParsed.hostname || parsed.port !== apiParsed.port) {
      return url;
    }
    if (!/^\/ws\/?$/i.test(parsed.pathname)) {
      return url;
    }
    parsed.pathname = `${prefix}/ws`;
    return sanitizeUrl(parsed.toString());
  } catch (error) {
    console.warn("Failed to align websocket path with API prefix", error);
    return url;
  }
}

function determineBaseUrl(options: RealtimeOptions = {}): string {
  const apiBase = import.meta.env.VITE_API_BASE_URL as string | undefined;
  const manualCandidates = [options.baseUrl, import.meta.env.VITE_WS_BASE_URL as string | undefined];

  for (const candidate of manualCandidates) {
    const normalized = normalizeCandidate(candidate);
    if (normalized) {
      return applyApiPrefixIfNeeded(normalized, apiBase);
    }
  }

  const derived = normalizeCandidate(apiBase);
  if (derived) {
    return applyApiPrefixIfNeeded(derived, apiBase);
  }

  if (typeof window !== "undefined" && window.location) {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return applyApiPrefixIfNeeded(`${protocol}//${window.location.host}/ws`, apiBase);
  }

  return "ws://localhost/ws";
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

export interface WebSocketOptions {
  token?: string;
  url?: string;
}

function resolveBaseUrl(custom?: string): string {
  const sanitize = (value: string) => value.replace(/\/$/, "");
  const directCandidates: Array<{ value?: string; source: string }> = [
    { value: custom, source: "options.url" },
    { value: import.meta.env.VITE_WS_BASE_URL as string | undefined, source: "VITE_WS_BASE_URL" }
  ];

  for (const candidate of directCandidates) {
    if (!candidate.value) {
      continue;
    }
    try {
      const parsed = new URL(candidate.value);
      return sanitize(parsed.toString());
    } catch (error) {
      console.warn(`Invalid WebSocket base URL provided via ${candidate.source}, falling back to auto detection.`, error);
    }
  }

  const apiBaseEnv = import.meta.env.VITE_API_BASE_URL as string | undefined;
  const apiBase = apiBaseEnv && apiBaseEnv.trim() ? apiBaseEnv : "http://localhost:8000/api";
  try {
    const parsed = new URL(apiBase);
    parsed.protocol = parsed.protocol === "https:" ? "wss:" : "ws:";
    const pathname = parsed.pathname.replace(/\/$/, "");
    parsed.pathname = pathname.endsWith("/api") ? `${pathname.replace(/\/api$/, "")}/ws` : `${pathname}/ws`;
    return sanitize(parsed.toString());
  } catch (error) {
    if (apiBaseEnv) {
      console.warn("Failed to derive WebSocket base URL from API base, using default detection.", error);
    }
  }

  if (typeof window !== "undefined" && window.location) {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return sanitize(`${protocol}//${window.location.host}/ws`);
  }

  return "ws://localhost:8000/ws";
}

function buildUrl(base: string, path: string, token?: string): string {
  const normalizedBase = base.endsWith("/") ? base : `${base}/`;
  const normalizedPath = path.startsWith("/") ? path.slice(1) : path;
  const url = new URL(normalizedPath, normalizedBase);
  if (token) {
    url.searchParams.set("token", token);
  }
  return url.toString();
}

export class GameSocket {
  private socket: WebSocket | null = null;
  private readonly options: WebSocketOptions;
  private readonly baseUrl: string;

  constructor(options: WebSocketOptions = {}) {
    this.options = options;
    this.baseUrl = resolveBaseUrl(options.url);
  }

  connect(path: string) {
    const target = buildUrl(this.baseUrl, path, this.options.token);
    this.socket = new WebSocket(target);
    return this.socket;
  }

  close() {
    this.socket?.close();
  }

  getRawInstance() {
    return this.socket;
  }

  isConnected() {
    return this.socket?.readyState === WebSocket.OPEN;
  }
}

export interface WebSocketOptions {
  token?: string;
  url?: string;
}

function resolveBaseUrl(custom?: string): string {
  const sanitize = (value: string) => value.replace(/\/$/, "");
  if (custom) {
    try {
      const parsed = new URL(custom);
      return sanitize(parsed.toString());
    } catch (error) {
      console.warn("Invalid WebSocket base URL provided, falling back to auto detection.", error);
    }
  }

  const apiBase = import.meta.env.VITE_API_BASE_URL as string | undefined;
  if (apiBase) {
    try {
      const parsed = new URL(apiBase);
      parsed.protocol = parsed.protocol === "https:" ? "wss:" : "ws:";
      const pathname = parsed.pathname.replace(/\/$/, "");
      parsed.pathname = pathname.endsWith("/api") ? `${pathname.replace(/\/api$/, "")}/ws` : `${pathname}/ws`;
      return sanitize(parsed.toString());
    } catch (error) {
      console.warn("Failed to derive WebSocket base URL from API base, using default.", error);
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

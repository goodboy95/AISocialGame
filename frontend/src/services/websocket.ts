export interface WebSocketOptions {
  token?: string;
  url?: string;
}

export class GameSocket {
  private socket: WebSocket | null = null;
  private readonly options: WebSocketOptions;

  constructor(options: WebSocketOptions = {}) {
    this.options = options;
  }

  connect(path: string) {
    const base = this.options.url ?? "ws://localhost:8000/ws";
    const token = this.options.token ? `?token=${this.options.token}` : "";
    const url = `${base}${path}${token}`;
    this.socket = new WebSocket(url);
    return this.socket;
  }

  close() {
    this.socket?.close();
  }
}

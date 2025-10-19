import axios from "axios";

/**
 * Central axios instance used by the application.  The file keeps the logic for resolving the base
 * URL, attaching tracing headers and exposing consistent error notifications in one place so that
 * feature modules stay lean.
 */

/**
 * Determines the correct API base URL by inspecting the Vite configuration and falling back to a
 * sensible local default.
 */
function resolveBaseURL(): string {
  const envBase = import.meta.env.VITE_API_BASE_URL as string | undefined;
  if (envBase && envBase.trim()) {
    return envBase;
  }

  if (typeof window !== "undefined" && window.location) {
    const origin = window.location.origin.replace(/\/$/, "");
    const hostname = window.location.hostname.toLowerCase();
    const isLocalHost = ["localhost", "127.0.0.1", "::1"].includes(hostname);

    if (!import.meta.env.DEV || !isLocalHost) {
      return `${origin}/api`;
    }
  }

  return "http://socialgame.seekerhut.com/api";
}

const baseURL = resolveBaseURL();
if (import.meta.env.DEV) {
  console.debug("Resolved API base URL", baseURL);
}

type NotificationHandler = (message: string) => void;

let notifyError: NotificationHandler | null = null;

async function loadNotifier(): Promise<NotificationHandler> {
  if (notifyError) {
    return notifyError;
  }
  const module = await import("../services/notifications");
  notifyError = module.notifyError;
  return notifyError!;
}

export const http = axios.create({
  baseURL,
  withCredentials: false
});

http.interceptors.request.use((config) => {
  const headers = config.headers ?? (config.headers = {});
  const requestId =
    typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
      ? crypto.randomUUID()
      : Math.random().toString(36).slice(2);
  headers["X-Request-ID"] = requestId;
  if (import.meta.env.DEV) {
    console.debug("HTTP request", {
      method: config.method,
      url: config.url,
      requestId
    });
  }
  return config;
});

/**
 * Tries to unwrap a human friendly error message from an axios error payload.
 */
function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const response = error.response;
    if (response?.data) {
      const data = response.data as Record<string, unknown>;
      if (typeof data.detail === "string") {
        return data.detail;
      }
      if (typeof data.message === "string") {
        return data.message;
      }
      if (Array.isArray(data.errors) && data.errors.length > 0) {
        const first = data.errors[0];
        if (typeof first === "string") {
          return first;
        }
        if (typeof first?.message === "string") {
          return first.message;
        }
      }
    }
    if (typeof response?.status === "number") {
      return `请求失败，状态码：${response.status}`;
    }
    return error.message || "请求失败，请稍后重试";
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return "请求失败，请稍后重试";
}

http.interceptors.response.use(
  (response) => {
    if (import.meta.env.DEV) {
      console.debug("HTTP response", {
        url: response.config.url,
        status: response.status,
        requestId: response.config.headers?.["X-Request-ID"]
      });
    }
    return response;
  },
  async (error) => {
    const message = extractErrorMessage(error);
    try {
      const notifier = await loadNotifier();
      notifier(message);
    } catch (notifyError) {
      console.error("Failed to notify error", notifyError);
    }
    console.error("API error", error);
    return Promise.reject(error);
  }
);

export default http;

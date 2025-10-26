import axios from "axios";
import { notifyError } from "../services/notifications";

function resolveBaseURL(): string {
  const envBase = import.meta.env.VITE_API_BASE_URL as string | undefined;
  if (envBase && envBase.trim()) {
    return envBase.trim();
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

const http = axios.create({
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
  return config;
});

http.interceptors.response.use(
    (response) => response,
    (error) => {
      const message = extractErrorMessage(error);
      notifyError(message);
      return Promise.reject(error);
    }
);

function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const response = error.response;
    const data = response?.data as Record<string, unknown> | undefined;
    if (data) {
      if (typeof data.detail === "string") {
        return data.detail;
      }
      if (typeof data.message === "string") {
        return data.message;
      }
    }
    if (typeof response?.status === "number") {
      return `请求失败，状态码：${response.status}`;
    }
    return error.message || "请求失败，请稍后重试";
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "请求失败，请稍后重试";
}

export default http;

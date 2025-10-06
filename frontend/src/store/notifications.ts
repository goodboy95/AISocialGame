import { defineStore } from "pinia";

export type NotificationLevel = "error" | "success" | "info" | "warning";

export interface NotificationItem {
  id: string;
  message: string;
  type: NotificationLevel;
  duration: number;
}

interface NotificationState {
  items: NotificationItem[];
}

function generateId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return Math.random().toString(36).slice(2);
}

export const useNotificationStore = defineStore("notifications", {
  state: (): NotificationState => ({
    items: [],
  }),
  actions: {
    push(message: string, type: NotificationLevel = "info", duration = 3000) {
      const id = generateId();
      const item: NotificationItem = {
        id,
        message,
        type,
        duration,
      };
      this.items.push(item);
      if (duration > 0 && typeof window !== "undefined") {
        window.setTimeout(() => {
          this.remove(id);
        }, duration);
      }
      return id;
    },
    remove(id: string) {
      this.items = this.items.filter((item) => item.id !== id);
    },
    clear() {
      this.items = [];
    },
  },
});

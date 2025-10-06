import pinia from "../store";
import { useNotificationStore, type NotificationLevel } from "../store/notifications";

function withStore() {
  return useNotificationStore(pinia);
}

export function notify(message: string, type: NotificationLevel = "info", duration = 3000) {
  const store = withStore();
  store.push(message, type, duration);
}

export function notifyError(message: string, duration = 3000) {
  notify(message, "error", duration);
}

export function notifySuccess(message: string, duration = 3000) {
  notify(message, "success", duration);
}

export function notifyWarning(message: string, duration = 3000) {
  notify(message, "warning", duration);
}

export function dismissNotification(id: string) {
  const store = withStore();
  store.remove(id);
}

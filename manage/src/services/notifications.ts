import { ElMessage } from 'element-plus';

export type NotificationLevel = 'success' | 'warning' | 'info' | 'error';

export function notify(message: string, type: NotificationLevel = 'info', duration = 3000) {
  ElMessage({
    message,
    type,
    duration
  });
}

export function notifySuccess(message: string, duration = 3000) {
  notify(message, 'success', duration);
}

export function notifyError(message: string, duration = 3000) {
  notify(message, 'error', duration);
}

export function notifyWarning(message: string, duration = 3000) {
  notify(message, 'warning', duration);
}

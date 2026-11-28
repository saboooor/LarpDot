import { createContextId, type Signal } from '@qwik.dev/core';

export interface ToastItem {
  id: string;
  title: string;
  description?: string;
  tone?: 'accent' | 'success' | 'error';
}

export const ToastContext = createContextId<Signal<ToastItem[]>>('toast-list');

export function pushToast(
  toasts: Signal<ToastItem[]>,
  toast: Omit<ToastItem, 'id'>
) {
  const id = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
  toasts.value = [...toasts.value.slice(-2), { ...toast, id }];
  if (typeof window !== 'undefined') {
    window.setTimeout(() => {
      toasts.value = toasts.value.filter((item) => item.id !== id);
    }, 3400);
  }
}

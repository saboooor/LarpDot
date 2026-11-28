import { component$, Slot, useContextProvider, useSignal } from '@qwik.dev/core';
import { Footer } from '~/components/Footer';
import { Nav } from '~/components/Nav';
import { ToastContext, type ToastItem } from '~/util/toast';

export default component$(() => {
  const toasts = useSignal<ToastItem[]>([]);
  useContextProvider(ToastContext, toasts);

  return (
    <div class="flex min-h-svh flex-col">
      <Nav />
      <main class="flex-1">
        <Slot />
      </main>
      <Footer />

      {toasts.value.length > 0 && (
        <div
          class="pointer-events-none fixed right-4 bottom-4 z-50 flex max-w-sm flex-col gap-2"
          role="status"
          aria-live="polite"
        >
          {toasts.value.map((toast) => (
            <div
              key={toast.id}
              class={{
                'lum-card pointer-events-auto px-4 py-3': true,
                'bg-emerald-950/85 text-emerald-100':
                  toast.tone === 'success',
                'bg-rose-950/85 text-rose-100':
                  toast.tone === 'error',
                'bg-lum-card-bg text-white':
                  !toast.tone || toast.tone === 'accent',
              }}
            >
              <div class="text-sm font-semibold">{toast.title}</div>
              {toast.description && (
                <p class="mt-0.5 text-xs opacity-80">{toast.description}</p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
});

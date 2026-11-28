import { component$ } from '@qwik.dev/core';
import { Link, type DocumentHead } from '@qwik.dev/router';

export default component$(() => {
  return (
    <section class="mx-auto flex min-h-[60svh] max-w-xl flex-col items-center justify-center gap-4 px-4 text-center">
      <div class="font-mono text-sm font-semibold text-indigo-300">404</div>
      <h1 class="text-3xl font-extrabold text-white">Page not found</h1>
      <p class="text-lum-text-secondary text-sm">
        The page you requested does not exist on LarpDot.
      </p>
      <div class="mt-2 flex gap-2">
        <Link
          href="/"
          class="lum-btn rounded-lum-1 bg-white/[0.06] text-sm text-white hover:bg-white/12"
        >
          Go Home
        </Link>
        <Link
          href="/presets"
          class="lum-btn lum-grad-bg-lum-accent rounded-lum-1 text-sm font-semibold text-white"
        >
          Browse Presets
        </Link>
      </div>
    </section>
  );
});

export const head: DocumentHead = {
  title: 'Page Not Found — LarpDot',
};

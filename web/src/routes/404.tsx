import { component$ } from '@qwik.dev/core';
import { Link, type DocumentHead } from '@qwik.dev/router';

export default component$(() => {
  return (
    <section class="mx-auto flex min-h-96 max-w-xl flex-col items-center justify-center gap-4 px-4 text-center">
      <div class="font-mono text-sm font-semibold text-lum-accent">404</div>
      <h1 class="text-3xl font-extrabold">Page not found</h1>
      <p class="text-lum-text-secondary text-sm">
        The page you requested does not exist on LarpDot.
      </p>
      <div class="mt-2 flex gap-2">
        <Link
          href="/"
          class="lum-btn text-sm"
        >
          Go Home
        </Link>
        <Link
          href="/presets"
          class="lum-btn lum-bg-lum-accent text-sm"
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

import { component$, isDev } from '@qwik.dev/core';
import { QwikRouterProvider, RouterOutlet } from '@qwik.dev/router';
import { RouterHead } from './components/Head';
import './global.css';

export default component$(() => {
  return (
    <QwikRouterProvider>
      <head>
        <meta charset="utf-8" />
        {!isDev && (
          <link
            rel="manifest"
            href={`${import.meta.env.BASE_URL}manifest.json`}
          />
        )}
        <RouterHead />
      </head>
      <body class="bg-gray-950 text-lum-text min-h-svh antialiased">
        <RouterOutlet />
      </body>
    </QwikRouterProvider>
  );
});

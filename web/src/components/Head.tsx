import { component$ } from '@qwik.dev/core';
import { useDocumentHead, useLocation } from '@qwik.dev/router';

export const RouterHead = component$(() => {
  const head = useDocumentHead();
  const loc = useLocation();

  return (
    <>
      <title>{head.title || 'LarpDot'}</title>
      <meta content={head.title || 'LarpDot'} property="og:title" />
      <meta content="#0c0e14" name="theme-color" />
      <link rel="canonical" href={loc.url.href} />
      <meta name="viewport" content="width=device-width, initial-scale=1.0" />
      <link rel="icon" type="image/svg+xml" href="/favicon.svg" />

      {head.meta.map((m, idx) => (
        <meta key={idx} {...m} />
      ))}

      {head.links.map((l, idx) => (
        <link key={idx} {...l} />
      ))}

      {head.styles.map((s, idx) => (
        <style key={idx} {...s.props} dangerouslySetInnerHTML={s.style} />
      ))}
    </>
  );
});

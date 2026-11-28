import {
  createQwikRouter,
  type PlatformCloudflarePages,
} from '@qwik.dev/router/middleware/cloudflare-pages';
import render from './entry.ssr';

declare global {
  // oxlint-disable-next-line typescript/no-empty-object-type
  interface QwikRouterPlatform extends PlatformCloudflarePages {}
}

const fetch = createQwikRouter({ render });

export { fetch };
export default { fetch };

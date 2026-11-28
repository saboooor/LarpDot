import { defineConfig, type UserConfig } from 'vite-plus';
import { qwikVite } from '@qwik.dev/core/optimizer';
import { qwikRouter } from '@qwik.dev/router/vite';
import tailwindcss from '@tailwindcss/vite';
import pkg from './package.json';
import { fmt, lint } from './vite.lint';

type PkgDep = Record<string, string>;
const { dependencies = {}, devDependencies = {} } = pkg as unknown as {
  dependencies: PkgDep;
  devDependencies: PkgDep;
};
errorOnDuplicatesPkgDeps(devDependencies, dependencies);

let platform = {};
if (process.env.NODE_ENV === 'development') {
  const { getPlatformProxy } = await import('wrangler');
  platform = await getPlatformProxy();
}

export default defineConfig((): UserConfig => {
  return {
    lint,
    fmt,
    resolve: {
      tsconfigPaths: true,
    },
    plugins: [
      qwikRouter({
        platform,
      }),
      qwikVite(),
      tailwindcss(),
    ],
    optimizeDeps: {
      exclude: [],
    },
    ssr: {
      noExternal: [
        'lucide-icons-qwik',
        'simple-icons-qwik',
        '@luminescent/ui-qwik',
        '@luminescent/icons-qwik',
      ],
    },
    server: {
      headers: {
        'Cache-Control': 'public, max-age=0',
      },
    },
    preview: {
      headers: {
        'Cache-Control': 'public, max-age=600',
      },
    },
  };
});

function errorOnDuplicatesPkgDeps(
  devDependencies: PkgDep,
  dependencies: PkgDep
) {
  const duplicateDeps = Object.keys(devDependencies).filter(
    (dep) => dependencies[dep]
  );

  const qwikPkg = Object.keys(dependencies).filter((value) =>
    /qwik/i.test(value)
  );

  if (qwikPkg.length > 0) {
    throw new Error(
      `Move qwik packages ${qwikPkg.join(', ')} to devDependencies`
    );
  }

  if (duplicateDeps.length > 0) {
    throw new Error(`
      Warning: The dependency "${duplicateDeps.join(', ')}" is listed in both "devDependencies" and "dependencies".
      Please move the duplicated dependencies to "devDependencies" only and remove it from "dependencies"
    `);
  }
}

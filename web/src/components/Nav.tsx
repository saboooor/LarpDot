import { component$, useSignal } from '@qwik.dev/core';
import { Link, useLocation } from '@qwik.dev/router';
import Menu from 'lucide-icons-qwik/icons/Menu';
import X from 'lucide-icons-qwik/icons/X';
import Layers from 'lucide-icons-qwik/icons/Layers';
import Upload from 'lucide-icons-qwik/icons/Upload';
import SiGithub from 'simple-icons-qwik/icons/SiGithub';
import { LarpDotLogo } from './LarpDotLogo';

export const GITHUB_REPO_URL = 'https://github.com/saboooor/LarpDot';
export const GITHUB_RELEASES_URL =
  'https://github.com/saboooor/LarpDot/releases';

export const Nav = component$(() => {
  const loc = useLocation();
  const mobileOpen = useSignal(false);

  const isHome = loc.url.pathname === '/';
  const isPresets =
    loc.url.pathname === '/presets/' ||
    loc.url.pathname === '/presets' ||
    (loc.url.pathname.startsWith('/presets/') &&
      !loc.url.pathname.startsWith('/presets/submit'));
  const isSubmit = loc.url.pathname.startsWith('/presets/submit');

  return (
    <header class="sticky top-0 z-50 bg-lum-card-bg">
      <div class="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
        <Link
          href="/"
          class="flex items-center gap-2.5 font-bold tracking-tight transition-opacity hover:opacity-90"
        >
          <span class="flex h-8 w-8 items-center justify-center rounded-xl bg-lum-input-bg">
            <LarpDotLogo size={20} />
          </span>
          <span class="text-lg">LarpDot</span>
        </Link>

        <nav class="hidden items-center gap-1.5 sm:flex">
          <Link
            href="/"
            class={{
              'lum-btn text-sm': true,
              'lum-bg-lum-accent text-white': isHome,
              'lum-bg-transparent text-lum-text-secondary hover:text-lum-text':
                !isHome,
            }}
          >
            Overview
          </Link>
          <Link
            href="/presets"
            class={{
              'lum-btn text-sm': true,
              'lum-bg-lum-accent text-white': isPresets,
              'lum-bg-transparent text-lum-text-secondary hover:text-lum-text':
                !isPresets,
            }}
          >
            <Layers size={16} />
            Preset Catalog
          </Link>
          <Link
            href="/presets/submit"
            class={{
              'lum-btn text-sm': true,
              'lum-bg-lum-accent text-white': isSubmit,
              'lum-bg-transparent text-lum-text-secondary hover:text-lum-text':
                !isSubmit,
            }}
          >
            <Upload size={16} />
            Submit Preset
          </Link>
          <a
            href={GITHUB_REPO_URL}
            target="_blank"
            rel="noopener noreferrer"
            class="lum-btn lum-bg-transparent text-lum-text-secondary hover:text-lum-text text-sm"
          >
            <span class="fill-current">
              <SiGithub size={16} />
            </span>
            GitHub
          </a>
        </nav>

        <button
          type="button"
          class="lum-btn lum-bg-transparent p-2 sm:hidden"
          aria-label="Toggle navigation menu"
          onClick$={() => {
            mobileOpen.value = !mobileOpen.value;
          }}
        >
          {mobileOpen.value ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>

      {mobileOpen.value && (
        <nav class="bg-lum-card-bg px-4 py-3 sm:hidden">
          <div class="flex flex-col gap-1.5">
            <Link
              href="/"
              class="lum-btn lum-bg-transparent justify-start text-sm"
              onClick$={() => {
                mobileOpen.value = false;
              }}
            >
              Overview
            </Link>
            <Link
              href="/presets"
              class="lum-btn lum-bg-transparent justify-start text-sm"
              onClick$={() => {
                mobileOpen.value = false;
              }}
            >
              <Layers size={16} />
              Preset Catalog
            </Link>
            <Link
              href="/presets/submit"
              class="lum-btn lum-bg-transparent justify-start text-sm"
              onClick$={() => {
                mobileOpen.value = false;
              }}
            >
              <Upload size={16} />
              Submit Preset
            </Link>
            <a
              href={GITHUB_REPO_URL}
              target="_blank"
              rel="noopener noreferrer"
              class="lum-btn lum-bg-transparent justify-start text-sm"
            >
              <span class="fill-current">
                <SiGithub size={16} />
              </span>
              GitHub
            </a>
          </div>
        </nav>
      )}
    </header>
  );
});

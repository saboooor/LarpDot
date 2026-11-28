import { component$ } from '@qwik.dev/core';
import { Link } from '@qwik.dev/router';
import { Luminescent } from '@luminescent/icons-qwik';
import SiGithub from 'simple-icons-qwik/icons/SiGithub';
import { GITHUB_RELEASES_URL, GITHUB_REPO_URL } from './Nav';
import { LarpDotLogo } from './LarpDotLogo';

export const Footer = component$(() => {
  return (
    <footer class="mt-20 border-t border-white/8 bg-[#090b10]/80">
      <div class="mx-auto flex max-w-6xl flex-col items-start justify-between gap-6 px-4 py-10 sm:flex-row sm:items-center sm:px-6">
        <div class="flex flex-col gap-1.5">
          <div class="flex items-center gap-2 font-semibold text-white">
            <LarpDotLogo size={18} />
            <span>LarpDot</span>
            <span class="text-lum-text-secondary text-xs font-normal">
              larpdot.luminescent.dev
            </span>
          </div>
          <p class="text-lum-text-secondary text-sm">
            Customizable Dynamic Island overlay for Android (API 26+).
          </p>
        </div>

        <div class="text-lum-text-secondary flex flex-wrap items-center gap-4 text-sm">
          <Link href="/presets" class="transition-colors hover:text-white">
            Preset Catalog
          </Link>
          <Link
            href="/presets/submit"
            class="transition-colors hover:text-white"
          >
            Submit Preset
          </Link>
          <a
            href={GITHUB_RELEASES_URL}
            target="_blank"
            rel="noopener noreferrer"
            class="transition-colors hover:text-white"
          >
            Releases
          </a>
          <a
            href={GITHUB_REPO_URL}
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex items-center gap-1.5 transition-colors hover:text-white"
          >
            <span class="fill-current">
              <SiGithub size={14} />
            </span>
            Source
          </a>
          <a
            href="https://saboor.ca"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex items-center gap-1.5 transition-colors hover:text-white"
          >
            <Luminescent size={14} />
            saboor.ca
          </a>
        </div>
      </div>
    </footer>
  );
});

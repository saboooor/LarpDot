import { component$ } from '@qwik.dev/core';
import {
  Link,
  routeLoader$,
  type DocumentHead,
} from '@qwik.dev/router';
import { desc } from 'drizzle-orm';
import ArrowRight from 'lucide-icons-qwik/icons/ArrowRight';
import BellRing from 'lucide-icons-qwik/icons/BellRing';
import Download from 'lucide-icons-qwik/icons/Download';
import Flashlight from 'lucide-icons-qwik/icons/Flashlight';
import Layers from 'lucide-icons-qwik/icons/Layers';
import Music from 'lucide-icons-qwik/icons/Music';
import Scan from 'lucide-icons-qwik/icons/Scan';
import SlidersHorizontal from 'lucide-icons-qwik/icons/SlidersHorizontal';
import Upload from 'lucide-icons-qwik/icons/Upload';
import SiGithub from 'simple-icons-qwik/icons/SiGithub';
import { HeroIslandDemo } from '~/components/IslandPreview';
import { GITHUB_RELEASES_URL, GITHUB_REPO_URL } from '~/components/Nav';
import { PresetCard } from '~/components/PresetCard';
import { getDB, presets, type CatalogPreset } from '~/util/db';

export const useFeaturedPresets = routeLoader$(async () => {
  try {
    const db = getDB();
    const featured = await db
      .select()
      .from(presets)
      .orderBy(desc(presets.copies), desc(presets.createdAt))
      .limit(4)
      .all();
    return featured;
  } catch {
    return [] as CatalogPreset[];
  }
});

const FEATURES = [
  {
    icon: Scan,
    title: 'Cutout Alignment',
    description:
      'Auto-detects your front camera cutout with fine manual controls for pill size, horizontal offset, and vertical alignment.',
  },
  {
    icon: Music,
    title: 'Music Island',
    description:
      'Live playback info, album art, customizable player controls, and visualizer modes (BPM, device audio capture, or audio preview).',
  },
  {
    icon: BellRing,
    title: 'Live Activities',
    description:
      'Turns ongoing Android notifications — navigation, timers, calls, downloads, and progress updates — into compact island pills.',
  },
  {
    icon: Flashlight,
    title: 'Flashlight Island',
    description:
      'Slide and hold on the pill to step smoothly through brightness levels, with optional strength outlines and Camera2 boost on supported Pixel devices.',
  },
  {
    icon: Layers,
    title: 'Custom Islands & Bundles',
    description:
      'Build custom compact wings and expanded layouts triggered by apps, notifications, or swipe-down — and share single presets or full app bundles.',
  },
  {
    icon: SlidersHorizontal,
    title: 'Display Controls',
    description:
      'Fine-tune tap actions, lock-screen and notification-shade visibility via Accessibility, and per-app exclusions.',
  },
];

export default component$(() => {
  const featuredPresets = useFeaturedPresets();

  return (
    <div class="mx-auto max-w-6xl px-4 sm:px-6">
      {/* Hero */}
      <section class="grid items-center gap-12 pt-14 pb-16 lg:grid-cols-12 lg:pt-20 lg:pb-24">
        <div class="flex flex-col gap-6 lg:col-span-7">
          <div class="inline-flex w-fit items-center gap-2 rounded-full border border-white/10 bg-white/[0.04] px-3.5 py-1 text-xs font-medium text-indigo-200">
            Android 8.0+ (API 26) • Kotlin & Jetpack Compose
          </div>

          <h1 class="text-4xl font-extrabold tracking-tight text-white sm:text-5xl lg:text-[3.35rem] lg:leading-[1.08]">
            A customizable Dynamic Island overlay for Android.
          </h1>

          <p class="text-lum-text-secondary max-w-xl text-base leading-relaxed sm:text-lg">
            LarpDot wraps your front camera cutout with music playback, ongoing
            live activities, flashlight controls, and shareable Custom Islands
            built with Material 3 Expressive.
          </p>

          <div class="flex flex-wrap items-center gap-3 pt-1">
            <Link
              href="/presets"
              class="lum-btn lum-grad-bg-lum-accent rounded-lum-1 px-5 py-3 text-sm font-semibold text-white"
            >
              <Layers size={18} />
              Browse Preset Catalog
              <ArrowRight size={16} />
            </Link>
            <a
              href={GITHUB_RELEASES_URL}
              target="_blank"
              rel="noopener noreferrer"
              class="lum-btn rounded-lum-1 border border-white/12 bg-white/[0.05] px-5 py-3 text-sm font-semibold text-white hover:bg-white/10"
            >
              <Download size={18} />
              Download APK
            </a>
            <a
              href={GITHUB_REPO_URL}
              target="_blank"
              rel="noopener noreferrer"
              class="lum-btn lum-bg-transparent rounded-lum-1 text-lum-text-secondary px-4 py-3 text-sm hover:text-white"
            >
              <span class="fill-current">
                <SiGithub size={16} />
              </span>
              GitHub
            </a>
          </div>
        </div>

        <div class="lg:col-span-5">
          <HeroIslandDemo />
        </div>
      </section>

      {/* Features */}
      <section class="border-t border-white/8 py-16">
        <div class="mb-10 max-w-2xl">
          <h2 class="text-2xl font-bold tracking-tight text-white sm:text-3xl">
            Built around your cutout
          </h2>
          <p class="text-lum-text-secondary mt-2 text-sm sm:text-base">
            Everything stays lightweight, local to your device, and tailored to
            how you use your phone.
          </p>
        </div>

        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((feature) => {
            const Icon = feature.icon;
            return (
              <div
                key={feature.title}
                class="lum-card lum-grad-bg-lum-card-bg/80 border border-white/8 p-5"
              >
                <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-500/15 text-indigo-300 ring-1 ring-indigo-400/20">
                  <Icon size={20} />
                </div>
                <h3 class="text-base font-bold text-white">{feature.title}</h3>
                <p class="text-lum-text-secondary mt-1.5 text-sm leading-relaxed">
                  {feature.description}
                </p>
              </div>
            );
          })}
        </div>
      </section>

      {/* Community Presets Preview */}
      <section class="border-t border-white/8 py-16">
        <div class="mb-8 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
          <div>
            <h2 class="text-2xl font-bold tracking-tight text-white sm:text-3xl">
              Custom Island Preset Catalog
            </h2>
            <p class="text-lum-text-secondary mt-2 text-sm sm:text-base">
              Copy a <code class="text-indigo-300">LARPDOT1:</code> share code
              or download a JSON bundle and import it directly in LarpDot.
            </p>
          </div>
          <div class="flex flex-wrap gap-2">
            <Link
              href="/presets/submit"
              class="lum-btn rounded-lum-1 border border-white/12 bg-white/[0.05] text-sm text-white hover:bg-white/10"
            >
              <Upload size={16} />
              Submit Preset
            </Link>
            <Link
              href="/presets"
              class="lum-btn lum-grad-bg-lum-accent rounded-lum-1 text-sm font-semibold text-white"
            >
              View All
              <ArrowRight size={16} />
            </Link>
          </div>
        </div>

        {featuredPresets.value.length > 0 ? (
          <div class="grid gap-4 sm:grid-cols-2">
            {featuredPresets.value.map((preset) => (
              <PresetCard key={preset.id} preset={preset} />
            ))}
          </div>
        ) : (
          <div class="lum-card lum-grad-bg-lum-card-bg/70 flex flex-col items-center justify-center gap-3 border border-white/8 px-6 py-12 text-center">
            <p class="text-base font-semibold text-white">
              No community presets published yet
            </p>
            <p class="text-lum-text-secondary max-w-md text-sm">
              Export any Custom Island or App Bundle from LarpDot as a{' '}
              <code class="text-indigo-300">LARPDOT1:</code> share code or JSON
              file and be the first to share it here.
            </p>
            <Link
              href="/presets/submit"
              class="lum-btn lum-grad-bg-lum-accent rounded-lum-1 mt-2 text-sm font-semibold text-white"
            >
              <Upload size={16} />
              Publish the first preset
            </Link>
          </div>
        )}
      </section>
    </div>
  );
});

export const head: DocumentHead = {
  title: 'LarpDot — Dynamic Island Overlay & Preset Catalog for Android',
  meta: [
    {
      name: 'description',
      content:
        'LarpDot adds a customizable Dynamic Island overlay around your Android front camera cutout. Browse and share Custom Island presets and app bundles.',
    },
    {
      property: 'og:description',
      content:
        'Customizable Dynamic Island overlay for Android with music playback, live activities, flashlight controls, and shareable Custom Island presets.',
    },
  ],
};

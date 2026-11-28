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
          <div class="text-sm text-lum-text-secondary">
            Android 8.0+ (API 26) • Kotlin & Jetpack Compose
          </div>

          <h1 class="text-4xl font-extrabold tracking-tight sm:text-5xl leading-tight">
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
              class="lum-btn lum-bg-lum-accent px-5 py-3 text-sm"
            >
              <Layers size={18} />
              Browse Preset Catalog
              <ArrowRight size={16} />
            </Link>
            <a
              href={GITHUB_RELEASES_URL}
              target="_blank"
              rel="noopener noreferrer"
              class="lum-btn px-5 py-3 text-sm"
            >
              <Download size={18} />
              Download APK
            </a>
            <a
              href={GITHUB_REPO_URL}
              target="_blank"
              rel="noopener noreferrer"
              class="lum-btn lum-bg-transparent text-lum-text-secondary px-4 py-3 text-sm hover:text-lum-text"
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
      <section class="py-16">
        <div class="mb-10 max-w-2xl">
          <h2 class="text-2xl font-bold tracking-tight sm:text-3xl">
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
                class="lum-card p-5"
              >
                <div class="text-lum-accent">
                  <Icon size={20} />
                </div>
                <h3 class="text-base font-bold">{feature.title}</h3>
                <p class="text-lum-text-secondary mt-1.5 text-sm leading-relaxed">
                  {feature.description}
                </p>
              </div>
            );
          })}
        </div>
      </section>

      {/* Community Presets Preview */}
      <section class="py-16">
        <div class="mb-8 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
          <div>
            <h2 class="text-2xl font-bold tracking-tight sm:text-3xl">
              Custom Island Preset Catalog
            </h2>
            <p class="text-lum-text-secondary mt-2 text-sm sm:text-base">
              Copy a <code class="text-lum-accent">LARPDOT1:</code> share code
              or download a JSON bundle and import it directly in LarpDot.
            </p>
          </div>
          <div class="flex flex-wrap gap-2">
            <Link
              href="/presets/submit"
              class="lum-btn text-sm"
            >
              <Upload size={16} />
              Submit Preset
            </Link>
            <Link
              href="/presets"
              class="lum-btn lum-bg-lum-accent text-sm"
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
          <div class="lum-card items-center justify-center gap-3 px-6 py-12 text-center">
            <p class="text-base font-semibold">
              No community presets published yet
            </p>
            <p class="text-lum-text-secondary max-w-md text-sm">
              Export any Custom Island or App Bundle from LarpDot as a{' '}
              <code class="text-lum-accent">LARPDOT1:</code> share code or JSON
              file and be the first to share it here.
            </p>
            <Link
              href="/presets/submit"
              class="lum-btn lum-bg-lum-accent mt-2 text-sm"
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

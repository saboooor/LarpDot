import { component$, useSignal } from '@qwik.dev/core';
import AudioWaveform from 'lucide-icons-qwik/icons/AudioWaveform';
import Battery from 'lucide-icons-qwik/icons/Battery';
import Bell from 'lucide-icons-qwik/icons/Bell';
import Bluetooth from 'lucide-icons-qwik/icons/Bluetooth';
import Car from 'lucide-icons-qwik/icons/Car';
import Disc3 from 'lucide-icons-qwik/icons/Disc3';
import Download from 'lucide-icons-qwik/icons/Download';
import Flashlight from 'lucide-icons-qwik/icons/Flashlight';
import Gamepad2 from 'lucide-icons-qwik/icons/Gamepad2';
import LayoutGrid from 'lucide-icons-qwik/icons/LayoutGrid';
import Headphones from 'lucide-icons-qwik/icons/Headphones';
import Home from 'lucide-icons-qwik/icons/Home';
import Image from 'lucide-icons-qwik/icons/Image';
import Layers from 'lucide-icons-qwik/icons/Layers';
import Lock from 'lucide-icons-qwik/icons/Lock';
import Mic from 'lucide-icons-qwik/icons/Mic';
import Music from 'lucide-icons-qwik/icons/Music';
import Navigation from 'lucide-icons-qwik/icons/Navigation';
import Palette from 'lucide-icons-qwik/icons/Palette';
import Pause from 'lucide-icons-qwik/icons/Pause';
import Phone from 'lucide-icons-qwik/icons/Phone';
import Play from 'lucide-icons-qwik/icons/Play';
import Power from 'lucide-icons-qwik/icons/Power';
import Search from 'lucide-icons-qwik/icons/Search';
import Settings from 'lucide-icons-qwik/icons/Settings';
import Share2 from 'lucide-icons-qwik/icons/Share2';
import Shield from 'lucide-icons-qwik/icons/Shield';
import SkipBack from 'lucide-icons-qwik/icons/SkipBack';
import SkipForward from 'lucide-icons-qwik/icons/SkipForward';
import Smartphone from 'lucide-icons-qwik/icons/Smartphone';
import Sparkles from 'lucide-icons-qwik/icons/Sparkles';
import Star from 'lucide-icons-qwik/icons/Star';
import Sun from 'lucide-icons-qwik/icons/Sun';
import Timer from 'lucide-icons-qwik/icons/Timer';
import Upload from 'lucide-icons-qwik/icons/Upload';
import Volume2 from 'lucide-icons-qwik/icons/Volume2';
import VolumeX from 'lucide-icons-qwik/icons/VolumeX';
import Watch from 'lucide-icons-qwik/icons/Watch';
import {
  islandHasExpandedContent,
  resolvePreviewColor,
  slotHasContent,
  type CustomIslandIcon,
  type CustomIslandSlot,
  type PortableCustomIsland,
} from '~/util/presets';

const SlotIconGlyph = component$<{
  icon: CustomIslandIcon;
  size?: number;
}>(({ icon, size = 14 }) => {
  switch (icon) {
    case 'MUSIC':
    case 'AUDIO_TRACK':
      return <Music size={size} />;
    case 'WAVEFORM':
      return <AudioWaveform size={size} />;
    case 'FLASHLIGHT':
      return <Flashlight size={size} />;
    case 'BATTERY':
    case 'BATTERY_ALERT':
    case 'BATTERY_SAVER':
      return <Battery size={size} />;
    case 'HEADPHONES':
    case 'EARBUDS':
      return <Headphones size={size} />;
    case 'TIMER':
    case 'HOURGLASS':
      return <Timer size={size} />;
    case 'CALL':
      return <Phone size={size} />;
    case 'STAR':
      return <Star size={size} />;
    case 'NAVIGATION':
      return <Navigation size={size} />;
    case 'NOTIFICATIONS':
    case 'BUBBLES':
      return <Bell size={size} />;
    case 'HOME':
      return <Home size={size} />;
    case 'PHOTO':
    case 'SCREENSHOT':
      return <Image size={size} />;
    case 'BLUETOOTH':
      return <Bluetooth size={size} />;
    case 'BRIGHTNESS':
    case 'SUN':
      return <Sun size={size} />;
    case 'CAR':
    case 'SPEED':
      return <Car size={size} />;
    case 'DOWNLOAD':
      return <Download size={size} />;
    case 'UPLOAD':
      return <Upload size={size} />;
    case 'GAME':
      return <Gamepad2 size={size} />;
    case 'LAYERS':
      return <Layers size={size} />;
    case 'LOCK':
      return <Lock size={size} />;
    case 'MIC':
      return <Mic size={size} />;
    case 'PALETTE':
      return <Palette size={size} />;
    case 'PAUSE':
      return <Pause size={size} />;
    case 'PLAY':
      return <Play size={size} />;
    case 'POWER':
      return <Power size={size} />;
    case 'SEARCH':
      return <Search size={size} />;
    case 'SECURITY':
      return <Shield size={size} />;
    case 'SETTINGS':
      return <Settings size={size} />;
    case 'SHARE':
      return <Share2 size={size} />;
    case 'SPEAKER':
    case 'VOLUME':
      return <Volume2 size={size} />;
    case 'VOLUME_OFF':
      return <VolumeX size={size} />;
    case 'WATCH':
      return <Watch size={size} />;
    case 'PHONE':
    case 'ANDROID':
      return <Smartphone size={size} />;
    case 'APPS':
    default:
      return <LayoutGrid size={size} />;
  }
});

function renderPlaceholderText(raw: string) {
  const parts = raw.split(/(\{[a-zA-Z0-9_]+\})/g);
  return parts.map((part, idx) =>
    /^\{[a-zA-Z0-9_]+\}$/.test(part) ? (
      <span
        key={idx}
        class="mx-0.5 inline-block rounded bg-indigo-400/25 px-1 py-0.5 font-mono text-sm text-indigo-200"
      >
        {part}
      </span>
    ) : (
      <span key={idx}>{part}</span>
    )
  );
}

const SlotContent = component$<{
  slot: CustomIslandSlot;
  size?: 'sm' | 'md';
}>(({ slot, size = 'sm' }) => {
  const iconSize = size === 'md' ? 18 : 13;

  if (slot.text?.trim()) {
    const color = resolvePreviewColor(
      slot.textStyle?.colorMode,
      slot.textStyle?.rgb,
      '#c7d2fe'
    );
    return (
      <span
        class="max-w-28 truncate leading-none"
        style={{
          color,
          fontSize: `${Math.min(Math.max(slot.textStyle?.size ?? 11, 10), 14)}px`,
          fontWeight: slot.textStyle?.bold ? 700 : 500,
          fontStyle: slot.textStyle?.italic ? 'italic' : 'normal',
          textDecoration: slot.textStyle?.underline ? 'underline' : 'none',
        }}
      >
        {renderPlaceholderText(slot.text)}
      </span>
    );
  }

  if (slot.image === 'ALBUM_ART') {
    return (
      <span
        class={{
          'bg-lum-accent flex items-center justify-center rounded-md text-white': true,
          'h-5 w-5': size === 'sm',
          'h-9 w-9 rounded-xl': size === 'md',
        }}
      >
        <Disc3 size={iconSize} />
      </span>
    );
  }

  if (slot.image === 'APP_ICON') {
    return (
      <span
        class={{
          'bg-lum-accent flex items-center justify-center rounded-full text-white': true,
          'h-5 w-5': size === 'sm',
          'h-9 w-9 rounded-xl': size === 'md',
        }}
      >
        <Sparkles size={iconSize - 1} />
      </span>
    );
  }

  if (slot.image === 'NOTIFICATION_ICON') {
    const color = resolvePreviewColor(
      slot.iconColor?.mode,
      slot.iconColor?.rgb,
      '#93c5fd'
    );
    return (
      <span class="flex items-center justify-center" style={{ color }}>
        <Bell size={iconSize} />
      </span>
    );
  }

  if (slot.image === 'NOTIFICATION_IMAGE') {
    return (
      <span
        class={{
          'bg-lum-input-bg flex items-center justify-center rounded-md text-slate-200': true,
          'h-5 w-5': size === 'sm',
          'h-9 w-9 rounded-xl': size === 'md',
        }}
      >
        <Image size={iconSize} />
      </span>
    );
  }

  if (slot.icon) {
    const color = resolvePreviewColor(
      slot.iconColor?.mode,
      slot.iconColor?.rgb,
      '#a5b4fc'
    );
    return (
      <span class="flex items-center justify-center" style={{ color }}>
        <SlotIconGlyph icon={slot.icon} size={iconSize} />
      </span>
    );
  }

  return null;
});

interface IslandPreviewProps {
  island: PortableCustomIsland;
  mode?: 'compact' | 'expanded' | 'interactive';
}

export const IslandPreview = component$<IslandPreviewProps>(
  ({ island, mode = 'interactive' }) => {
    const hasExpanded = islandHasExpandedContent(island);
    const isSwipeDownOnly = island.visibility === 'SWIPE_DOWN';
    const showExpanded = useSignal(
      mode === 'expanded' || (isSwipeDownOnly && hasExpanded)
    );

    const hasLeft = !isSwipeDownOnly && slotHasContent(island.LEFT);
    const hasRight = !isSwipeDownOnly && slotHasContent(island.RIGHT);
    const expandedActive =
      mode === 'expanded' ||
      (mode === 'interactive' && showExpanded.value && hasExpanded);

    return (
      <div class="flex flex-col items-center gap-2.5">
        {!expandedActive ? (
          <div class="island-shell inline-flex min-h-9 items-center gap-2.5 rounded-full px-3 py-1.5 transition-all duration-300">
            {hasLeft && (
              <div class="flex min-w-5 items-center justify-center">
                <SlotContent slot={island.LEFT} size="sm" />
              </div>
            )}
            <div class="camera-cutout h-4 w-4 shrink-0 rounded-full" />
            {hasRight && (
              <div class="flex min-w-5 items-center justify-center">
                <SlotContent slot={island.RIGHT} size="sm" />
              </div>
            )}
            {!hasLeft && !hasRight && (
              <span class="text-xs font-medium text-white/45">
                {isSwipeDownOnly ? 'Swipe down' : 'Dot'}
              </span>
            )}
          </div>
        ) : (
          <div class="island-shell relative w-full max-w-76 overflow-hidden rounded-3xl p-3.5 transition-all duration-300">
            {island.backgroundStyle && island.backgroundStyle !== 'NONE' && (
              <div
                aria-hidden="true"
                class={{
                  'pointer-events-none absolute inset-0': true,
                  'bg-gradient-to-r from-indigo-500/35 via-sky-500/15 to-transparent':
                    island.backgroundStyle === 'BASIC_FADED',
                  'bg-gradient-to-r from-indigo-500/45 via-sky-500/20 to-transparent':
                    island.backgroundStyle === 'BLENDED',
                  'bg-gradient-to-b from-indigo-500/35 via-sky-500/20 to-black/40':
                    island.backgroundStyle === 'FULL_BACKGROUND',
                  'bg-gradient-to-b from-indigo-500/30 via-sky-500/18 to-black/35 blur-sm':
                    island.backgroundStyle === 'BLURRED_FULL_BACKGROUND',
                }}
              />
            )}
            <div class="relative z-10 mb-2.5 flex items-center justify-between gap-2">
              <div class="flex min-w-8 items-center">
                {slotHasContent(island.EXPANDED_LEFT) ? (
                  <SlotContent slot={island.EXPANDED_LEFT} size="md" />
                ) : (
                  <span class="h-1 w-6 rounded-full bg-white/10" />
                )}
              </div>
              <div class="camera-cutout h-3.5 w-3.5 shrink-0 rounded-full" />
              <div class="flex min-w-8 items-center justify-end">
                {slotHasContent(island.EXPANDED_RIGHT) ? (
                  <SlotContent slot={island.EXPANDED_RIGHT} size="md" />
                ) : (
                  <span class="h-1 w-6 rounded-full bg-white/10" />
                )}
              </div>
            </div>

            {island.components.length > 0 ? (
              <div class="relative z-10 flex flex-col gap-2">
                {island.components.map((comp, idx) => {
                  if (comp.kind === 'TEXT') {
                    const color = resolvePreviewColor(
                      comp.textStyle?.colorMode,
                      comp.textStyle?.rgb,
                      '#e2e8f0'
                    );
                    const alignClass =
                      comp.textStyle?.alignment === 'CENTER'
                        ? 'text-center'
                        : comp.textStyle?.alignment === 'END'
                          ? 'text-right'
                          : 'text-left';
                    return (
                      <div
                        key={idx}
                        class={`truncate rounded-xl bg-white/5 px-3 py-2 text-xs ${alignClass}`}
                        style={{
                          color,
                          fontWeight: comp.textStyle?.bold ? 700 : 500,
                          fontStyle: comp.textStyle?.italic
                            ? 'italic'
                            : 'normal',
                          textDecoration: comp.textStyle?.underline
                            ? 'underline'
                            : 'none',
                        }}
                      >
                        {comp.text || 'Custom text'}
                      </div>
                    );
                  }

                  if (comp.kind === 'BUTTONS') {
                    return (
                      <div
                        key={idx}
                        class="flex items-center justify-center gap-3 rounded-xl bg-white/5 py-2"
                      >
                        <span class="flex h-7 w-7 items-center justify-center rounded-full bg-white/8 text-white/75">
                          <SkipBack size={13} />
                        </span>
                        <span
                          class={{
                            'flex h-8 w-10 items-center justify-center rounded-full text-white': true,
                            'bg-indigo-500': Boolean(
                              comp.buttonStyle?.playAccent
                            ),
                            'bg-white/16': !comp.buttonStyle?.playAccent,
                          }}
                        >
                          <Play size={14} />
                        </span>
                        <span class="flex h-7 w-7 items-center justify-center rounded-full bg-white/8 text-white/75">
                          <SkipForward size={13} />
                        </span>
                      </div>
                    );
                  }

                  if (comp.kind === 'ALBUM_ART') {
                    return (
                      <div
                        key={idx}
                        class="flex items-center gap-2.5 rounded-xl bg-white/5 p-2"
                      >
                        <div class="bg-lum-accent flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-white">
                          <Disc3 size={18} />
                        </div>
                        <div class="min-w-0 flex-1">
                          <div class="h-2.5 w-24 rounded-full bg-white/75" />
                          <div class="mt-1.5 h-2 w-16 rounded-full bg-white/30" />
                        </div>
                      </div>
                    );
                  }

                  if (comp.kind === 'NOTIFICATION_IMAGE') {
                    return (
                      <div
                        key={idx}
                        class="bg-lum-input-bg flex h-16 items-center justify-center gap-2 rounded-xl text-xs text-white/60"
                      >
                        <Image size={15} />
                        Notification image
                      </div>
                    );
                  }

                  if (comp.kind === 'APP_ICON') {
                    return (
                      <div
                        key={idx}
                        class="flex items-center justify-center rounded-xl bg-white/5 p-2.5"
                      >
                        <div class="bg-lum-accent flex h-10 w-10 items-center justify-center rounded-xl text-white">
                          <LayoutGrid size={18} />
                        </div>
                      </div>
                    );
                  }

                  if (comp.kind === 'NOTIFICATION_ICON') {
                    return (
                      <div
                        key={idx}
                        class="flex items-center justify-center rounded-xl bg-white/5 p-2.5 text-sky-300"
                      >
                        <Bell size={20} />
                      </div>
                    );
                  }

                  return (
                    <div
                      key={idx}
                      class="rounded-xl bg-white/5 px-3 py-2 text-left"
                    >
                      <div class="flex items-center gap-1.5 text-xs font-semibold text-white/85">
                        <Bell size={11} class="text-indigo-300" />
                        Notification title
                      </div>
                      <div class="mt-1 text-xs text-white/50">
                        Live notification text & actions
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div class="rounded-xl bg-white/5 px-3 py-2 text-center text-xs text-white/50">
                Expanded wing layout
              </div>
            )}
          </div>
        )}

        {mode === 'interactive' && hasExpanded && !isSwipeDownOnly && (
          <button
            type="button"
            class="lum-btn text-xs"
            onClick$={() => {
              showExpanded.value = !showExpanded.value;
            }}
          >
            {showExpanded.value ? 'Show compact pill' : 'Preview expanded'}
          </button>
        )}
      </div>
    );
  }
);

type HeroMode = 'music' | 'live' | 'flashlight' | 'custom';

interface DemoTrack {
  title: string;
  artist: string;
  bpm: number;
  accent: string;
  artBg: string;
  artGlow: string;
  progress: number;
}

const DEMO_TRACKS: DemoTrack[] = [
  {
    title: 'Midnight City',
    artist: 'M83',
    bpm: 105,
    accent: '#ff5e9c',
    artBg:
      'radial-gradient(circle at 72% 28%, #ff5e9c 0%, transparent 52%), radial-gradient(circle at 26% 74%, #38bdf8 0%, transparent 56%), linear-gradient(145deg, #1e1038 0%, #3b1254 52%, #0c142c 100%)',
    artGlow: 'rgba(255, 94, 156, 0.36)',
    progress: 0.44,
  },
  {
    title: 'Starboy',
    artist: 'The Weeknd',
    bpm: 186,
    accent: '#ff477e',
    artBg:
      'radial-gradient(circle at 50% 42%, #ff2e63 0%, transparent 48%), radial-gradient(circle at 50% 80%, #00e5ff 0%, transparent 54%), linear-gradient(155deg, #2b0918 0%, #0b1126 100%)',
    artGlow: 'rgba(255, 71, 126, 0.36)',
    progress: 0.62,
  },
  {
    title: 'Resonance',
    artist: 'HOME',
    bpm: 100,
    accent: '#ffb057',
    artBg:
      'radial-gradient(circle at 50% 34%, #ffb057 0%, #f97316 32%, transparent 58%), linear-gradient(180deg, #1e1b4b 0%, #581c87 52%, #0f172a 100%)',
    artGlow: 'rgba(255, 176, 87, 0.34)',
    progress: 0.31,
  },
];

const WAVE_BARS = [
  { height: 7, delay: '0ms' },
  { height: 14, delay: '-180ms' },
  { height: 18, delay: '-360ms' },
  { height: 11, delay: '-120ms' },
  { height: 15, delay: '-290ms' },
];

export const HeroIslandDemo = component$(() => {
  const activeMode = useSignal<HeroMode>('music');
  const expanded = useSignal(true);
  const trackIndex = useSignal(0);
  const isPlaying = useSignal(true);
  const torchOn = useSignal(true);
  const torchBrightness = useSignal(80);
  const navEnded = useSignal(false);
  const customAck = useSignal(false);

  const modes: Array<{ id: HeroMode; label: string }> = [
    { id: 'music', label: 'Music' },
    { id: 'live', label: 'Live Activity' },
    { id: 'flashlight', label: 'Flashlight' },
    { id: 'custom', label: 'Custom Island' },
  ];

  const track = DEMO_TRACKS[trackIndex.value];
  const torchFraction = torchOn.value ? torchBrightness.value / 100 : 0;
  const ringRadius = 9.5;
  const ringCircumference = 2 * Math.PI * ringRadius;
  const torchDashOffset = ringCircumference * (1 - torchFraction);

  const expandedRingRadius = 18;
  const expandedRingCircumference = 2 * Math.PI * expandedRingRadius;
  const expandedTorchDashOffset =
    expandedRingCircumference * (1 - torchFraction);

  const beamTravel = (torchBrightness.value - 10) / 90;
  const handleTopPct = 14 + (1 - beamTravel) * 52;
  const handleWidthPx = Math.round(48 + beamTravel * 68);

  return (
    <div class="flex flex-col items-center gap-4">
      <div class="w-full max-w-sm">
        <div class="hero-phone-frame">
          <div class="hero-island-stage relative overflow-hidden">
            {/* Android status bar aligned with the front camera cutout */}
            <div
              aria-hidden="true"
              class="hero-status-bar pointer-events-none absolute inset-x-5 top-3.5 z-10 flex h-[30px] items-center justify-between text-[11px] font-semibold tracking-tight text-white/90"
            >
              <span>9:41</span>
              <span class="flex items-center gap-1.5">
                <svg
                  width="12"
                  height="10"
                  viewBox="0 0 12 10"
                  fill="currentColor"
                  class="opacity-90"
                >
                  <rect x="0.5" y="6.5" width="2" height="3" rx="0.6" />
                  <rect x="3.5" y="4.5" width="2" height="5" rx="0.6" />
                  <rect x="6.5" y="2.5" width="2" height="7" rx="0.6" />
                  <rect x="9.5" y="0.5" width="2" height="9" rx="0.6" />
                </svg>
                <svg
                  width="13"
                  height="10"
                  viewBox="0 0 16 12"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.7"
                  stroke-linecap="round"
                  class="opacity-90"
                >
                  <path d="M1.5 4.2C5.2 1.1 10.8 1.1 14.5 4.2" />
                  <path d="M4.2 7C6.4 5.1 9.6 5.1 11.8 7" />
                  <circle
                    cx="8"
                    cy="10"
                    r="1.2"
                    fill="currentColor"
                    stroke="none"
                  />
                </svg>
                <span class="inline-flex items-center gap-0.5">
                  <span class="relative inline-flex h-[10px] w-[18px] items-center rounded-[3px] border border-white/65 p-[1.5px]">
                    <span class="h-full w-[82%] rounded-[1px] bg-white" />
                  </span>
                </span>
              </span>
            </div>

            {/* Dynamic Island shell */}
            <div
              data-mode={activeMode.value}
              data-expanded={expanded.value ? 'true' : 'false'}
              class="hero-island relative mx-auto overflow-hidden text-left text-white"
              style={{
                '--hero-accent': track.accent,
                '--hero-art-bg': track.artBg,
                '--hero-art-glow': track.artGlow,
              }}
            >
              {/* Full-bleed background toggle button (avoids nested <button>) */}
              <button
                type="button"
                aria-label={`${expanded.value ? 'Minimize' : 'Expand'} ${activeMode.value} island preview`}
                aria-expanded={expanded.value}
                onClick$={() => {
                  expanded.value = !expanded.value;
                }}
                class="absolute inset-0 z-0 cursor-pointer bg-transparent"
              />

              {/* Physical camera cutout stays fixed across morphs */}
              <span class="hero-camera camera-cutout" aria-hidden="true" />

              {/* MUSIC MODE */}
              {activeMode.value === 'music' && (
                <>
                  {/* Expanded BLENDED background + top camera swoop */}
                  {expanded.value && (
                    <>
                      <span
                        aria-hidden="true"
                        class="hero-music-bg pointer-events-none absolute inset-0"
                      />
                      <span
                        aria-hidden="true"
                        class="hero-camera-swoop pointer-events-none absolute inset-x-0 top-0"
                      />
                    </>
                  )}

                  {!expanded.value ? (
                    /* Compact Music Pill: BLENDED art left, cutout center, waveform + glow right */
                    <div class="pointer-events-none relative z-10 flex h-full w-full items-center justify-between">
                      <span class="hero-compact-art-blended" />
                      <span class="hero-compact-wave-wing">
                        <span
                          class={{
                            'hero-wave': true,
                            'hero-wave-paused': !isPlaying.value,
                          }}
                          aria-hidden="true"
                        >
                          {WAVE_BARS.map((bar, i) => (
                            <span
                              key={i}
                              style={{
                                height: bar.height + 'px',
                                animationDelay: bar.delay,
                              }}
                            />
                          ))}
                        </span>
                      </span>
                    </div>
                  ) : (
                    /* Expanded Android Media Controls layout */
                    <div class="pointer-events-none relative z-10 flex h-full flex-col justify-between px-[18px] pt-[10px] pb-[14px]">
                      {/* Top row: BPM chip aligned right of camera cutout */}
                      <div class="flex h-5 items-center justify-end">
                        <span class="rounded-full bg-white/10 px-2 py-0.5 text-[9.5px] font-semibold tracking-wide text-white/80">
                          {track.bpm} BPM
                        </span>
                      </div>

                      {/* Middle header row: Artwork | Track info + Audio chip | Wave + Play/Pause */}
                      <div class="flex items-center gap-3">
                        <div
                          class={{
                            'hero-expanded-art relative shrink-0 transition-all duration-300': true,
                            'scale-[0.86] opacity-55': !isPlaying.value,
                          }}
                        >
                          <span class="hero-expanded-art-fill block h-full w-full rounded-[13px]" />
                          <span
                            class="pointer-events-none absolute -inset-[2px] rounded-[15px] border-[1.5px]"
                            style={{
                              borderColor: `${track.accent}99`,
                            }}
                          />
                        </div>

                        <div class="min-w-0 flex-1">
                          <div class="truncate text-[16px] leading-tight font-bold text-white">
                            {track.title}
                          </div>
                          <div class="mt-0.5 truncate text-[12.5px] leading-tight text-white/75">
                            {track.artist}
                          </div>
                          <div class="mt-1.5 inline-flex max-w-full items-center gap-1 rounded-full bg-white/12 px-2 py-[2px] text-[10px] font-medium text-white/90">
                            <Headphones size={10} class="shrink-0" />
                            <span class="truncate">Pixel Buds Pro</span>
                          </div>
                        </div>

                        <div class="flex shrink-0 flex-col items-center gap-2">
                          <span
                            class={{
                              'hero-wave': true,
                              'hero-wave-paused': !isPlaying.value,
                            }}
                            aria-hidden="true"
                          >
                            {WAVE_BARS.map((bar, i) => (
                              <span
                                key={i}
                                style={{
                                  height: bar.height + 'px',
                                  animationDelay: bar.delay,
                                }}
                              />
                            ))}
                          </span>

                          <button
                            type="button"
                            aria-label={isPlaying.value ? 'Pause' : 'Play'}
                            onClick$={() => {
                              isPlaying.value = !isPlaying.value;
                            }}
                            class="pointer-events-auto flex h-[40px] w-[56px] cursor-pointer items-center justify-center rounded-[18px] bg-white/94 text-[#1b1a1e] shadow-sm transition-transform active:scale-95"
                          >
                            {isPlaying.value ? (
                              <Pause size={20} fill="currentColor" />
                            ) : (
                              <Play size={20} fill="currentColor" />
                            )}
                          </button>
                        </div>
                      </div>

                      {/* Bottom row: Prev | Material 3 Expressive Wavy Progress | Next */}
                      <div class="flex items-center gap-2 pt-1">
                        <button
                          type="button"
                          aria-label="Previous track"
                          onClick$={() => {
                            trackIndex.value =
                              (trackIndex.value + DEMO_TRACKS.length - 1) %
                              DEMO_TRACKS.length;
                          }}
                          class="pointer-events-auto flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-full text-white/90 transition-colors hover:bg-white/10 active:scale-90"
                        >
                          <SkipBack size={18} fill="currentColor" />
                        </button>

                        {/* Wavy progress indicator */}
                        <div class="relative flex h-6 flex-1 items-center">
                          <div class="h-[3px] w-full rounded-full bg-white/22" />
                          <div
                            class="absolute inset-y-0 left-0 flex items-center overflow-hidden"
                            style={{
                              width: `${Math.round(track.progress * 100)}%`,
                            }}
                          >
                            <svg
                              viewBox="0 0 120 12"
                              preserveAspectRatio="none"
                              class={{
                                'hero-wavy-svg h-3 w-[160px] shrink-0': true,
                                'hero-wavy-playing': isPlaying.value,
                              }}
                              aria-hidden="true"
                            >
                              {isPlaying.value ? (
                                <path
                                  d="M0 6 Q 5 1.5, 10 6 T 20 6 T 30 6 T 40 6 T 50 6 T 60 6 T 70 6 T 80 6 T 90 6 T 100 6 T 110 6 T 120 6 T 130 6 T 140 6"
                                  fill="none"
                                  stroke={track.accent}
                                  stroke-width="2.8"
                                  stroke-linecap="round"
                                />
                              ) : (
                                <line
                                  x1="0"
                                  y1="6"
                                  x2="120"
                                  y2="6"
                                  stroke={track.accent}
                                  stroke-width="2.8"
                                  stroke-linecap="round"
                                />
                              )}
                            </svg>
                          </div>
                          <span
                            class="absolute h-2.5 w-2.5 -translate-x-1/2 rounded-full shadow-sm"
                            style={{
                              left: `${Math.round(track.progress * 100)}%`,
                              backgroundColor: track.accent,
                            }}
                          />
                        </div>

                        <button
                          type="button"
                          aria-label="Next track"
                          onClick$={() => {
                            trackIndex.value =
                              (trackIndex.value + 1) % DEMO_TRACKS.length;
                          }}
                          class="pointer-events-auto flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-full text-white/90 transition-colors hover:bg-white/10 active:scale-90"
                        >
                          <SkipForward size={18} fill="currentColor" />
                        </button>
                      </div>
                    </div>
                  )}
                </>
              )}

              {/* LIVE ACTIVITY MODE */}
              {activeMode.value === 'live' && (
                <>
                  {!expanded.value ? (
                    <div class="pointer-events-none relative z-10 flex h-full w-full items-center justify-between px-2.5">
                      <span class="flex h-5 w-5 items-center justify-center text-[#4ade80]">
                        <Navigation size={15} fill="currentColor" />
                      </span>
                      <span class="text-[12.5px] font-semibold tracking-tight text-white">
                        {navEnded.value ? 'Ended' : '15m'}
                      </span>
                    </div>
                  ) : (
                    <div class="pointer-events-none relative z-10 flex h-full items-center gap-3 px-[16px] pt-[16px] pb-[12px]">
                      {/* Left icon spanning left of cutout */}
                      <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[#4ade80]/18 text-[#4ade80]">
                        <Navigation size={24} fill="currentColor" />
                      </div>

                      <div class="min-w-0 flex-1 pt-2">
                        <div class="truncate text-[15px] leading-snug font-bold text-white">
                          {navEnded.value
                            ? 'Navigation paused'
                            : 'Turn right on Main St'}
                        </div>
                        <div class="truncate text-[11.5px] text-white/70">
                          {navEnded.value
                            ? 'Google Maps · Tap Resume'
                            : '4.2 mi · 10:45 AM'}
                        </div>
                      </div>

                      {/* Trailing ETA + app-icon-color-tinted action button */}
                      <div class="flex shrink-0 flex-col items-end gap-1 pt-1">
                        <span class="text-[12px] font-semibold text-[#4ade80]">
                          {navEnded.value ? '—' : '15 min'}
                        </span>
                        <button
                          type="button"
                          onClick$={() => {
                            navEnded.value = !navEnded.value;
                          }}
                          class="pointer-events-auto cursor-pointer rounded-full bg-[#4ade80] px-3.5 py-1.5 text-[11.5px] font-bold text-[#071f10] transition-transform active:scale-95"
                        >
                          {navEnded.value ? 'Resume' : 'Exit'}
                        </button>
                      </div>
                    </div>
                  )}
                </>
              )}

              {/* FLASHLIGHT MODE */}
              {activeMode.value === 'flashlight' && (
                <>
                  {!expanded.value ? (
                    <div class="pointer-events-none relative z-10 flex h-full w-full items-center justify-between px-2">
                      <span class="hero-torch-compact-icon flex h-5 w-5 items-center justify-center text-white">
                        <Flashlight size={15} />
                      </span>
                      <span class="relative flex h-[22px] w-[22px] items-center justify-center">
                        <svg
                          width="22"
                          height="22"
                          viewBox="0 0 22 22"
                          class="-rotate-90"
                          aria-hidden="true"
                        >
                          <circle
                            cx="11"
                            cy="11"
                            r={ringRadius}
                            fill="none"
                            stroke="rgba(255,255,255,0.24)"
                            stroke-width="2"
                          />
                          <circle
                            cx="11"
                            cy="11"
                            r={ringRadius}
                            fill="none"
                            stroke="#ffffff"
                            stroke-width="2"
                            stroke-linecap="round"
                            stroke-dasharray={`${ringCircumference}`}
                            stroke-dashoffset={`${torchDashOffset}`}
                          />
                        </svg>
                        <span class="absolute text-[8.5px] font-bold text-white">
                          {torchOn.value ? torchBrightness.value : 0}
                        </span>
                      </span>
                    </div>
                  ) : (
                    /* Expanded Flashlight Beam layout (exact match to ExpandedFlashlightBeamLayout) */
                    <div class="pointer-events-none relative z-10 flex h-full flex-col items-center justify-end px-4 pt-8 pb-4">
                      {/* Interactive Beam & Handle Area */}
                      <div class="relative w-full flex-1">
                        {/* Soft blurred outer beam + inner core cone */}
                        <div
                          aria-hidden="true"
                          class="pointer-events-none absolute inset-x-0 bottom-1 transition-all duration-150"
                          style={{
                            top: `${handleTopPct}%`,
                            opacity: torchOn.value ? 1 : 0.08,
                          }}
                        >
                          <div
                            class="hero-beam-outer mx-auto h-full"
                            style={{ width: `${handleWidthPx + 18}px` }}
                          />
                          <div
                            class="hero-beam-core absolute inset-x-0 top-0 mx-auto h-full"
                            style={{ width: `${handleWidthPx}px` }}
                          />
                        </div>

                        {/* Horizontal pill handle at top of beam */}
                        <div
                          aria-hidden="true"
                          class="pointer-events-none absolute left-1/2 h-[6px] -translate-x-1/2 -translate-y-1/2 rounded-full transition-all duration-150"
                          style={{
                            top: `${handleTopPct}%`,
                            width: `${handleWidthPx}px`,
                            backgroundColor: torchOn.value
                              ? '#ffffff'
                              : 'rgba(255,255,255,0.35)',
                          }}
                        />

                        {/* Vertical accessible brightness slider overlaid on beam */}
                        <input
                          type="range"
                          min={10}
                          max={100}
                          step={5}
                          value={torchBrightness.value}
                          aria-label="Flashlight brightness"
                          onInput$={(e) => {
                            const val = Number(
                              (e.target as HTMLInputElement).value
                            );
                            torchBrightness.value = val;
                            if (!torchOn.value) torchOn.value = true;
                          }}
                          class="hero-beam-slider pointer-events-auto"
                        />
                      </div>

                      {/* Upright Flashlight Toggle Button + Radial Progress Ring */}
                      <button
                        type="button"
                        aria-label={
                          torchOn.value
                            ? 'Turn off flashlight'
                            : 'Turn on flashlight'
                        }
                        onClick$={() => {
                          torchOn.value = !torchOn.value;
                        }}
                        class="pointer-events-auto relative mt-1 flex h-[44px] w-[44px] cursor-pointer items-center justify-center rounded-full transition-transform active:scale-92"
                      >
                        <svg
                          width="42"
                          height="42"
                          viewBox="0 0 42 42"
                          class="absolute inset-0 m-auto -rotate-90"
                          aria-hidden="true"
                        >
                          <circle
                            cx="21"
                            cy="21"
                            r={expandedRingRadius}
                            fill="none"
                            stroke="rgba(255,255,255,0.22)"
                            stroke-width="3"
                          />
                          <circle
                            cx="21"
                            cy="21"
                            r={expandedRingRadius}
                            fill="none"
                            stroke="#ffffff"
                            stroke-width="3"
                            stroke-linecap="round"
                            stroke-dasharray={`${expandedRingCircumference}`}
                            stroke-dashoffset={`${expandedTorchDashOffset}`}
                          />
                        </svg>
                        <Flashlight
                          size={20}
                          class={torchOn.value ? 'text-white' : 'text-white/40'}
                        />
                      </button>
                    </div>
                  )}
                </>
              )}

              {/* CUSTOM ISLAND MODE */}
              {activeMode.value === 'custom' && (
                <>
                  {expanded.value && (
                    <>
                      <span
                        aria-hidden="true"
                        class="hero-custom-bg pointer-events-none absolute inset-0"
                      />
                      <span
                        aria-hidden="true"
                        class="hero-camera-swoop pointer-events-none absolute inset-x-0 top-0"
                      />
                    </>
                  )}
                  {!expanded.value ? (
                    <div class="pointer-events-none relative z-10 flex h-full w-full items-center justify-between px-2.5">
                      <span class="flex h-5 w-5 items-center justify-center text-[#8ea1ff]">
                        <Bell size={15} fill="currentColor" />
                      </span>
                      <span class="text-[12px] font-semibold text-white">
                        #general
                      </span>
                    </div>
                  ) : (
                    <div class="pointer-events-none relative z-10 flex h-full flex-col justify-between px-[16px] pt-[24px] pb-[14px]">
                      <div class="flex items-center gap-3">
                        <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[#7289da]/25 text-[#9eb0ff]">
                          <Bell size={22} fill="currentColor" />
                        </div>
                        <div class="min-w-0 flex-1">
                          <div class="truncate text-[14.5px] font-bold text-white">
                            Discord · #general
                          </div>
                          <div class="mt-0.5 truncate text-[12px] text-white/75">
                            {customAck.value
                              ? 'Marked as read'
                              : 'New custom island preset shared'}
                          </div>
                        </div>
                      </div>
                      <div class="flex items-center justify-end gap-2 pt-2">
                        <button
                          type="button"
                          onClick$={() => {
                            customAck.value = !customAck.value;
                          }}
                          class="pointer-events-auto cursor-pointer rounded-full bg-white/14 px-3 py-1.5 text-[11px] font-semibold text-white transition-colors hover:bg-white/20"
                        >
                          {customAck.value ? 'Undo' : 'Mark read'}
                        </button>
                        <button
                          type="button"
                          onClick$={() => {
                            customAck.value = true;
                          }}
                          class="pointer-events-auto cursor-pointer rounded-full bg-[#8ea1ff] px-3.5 py-1.5 text-[11px] font-bold text-[#0e1330] transition-transform active:scale-95"
                        >
                          Reply
                        </button>
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>

            {/* Subtle home screen hint widgets at the bottom of the phone stage */}
            <div
              aria-hidden="true"
              class="pointer-events-none absolute inset-x-6 bottom-4 flex items-center justify-between text-[11px] text-white/30"
            >
              <span>
                Tap island to {expanded.value ? 'minimize' : 'expand'}
              </span>
              <span>
                {activeMode.value === 'flashlight' && expanded.value
                  ? `${torchOn.value ? torchBrightness.value : 0}%`
                  : activeMode.value === 'music' && expanded.value
                    ? isPlaying.value
                      ? 'Playing'
                      : 'Paused'
                    : 'LarpDot'}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="flex flex-wrap justify-center gap-1.5">
        {modes.map((m) => (
          <button
            key={m.id}
            aria-pressed={activeMode.value === m.id}
            type="button"
            onClick$={() => {
              activeMode.value = m.id;
            }}
            class={{
              'lum-btn text-xs': true,
              'lum-bg-lum-accent text-white': activeMode.value === m.id,
              'lum-bg-transparent text-lum-text-secondary':
                activeMode.value !== m.id,
            }}
          >
            {m.label}
          </button>
        ))}
        <button
          type="button"
          onClick$={() => {
            expanded.value = !expanded.value;
          }}
          class="lum-btn lum-bg-transparent text-lum-text-secondary text-xs"
        >
          {expanded.value ? 'Minimize' : 'Expand'}
        </button>
      </div>
    </div>
  );
});

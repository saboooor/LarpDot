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
        class="mx-0.5 inline-block rounded bg-indigo-400/25 px-1 py-0.5 font-mono text-[0.9em] text-indigo-200"
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
          'flex items-center justify-center rounded-md bg-gradient-to-br from-indigo-500 via-sky-500 to-emerald-400 text-white shadow-inner': true,
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
          'flex items-center justify-center rounded-full bg-gradient-to-br from-violet-500 to-indigo-600 text-white': true,
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
          'flex items-center justify-center rounded-md bg-gradient-to-br from-slate-700 to-slate-800 text-slate-200 ring-1 ring-white/10': true,
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
              <span class="text-[11px] font-medium text-white/45">
                {isSwipeDownOnly ? 'Swipe down' : 'Dot'}
              </span>
            )}
          </div>
        ) : (
          <div class="island-shell relative w-full max-w-[300px] overflow-hidden rounded-[24px] p-3.5 transition-all duration-300">
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
                        class={`truncate rounded-xl bg-white/[0.04] px-3 py-2 text-xs ${alignClass}`}
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
                        class="flex items-center justify-center gap-3 rounded-xl bg-white/[0.04] py-2"
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
                        class="flex items-center gap-2.5 rounded-xl bg-white/[0.04] p-2"
                      >
                        <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 via-sky-500 to-emerald-400 text-white">
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
                        class="flex h-16 items-center justify-center gap-2 rounded-xl bg-gradient-to-br from-slate-800/90 to-slate-900/90 text-xs text-white/60 ring-1 ring-white/8"
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
                        class="flex items-center justify-center rounded-xl bg-white/[0.04] p-2.5"
                      >
                        <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-sky-500 to-indigo-500 text-white">
                          <LayoutGrid size={18} />
                        </div>
                      </div>
                    );
                  }

                  if (comp.kind === 'NOTIFICATION_ICON') {
                    return (
                      <div
                        key={idx}
                        class="flex items-center justify-center rounded-xl bg-white/[0.04] p-2.5 text-sky-300"
                      >
                        <Bell size={20} />
                      </div>
                    );
                  }

                  return (
                    <div
                      key={idx}
                      class="rounded-xl bg-white/[0.04] px-3 py-2 text-left"
                    >
                      <div class="flex items-center gap-1.5 text-[11px] font-semibold text-white/85">
                        <Bell size={11} class="text-indigo-300" />
                        Notification title
                      </div>
                      <div class="mt-1 text-[11px] text-white/50">
                        Live notification text & actions
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div class="rounded-xl bg-white/[0.04] px-3 py-2 text-center text-[11px] text-white/50">
                Expanded wing layout
              </div>
            )}
          </div>
        )}

        {mode === 'interactive' && hasExpanded && !isSwipeDownOnly && (
          <button
            type="button"
            class="text-lum-text-secondary rounded-full border border-white/10 bg-white/[0.04] px-2.5 py-0.5 text-[11px] font-medium transition-colors hover:border-white/20 hover:text-white"
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

export const HeroIslandDemo = component$(() => {
  const activeMode = useSignal<HeroMode>('music');
  const expanded = useSignal(true);

  const modes: Array<{ id: HeroMode; label: string }> = [
    { id: 'music', label: 'Music' },
    { id: 'live', label: 'Live Activity' },
    { id: 'flashlight', label: 'Flashlight' },
    { id: 'custom', label: 'Custom Island' },
  ];

  return (
    <div class="flex flex-col items-center gap-5">
      <div class="relative flex w-full max-w-[360px] flex-col items-center rounded-[36px] border border-white/10 bg-gradient-to-b from-[#151926] via-[#0f131d] to-[#0b0e15] px-5 pt-4 pb-6 shadow-2xl">
        <div class="mb-3 flex w-full items-center justify-between px-2 text-[11px] font-medium text-white/45">
          <span>9:41</span>
          <span class="tracking-wide uppercase">Android Overlay</span>
          <span>5G • 94%</span>
        </div>

        <button
          type="button"
          onClick$={() => {
            expanded.value = !expanded.value;
          }}
          class="w-full cursor-pointer text-left focus:outline-none"
          aria-label="Toggle Dynamic Island compact or expanded preview"
        >
          {!expanded.value ? (
            <div class="mx-auto flex justify-center">
              <div class="island-shell inline-flex h-10 items-center gap-3 rounded-full px-3.5 transition-all duration-300">
                {activeMode.value === 'music' && (
                  <>
                    <span class="flex h-5 w-5 items-center justify-center rounded-md bg-gradient-to-br from-indigo-500 via-sky-500 to-emerald-400 text-white">
                      <Disc3 size={12} />
                    </span>
                    <div class="camera-cutout h-4 w-4 rounded-full" />
                    <AudioWaveform size={14} class="text-indigo-300" />
                  </>
                )}
                {activeMode.value === 'live' && (
                  <>
                    <Navigation size={14} class="text-sky-400" />
                    <div class="camera-cutout h-4 w-4 rounded-full" />
                    <span class="font-mono text-xs font-semibold text-sky-200">
                      450m
                    </span>
                  </>
                )}
                {activeMode.value === 'flashlight' && (
                  <>
                    <Flashlight size={14} class="text-amber-300" />
                    <div class="camera-cutout h-4 w-4 rounded-full" />
                    <span class="font-mono text-xs font-semibold text-amber-200">
                      80%
                    </span>
                  </>
                )}
                {activeMode.value === 'custom' && (
                  <>
                    <Bell size={14} class="text-violet-300" />
                    <div class="camera-cutout h-4 w-4 rounded-full" />
                    <span class="text-xs font-semibold text-white/90">
                      Discord
                    </span>
                  </>
                )}
              </div>
            </div>
          ) : (
            <div class="island-shell mx-auto w-full max-w-[312px] rounded-[28px] p-4 transition-all duration-300">
              <div class="mb-3 flex items-center justify-between">
                <div class="flex items-center gap-2">
                  {activeMode.value === 'music' && (
                    <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 via-sky-500 to-emerald-400 text-white shadow-md">
                      <Disc3 size={20} />
                    </span>
                  )}
                  {activeMode.value === 'live' && (
                    <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-sky-500/20 text-sky-300 ring-1 ring-sky-400/25">
                      <Navigation size={18} />
                    </span>
                  )}
                  {activeMode.value === 'flashlight' && (
                    <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-400/18 text-amber-300 ring-1 ring-amber-300/25">
                      <Flashlight size={18} />
                    </span>
                  )}
                  {activeMode.value === 'custom' && (
                    <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-500/20 text-violet-300 ring-1 ring-violet-400/25">
                      <Bell size={18} />
                    </span>
                  )}

                  <div class="min-w-0">
                    <div class="truncate text-sm font-semibold text-white">
                      {activeMode.value === 'music' && 'Midnight City'}
                      {activeMode.value === 'live' && 'Turn right on King St'}
                      {activeMode.value === 'flashlight' && 'Torch Active'}
                      {activeMode.value === 'custom' && 'Discord • #general'}
                    </div>
                    <div class="truncate text-xs text-white/55">
                      {activeMode.value === 'music' && 'M83 • Visualizer on'}
                      {activeMode.value === 'live' && '450 m • ETA 6 min'}
                      {activeMode.value === 'flashlight' &&
                        'Slide & hold to adjust level'}
                      {activeMode.value === 'custom' &&
                        'Custom notification island'}
                    </div>
                  </div>
                </div>
                <div class="camera-cutout h-4 w-4 shrink-0 self-start rounded-full" />
              </div>

              {activeMode.value === 'music' && (
                <div class="space-y-3">
                  <div class="flex items-center gap-2">
                    <span class="font-mono text-[10px] text-white/45">
                      1:42
                    </span>
                    <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-white/12">
                      <div class="h-full w-3/5 rounded-full bg-gradient-to-r from-indigo-400 to-sky-400" />
                    </div>
                    <span class="font-mono text-[10px] text-white/45">
                      4:03
                    </span>
                  </div>
                  <div class="flex items-center justify-center gap-3">
                    <span class="flex h-8 w-8 items-center justify-center rounded-full bg-white/8 text-white/80">
                      <SkipBack size={14} />
                    </span>
                    <span class="flex h-9 w-12 items-center justify-center rounded-full bg-indigo-500 text-white">
                      <Pause size={15} />
                    </span>
                    <span class="flex h-8 w-8 items-center justify-center rounded-full bg-white/8 text-white/80">
                      <SkipForward size={14} />
                    </span>
                  </div>
                </div>
              )}

              {activeMode.value === 'live' && (
                <div class="space-y-2">
                  <div class="h-1.5 overflow-hidden rounded-full bg-white/12">
                    <div class="h-full w-2/3 rounded-full bg-sky-400" />
                  </div>
                  <div class="flex items-center justify-between text-[11px] text-white/55">
                    <span>Google Maps</span>
                    <span class="font-mono text-sky-300">2.4 km left</span>
                  </div>
                </div>
              )}

              {activeMode.value === 'flashlight' && (
                <div class="space-y-2.5">
                  <div class="h-2 overflow-hidden rounded-full bg-white/12">
                    <div class="h-full w-4/5 rounded-full bg-gradient-to-r from-amber-300 to-amber-500" />
                  </div>
                  <div class="flex items-center justify-between text-[11px] text-white/55">
                    <span>Brightness • 80%</span>
                    <span class="text-amber-200">Camera2 Boost ready</span>
                  </div>
                </div>
              )}

              {activeMode.value === 'custom' && (
                <div class="flex items-center justify-between rounded-2xl bg-white/[0.05] px-3 py-2 text-xs text-white/80">
                  <span class="truncate">New custom island preset shared</span>
                  <span class="ml-2 shrink-0 rounded-full bg-violet-500/25 px-2 py-0.5 text-[10px] font-semibold text-violet-200">
                    Open
                  </span>
                </div>
              )}
            </div>
          )}
        </button>

        <p class="mt-4 text-[11px] text-white/40">
          Tap the island to toggle compact & expanded
        </p>
      </div>

      <div class="flex flex-wrap justify-center gap-1.5">
        {modes.map((m) => (
          <button
            key={m.id}
            type="button"
            onClick$={() => {
              activeMode.value = m.id;
            }}
            class={{
              'lum-btn rounded-full px-3 py-1.5 text-xs font-medium transition-all': true,
              'lum-grad-bg-lum-accent text-white': activeMode.value === m.id,
              'bg-white/[0.04] text-lum-text-secondary hover:bg-white/[0.08] hover:text-white':
                activeMode.value !== m.id,
            }}
          >
            {m.label}
          </button>
        ))}
      </div>
    </div>
  );
});

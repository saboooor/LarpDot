import { component$, useContext, useSignal } from '@qwik.dev/core';
import { Link } from '@qwik.dev/router';
import Check from 'lucide-icons-qwik/icons/Check';
import Copy from 'lucide-icons-qwik/icons/Copy';
import Download from 'lucide-icons-qwik/icons/Download';
import Layers from 'lucide-icons-qwik/icons/Layers';
import Package from 'lucide-icons-qwik/icons/Package';
import Sparkles from 'lucide-icons-qwik/icons/Sparkles';
import type { CatalogPreset } from '~/util/db';
import { recordPresetCopy } from '~/util/presetActions';
import {
  formatAppDisplayName,
  formatShareJson,
  formatVisibilityLabel,
  getEnvelopeIslands,
} from '~/util/presets';
import { pushToast, ToastContext } from '~/util/toast';
import { IslandPreview } from './IslandPreview';

interface PresetCardProps {
  preset: CatalogPreset;
}

export const PresetCard = component$<PresetCardProps>(({ preset }) => {
  const toasts = useContext(ToastContext);
  const copied = useSignal(false);
  const copyCount = useSignal(preset.copies);

  const islands = getEnvelopeIslands(preset.payload);
  const primaryIsland = islands[0]?.island;
  const appLabel =
    formatAppDisplayName(preset.appPackage, preset.appName) ?? 'General';
  const triggerLabel = formatVisibilityLabel(
    preset.visibility,
    preset.condition
  );

  return (
    <article class="lum-card lum-grad-bg-lum-card-bg/85 flex flex-col justify-between gap-4 border border-white/8 p-5 transition-colors hover:border-white/15">
      <div class="flex flex-col gap-4">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0 flex-1">
            <Link
              href={`/presets/${preset.slug}`}
              class="block truncate text-base font-bold text-white transition-colors hover:text-indigo-300"
            >
              {preset.name}
            </Link>
            <p class="text-lum-text-secondary mt-0.5 text-xs">
              by <span class="text-white/80">{preset.author}</span>
            </p>
          </div>
          <span
            class={{
              'inline-flex shrink-0 items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-semibold': true,
              'bg-indigo-500/18 text-indigo-200 ring-1 ring-indigo-400/30':
                preset.isBundle,
              'bg-white/[0.06] text-white/75 ring-1 ring-white/10':
                !preset.isBundle,
            }}
          >
            {preset.isBundle ? (
              <>
                <Package size={12} />
                {preset.islandCount} islands
              </>
            ) : (
              <>
                <Sparkles size={12} />
                Single
              </>
            )}
          </span>
        </div>

        {primaryIsland && (
          <div class="flex min-h-24 items-center justify-center rounded-2xl border border-white/6 bg-[#0a0d13]/85 px-3 py-4">
            <IslandPreview island={primaryIsland} mode="compact" />
          </div>
        )}

        {preset.description && (
          <p class="text-lum-text-secondary line-clamp-2 text-sm leading-relaxed">
            {preset.description}
          </p>
        )}

        <div class="flex flex-wrap items-center gap-1.5 text-[11px]">
          <span class="inline-flex items-center gap-1 rounded-full bg-white/[0.06] px-2.5 py-1 font-medium text-white/80">
            <Layers size={11} class="text-indigo-300" />
            {appLabel}
          </span>
          <span class="text-lum-text-secondary rounded-full bg-white/[0.04] px-2.5 py-1">
            {triggerLabel}
          </span>
          <span class="text-lum-text-secondary ml-auto font-mono">
            {copyCount.value} {copyCount.value === 1 ? 'copy' : 'copies'}
          </span>
        </div>
      </div>

      <div class="flex items-center gap-2 border-t border-white/6 pt-3">
        <button
          type="button"
          class="lum-btn lum-grad-bg-lum-accent rounded-lum-1 flex-1 justify-center py-2 text-xs font-semibold text-white"
          onClick$={async () => {
            try {
              await navigator.clipboard.writeText(preset.shareCode);
              copied.value = true;
              pushToast(toasts, {
                title: 'Share code copied',
                description:
                  'Paste in LarpDot → Custom Islands → Presets → Import.',
                tone: 'success',
              });
              const updated = await recordPresetCopy(preset.id);
              if (typeof updated === 'number') copyCount.value = updated;
              window.setTimeout(() => {
                copied.value = false;
              }, 2000);
            } catch {
              pushToast(toasts, {
                title: 'Clipboard copy failed',
                description:
                  'Open the preset page to copy the share code manually.',
                tone: 'error',
              });
            }
          }}
        >
          {copied.value ? <Check size={14} /> : <Copy size={14} />}
          {copied.value ? 'Copied' : 'Copy Code'}
        </button>

        <button
          type="button"
          class="lum-btn rounded-lum-1 bg-white/[0.06] px-3 py-2 text-xs text-white/85 hover:bg-white/12"
          title="Download JSON preset file"
          onClick$={async () => {
            const json = formatShareJson(preset.payload);
            const blob = new Blob([json], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = `${preset.slug}.larpdot.json`;
            link.click();
            URL.revokeObjectURL(url);
            const updated = await recordPresetCopy(preset.id);
            if (typeof updated === 'number') copyCount.value = updated;
          }}
        >
          <Download size={14} />
        </button>

        <Link
          href={`/presets/${preset.slug}`}
          class="lum-btn rounded-lum-1 bg-white/[0.06] px-3 py-2 text-xs font-medium text-white/85 hover:bg-white/12"
        >
          Details
        </Link>
      </div>
    </article>
  );
});

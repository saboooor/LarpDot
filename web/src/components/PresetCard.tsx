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
    <article class="lum-card justify-between gap-4 p-5 transition-colors">
      <div class="flex flex-col gap-4">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0 flex-1">
            <Link
              href={`/presets/${preset.slug}`}
              class="block truncate text-base font-bold transition-colors hover:text-lum-accent"
            >
              {preset.name}
            </Link>
            <p class="text-lum-text-secondary mt-0.5 text-xs">
              by <span class="text-lum-text-secondary">{preset.author}</span>
            </p>
          </div>
          <span
            class={{
              'inline-flex shrink-0 items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold': true,
              'bg-lum-accent/15 text-lum-accent':
                preset.isBundle,
              'bg-lum-input-bg text-lum-text-secondary':
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
          <div class="flex min-h-24 items-center justify-center rounded-2xl bg-lum-card-bg px-3 py-4">
            <IslandPreview island={primaryIsland} mode="compact" />
          </div>
        )}

        {preset.description && (
          <p class="text-lum-text-secondary line-clamp-2 text-sm leading-relaxed">
            {preset.description}
          </p>
        )}

        <div class="flex flex-wrap items-center gap-1.5 text-xs">
          <span class="inline-flex items-center gap-1 rounded-full bg-lum-input-bg px-2.5 py-1 font-medium text-lum-text-secondary">
            <Layers size={11} class="text-lum-accent" />
            {appLabel}
          </span>
          <span class="text-lum-text-secondary rounded-full bg-lum-input-bg px-2.5 py-1">
            {triggerLabel}
          </span>
          <span class="text-lum-text-secondary ml-auto font-mono">
            {copyCount.value} {copyCount.value === 1 ? 'copy' : 'copies'}
          </span>
        </div>
      </div>

      <div class="flex items-center gap-2 pt-3">
        <button
          type="button"
          class="lum-btn lum-bg-lum-accent flex-1 justify-center py-2 text-xs"
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
          class="lum-btn px-3 py-2 text-xs text-lum-text-secondary"
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
          class="lum-btn px-3 py-2 text-xs font-medium text-lum-text-secondary"
        >
          Details
        </Link>
      </div>
    </article>
  );
});

import { component$, useContext, useSignal } from '@qwik.dev/core';
import {
  Link,
  routeLoader$,
  type DocumentHead,
} from '@qwik.dev/router';
import { eq } from 'drizzle-orm';
import Check from 'lucide-icons-qwik/icons/Check';
import ChevronLeft from 'lucide-icons-qwik/icons/ChevronLeft';
import Copy from 'lucide-icons-qwik/icons/Copy';
import Download from 'lucide-icons-qwik/icons/Download';
import Layers from 'lucide-icons-qwik/icons/Layers';
import Link2 from 'lucide-icons-qwik/icons/Link2';
import Package from 'lucide-icons-qwik/icons/Package';
import Sparkles from 'lucide-icons-qwik/icons/Sparkles';
import { IslandPreview } from '~/components/IslandPreview';
import { getDB, presets } from '~/util/db';
import { recordPresetCopy } from '~/util/presetActions';
import {
  formatAppDisplayName,
  formatComponentKindLabel,
  formatIconLabel,
  formatImageLabel,
  formatShareJson,
  formatTapActionLabel,
  formatVisibilityLabel,
  getEnvelopeIslands,
  slotHasContent,
  type CustomIslandSlot,
} from '~/util/presets';
import { pushToast, ToastContext } from '~/util/toast';

export const usePresetDetail = routeLoader$(async ({ params, status }) => {
  const slug = params.slug?.trim();
  if (!slug) {
    status(404);
    return null;
  }

  try {
    const db = getDB();
    const found = await db
      .select()
      .from(presets)
      .where(eq(presets.slug, slug))
      .get();
    if (!found) {
      status(404);
      return null;
    }
    return found;
  } catch {
    status(500);
    return null;
  }
});

function describeSlot(slot: CustomIslandSlot): string {
  if (slot.text?.trim()) return `Text: "${slot.text.trim()}"`;
  if (slot.image) return formatImageLabel(slot.image);
  if (slot.icon) return `Icon: ${formatIconLabel(slot.icon)}`;
  return 'Empty';
}

export default component$(() => {
  const preset = usePresetDetail().value;
  const toasts = useContext(ToastContext);
  const copiedCode = useSignal(false);
  const copyCount = useSignal(preset?.copies ?? 0);

  if (!preset) {
    return (
      <section class="mx-auto flex max-w-2xl flex-col items-center justify-center gap-4 px-4 py-24 text-center">
        <h1 class="text-2xl font-bold text-white">Preset not found</h1>
        <p class="text-lum-text-secondary text-sm">
          This preset may have been removed or the link may be incorrect.
        </p>
        <Link
          href="/presets"
          class="lum-btn lum-grad-bg-lum-accent rounded-lum-1 text-sm font-semibold text-white"
        >
          Back to Preset Catalog
        </Link>
      </section>
    );
  }

  const islands = getEnvelopeIslands(preset.payload);
  const appLabel =
    formatAppDisplayName(preset.appPackage, preset.appName) ?? 'General';
  const createdLabel = new Date(preset.createdAt).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });

  return (
    <section class="mx-auto max-w-5xl px-4 pt-10 pb-16 sm:px-6">
      <Link
        href="/presets"
        class="lum-btn lum-bg-transparent text-lum-text-secondary -ml-2 mb-4 text-xs hover:text-white"
      >
        <ChevronLeft size={16} />
        Back to Preset Catalog
      </Link>

      {/* Header Card */}
      <div class="lum-card lum-grad-bg-lum-card-bg/85 mb-6 border border-white/8 p-6">
        <div class="flex flex-col justify-between gap-4 md:flex-row md:items-start">
          <div class="min-w-0 flex-1">
            <div class="mb-2 flex flex-wrap items-center gap-2">
              <span
                class={{
                  'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold': true,
                  'bg-indigo-500/20 text-indigo-200 ring-1 ring-indigo-400/30':
                    preset.isBundle,
                  'bg-white/[0.06] text-white/80 ring-1 ring-white/10':
                    !preset.isBundle,
                }}
              >
                {preset.isBundle ? (
                  <>
                    <Package size={13} />
                    App Bundle • {preset.islandCount} islands
                  </>
                ) : (
                  <>
                    <Sparkles size={13} />
                    Custom Island Preset
                  </>
                )}
              </span>

              <span class="inline-flex items-center gap-1 rounded-full bg-white/[0.06] px-2.5 py-0.5 text-xs text-white/80">
                <Layers size={12} class="text-indigo-300" />
                {appLabel}
              </span>

              {preset.appPackage && (
                <code class="text-lum-text-secondary text-xs">
                  {preset.appPackage}
                </code>
              )}
            </div>

            <h1 class="text-2xl font-extrabold tracking-tight text-white sm:text-3xl">
              {preset.name}
            </h1>

            <p class="text-lum-text-secondary mt-1 text-xs">
              Shared by <strong class="text-white/90">{preset.author}</strong> •{' '}
              {createdLabel} •{' '}
              <span class="font-mono">
                {copyCount.value} {copyCount.value === 1 ? 'copy' : 'copies'}
              </span>
            </p>

            {preset.description && (
              <p class="mt-3 max-w-2xl text-sm leading-relaxed text-white/85">
                {preset.description}
              </p>
            )}
          </div>

          <div class="flex flex-wrap items-center gap-2">
            <button
              type="button"
              class="lum-btn lum-grad-bg-lum-accent rounded-lum-1 px-4 py-2.5 text-sm font-semibold text-white"
              onClick$={async () => {
                try {
                  await navigator.clipboard.writeText(preset.shareCode);
                  copiedCode.value = true;
                  pushToast(toasts, {
                    title: 'Share code copied',
                    description:
                      'Open LarpDot → Custom Islands → Presets → Import.',
                    tone: 'success',
                  });
                  const updated = await recordPresetCopy(preset.id);
                  if (typeof updated === 'number') copyCount.value = updated;
                  window.setTimeout(() => {
                    copiedCode.value = false;
                  }, 2200);
                } catch {
                  pushToast(toasts, {
                    title: 'Copy failed',
                    description: 'Select the share code box below to copy.',
                    tone: 'error',
                  });
                }
              }}
            >
              {copiedCode.value ? <Check size={16} /> : <Copy size={16} />}
              {copiedCode.value ? 'Copied Code' : 'Copy Share Code'}
            </button>

            <button
              type="button"
              class="lum-btn rounded-lum-1 bg-white/[0.06] px-4 py-2.5 text-sm text-white hover:bg-white/12"
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
              <Download size={16} />
              JSON
            </button>

            <button
              type="button"
              class="lum-btn rounded-lum-1 bg-white/[0.06] px-3 py-2.5 text-sm text-white hover:bg-white/12"
              title="Copy preset page URL"
              onClick$={async () => {
                await navigator.clipboard.writeText(window.location.href);
                pushToast(toasts, {
                  title: 'Link copied',
                  tone: 'accent',
                });
              }}
            >
              <Link2 size={16} />
            </button>
          </div>
        </div>
      </div>

      {/* Islands in preset / bundle */}
      <div class="mb-6 flex flex-col gap-4">
        <h2 class="text-sm font-semibold tracking-wide text-white/80 uppercase">
          {islands.length === 1
            ? 'Island Configuration'
            : `Included Islands (${islands.length})`}
        </h2>

        {islands.map((item, idx) => {
          const isl = item.island;
          return (
            <div
              key={idx}
              class="lum-card lum-grad-bg-lum-card-bg/85 grid gap-6 border border-white/8 p-5 md:grid-cols-12"
            >
              <div class="flex flex-col items-center justify-center rounded-2xl border border-white/6 bg-[#0a0d13]/90 p-5 md:col-span-5">
                <IslandPreview island={isl} mode="interactive" />
              </div>

              <div class="flex flex-col gap-3 md:col-span-7">
                <div>
                  <h3 class="text-lg font-bold text-white">{item.name}</h3>
                  {item.description && (
                    <p class="text-lum-text-secondary mt-0.5 text-sm">
                      {item.description}
                    </p>
                  )}
                </div>

                <dl class="grid grid-cols-2 gap-3 text-xs sm:grid-cols-3">
                  <div class="rounded-xl bg-white/[0.04] p-2.5">
                    <dt class="text-lum-text-secondary">Trigger</dt>
                    <dd class="mt-0.5 font-semibold text-white">
                      {formatVisibilityLabel(isl.visibility, isl.condition)}
                    </dd>
                  </div>
                  <div class="rounded-xl bg-white/[0.04] p-2.5">
                    <dt class="text-lum-text-secondary">Tap action</dt>
                    <dd class="mt-0.5 font-semibold text-white">
                      {formatTapActionLabel(isl.tapAction)}
                    </dd>
                  </div>
                  <div class="rounded-xl bg-white/[0.04] p-2.5">
                    <dt class="text-lum-text-secondary">Expanded size</dt>
                    <dd class="mt-0.5 font-mono font-semibold text-white">
                      {isl.MAIN.width}×{isl.MAIN.height} dp
                    </dd>
                  </div>
                </dl>

                <div class="grid gap-2 text-xs sm:grid-cols-2">
                  <div class="rounded-xl border border-white/6 bg-white/[0.02] p-3">
                    <div class="text-lum-text-secondary mb-1.5 font-semibold">
                      Compact Wings
                    </div>
                    <ul class="space-y-1 text-white/85">
                      <li>
                        <span class="text-lum-text-secondary">Left: </span>
                        {describeSlot(isl.LEFT)}
                      </li>
                      <li>
                        <span class="text-lum-text-secondary">Right: </span>
                        {describeSlot(isl.RIGHT)}
                      </li>
                    </ul>
                  </div>

                  <div class="rounded-xl border border-white/6 bg-white/[0.02] p-3">
                    <div class="text-lum-text-secondary mb-1.5 font-semibold">
                      Expanded Layout
                    </div>
                    <ul class="space-y-1 text-white/85">
                      {slotHasContent(isl.EXPANDED_LEFT) && (
                        <li>
                          <span class="text-lum-text-secondary">
                            Left wing:{' '}
                          </span>
                          {describeSlot(isl.EXPANDED_LEFT)}
                        </li>
                      )}
                      {slotHasContent(isl.EXPANDED_RIGHT) && (
                        <li>
                          <span class="text-lum-text-secondary">
                            Right wing:{' '}
                          </span>
                          {describeSlot(isl.EXPANDED_RIGHT)}
                        </li>
                      )}
                      <li>
                        <span class="text-lum-text-secondary">
                          Components:{' '}
                        </span>
                        {isl.components.length > 0
                          ? isl.components
                              .map((c) => formatComponentKindLabel(c.kind))
                              .join(', ')
                          : 'None'}
                      </li>
                    </ul>
                  </div>
                </div>

                {(isl.notificationRule.package ||
                  isl.notificationRule.title ||
                  isl.notificationRule.text ||
                  isl.notificationRule.channel ||
                  (isl.notificationRule.conditions &&
                    isl.notificationRule.conditions.length > 0)) && (
                  <div class="rounded-xl border border-white/6 bg-white/[0.02] p-3 text-xs">
                    <div class="text-lum-text-secondary mb-1 font-semibold">
                      Notification Rule
                    </div>
                    <div class="flex flex-wrap gap-x-4 gap-y-1 font-mono text-[11px] text-white/80">
                      {isl.notificationRule.package && (
                        <span>pkg: {isl.notificationRule.package}</span>
                      )}
                      {isl.notificationRule.channel && (
                        <span>channel: {isl.notificationRule.channel}</span>
                      )}
                      {isl.notificationRule.conditions &&
                      isl.notificationRule.conditions.length > 0 ? (
                        <span class="w-full">
                          {isl.notificationRule.conditions
                            .filter((c) => c.pattern.length > 0)
                            .map((c, cIdx) => {
                              const prefix = cIdx > 0 ? ` ${c.join} ` : '';
                              const field =
                                c.field === 'TITLE'
                                  ? 'title'
                                  : c.field === 'TEXT'
                                    ? 'text'
                                    : 'channel';
                              const op =
                                c.operator === 'NOT_CONTAINS'
                                  ? '!~'
                                  : '~';
                              return `${prefix}${field} ${op} "${c.pattern}"`;
                            })
                            .join('')}
                        </span>
                      ) : (
                        <>
                          {isl.notificationRule.title && (
                            <span>title: "{isl.notificationRule.title}"</span>
                          )}
                          {isl.notificationRule.text && (
                            <span>text: "{isl.notificationRule.text}"</span>
                          )}
                        </>
                      )}
                    </div>
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Share Code & Import Instructions */}
      <div class="lum-card lum-grad-bg-lum-card-bg/85 grid gap-5 border border-white/8 p-5 md:grid-cols-12">
        <div class="md:col-span-7">
          <div class="mb-2 text-xs font-semibold tracking-wide text-white/80 uppercase">
            LarpDot Share Code
          </div>
          <textarea
            readOnly
            rows={4}
            value={preset.shareCode}
            onClick$={(_, el) => {
              el.select();
            }}
            class="lum-input rounded-lum-1 w-full font-mono text-xs text-indigo-200"
          />
        </div>
        <div class="md:col-span-5">
          <div class="mb-2 text-xs font-semibold tracking-wide text-white/80 uppercase">
            How to import in LarpDot
          </div>
          <ol class="text-lum-text-secondary list-decimal space-y-1.5 pl-4 text-xs leading-relaxed">
            <li>
              Tap <strong class="text-white">Copy Share Code</strong> (or
              download the JSON file).
            </li>
            <li>
              Open <strong class="text-white">LarpDot</strong> on Android and
              go to <strong class="text-white">Custom Islands</strong>.
            </li>
            <li>
              Open <strong class="text-white">Presets → Import</strong>, paste
              the <code class="text-indigo-300">LARPDOT1:</code> code, and
              apply it.
            </li>
          </ol>
        </div>
      </div>
    </section>
  );
});

export const head: DocumentHead = ({ resolveValue }) => {
  const preset = resolveValue(usePresetDetail);
  if (!preset) {
    return { title: 'Preset Not Found — LarpDot' };
  }
  return {
    title: `${preset.name} — LarpDot Preset Catalog`,
    meta: [
      {
        name: 'description',
        content:
          preset.description ||
          `${preset.name} custom island ${preset.isBundle ? 'bundle' : 'preset'} for LarpDot by ${preset.author}.`,
      },
    ],
  };
};

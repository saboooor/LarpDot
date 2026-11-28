import { $, component$, useContext, useSignal } from '@qwik.dev/core';
import { Link, type DocumentHead } from '@qwik.dev/router';
import AlertCircle from 'lucide-icons-qwik/icons/AlertCircle';
import CheckCircle2 from 'lucide-icons-qwik/icons/CheckCircle2';
import ChevronLeft from 'lucide-icons-qwik/icons/ChevronLeft';
import Copy from 'lucide-icons-qwik/icons/Copy';
import FileUp from 'lucide-icons-qwik/icons/FileUp';
import Package from 'lucide-icons-qwik/icons/Package';
import SearchCode from 'lucide-icons-qwik/icons/SearchCode';
import Sparkles from 'lucide-icons-qwik/icons/Sparkles';
import Upload from 'lucide-icons-qwik/icons/Upload';
import { IslandPreview } from '~/components/IslandPreview';
import {
  decodeShareInput,
  encodeShareCode,
  formatShareJson,
  formatTapActionLabel,
  formatVisibilityLabel,
  type DecodedLarpDotShare,
} from '~/util/presets';
import { pushToast, ToastContext } from '~/util/toast';

export default component$(() => {
  const toasts = useContext(ToastContext);
  const rawInput = useSignal('');
  const decoded = useSignal<DecodedLarpDotShare | null>(null);
  const canonicalCode = useSignal('');
  const parseError = useSignal<string | null>(null);

  const inspect = $(async (raw: string) => {
    rawInput.value = raw;
    if (!raw.trim()) {
      decoded.value = null;
      canonicalCode.value = '';
      parseError.value = null;
      return;
    }

    const result = await decodeShareInput(raw);
    if (!result) {
      decoded.value = null;
      canonicalCode.value = '';
      parseError.value =
        'Not a valid LARPDOT1: share code or LarpDot preset JSON file.';
      return;
    }

    decoded.value = result;
    parseError.value = null;
    canonicalCode.value = await encodeShareCode(result.envelope);
  });

  const submitUrl =
    canonicalCode.value && canonicalCode.value.length <= 6000
      ? `/presets/submit?code=${encodeURIComponent(canonicalCode.value)}`
      : '/presets/submit';

  return (
    <section class="mx-auto max-w-5xl px-4 pt-10 pb-16 sm:px-6">
      <div class="mb-6">
        <Link
          href="/presets"
          class="lum-btn lum-bg-transparent text-lum-text-secondary -ml-2 mb-3 text-xs hover:text-lum-text"
        >
          <ChevronLeft size={16} />
          Back to Preset Catalog
        </Link>

        <h1 class="flex items-center gap-2.5 text-2xl font-extrabold tracking-tight sm:text-3xl">
          <SearchCode size={26} class="text-lum-accent" />
          Share Code Inspector
        </h1>
        <p class="text-lum-text-secondary mt-1.5 text-sm">
          Decode and preview any <code class="text-lum-accent">LARPDOT1:</code>{' '}
          share code or exported <code class="text-lum-accent">.json</code>{' '}
          locally in your browser before importing or publishing.
        </p>
      </div>

      <div class="lum-card mb-6 p-5">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <label for="inspect-input" class="text-sm font-semibold">
            Paste share code or preset JSON
          </label>
          <label class="lum-btn cursor-pointer px-3 py-1.5 text-xs font-medium text-lum-text-secondary">
            <FileUp size={14} />
            Open .json
            <input
              type="file"
              accept=".json,application/json,text/plain"
              class="hidden"
              onChange$={async (_, el) => {
                const file = el.files?.[0];
                if (!file) return;
                await inspect(await file.text());
              }}
            />
          </label>
        </div>

        <textarea
          id="inspect-input"
          rows={5}
          placeholder="LARPDOT1:H4sIAAAAAAAA..."
          class="lum-input w-full font-mono text-xs leading-relaxed"
          value={rawInput.value}
          onInput$={(_, el) => void inspect(el.value)}
        />

        {parseError.value && (
          <p class="mt-2.5 flex items-center gap-1.5 text-xs text-rose-300">
            <AlertCircle size={14} />
            {parseError.value}
          </p>
        )}
      </div>

      {decoded.value && (
        <div class="flex flex-col gap-6">
          <div class="lum-card justify-between gap-4 p-5 sm:flex-row sm:items-center">
            <div>
              <div class="mb-1.5 flex flex-wrap items-center gap-2">
                <span class="inline-flex items-center gap-1 text-xs font-semibold text-emerald-300">
                  <CheckCircle2 size={15} />
                  Valid {decoded.value.isBundle ? 'App Bundle' : 'Preset'} (
                  {decoded.value.islands.length}{' '}
                  {decoded.value.islands.length === 1 ? 'island' : 'islands'})
                </span>
                <span class="inline-flex items-center gap-1 rounded-full bg-lum-input-bg px-2.5 py-0.5 text-xs text-lum-text-secondary">
                  {decoded.value.isBundle ? (
                    <Package size={12} />
                  ) : (
                    <Sparkles size={12} />
                  )}
                  {decoded.value.appName ||
                    decoded.value.appPackage ||
                    'General'}
                </span>
              </div>
              <h2 class="text-xl font-bold">{decoded.value.name}</h2>
              {decoded.value.description && (
                <p class="text-lum-text-secondary mt-1 text-sm">
                  {decoded.value.description}
                </p>
              )}
            </div>

            <div class="flex flex-wrap items-center gap-2">
              {canonicalCode.value && (
                <button
                  type="button"
                  class="lum-btn px-3.5 py-2 text-xs font-medium"
                  onClick$={async () => {
                    await navigator.clipboard.writeText(canonicalCode.value);
                    pushToast(toasts, {
                      title: 'Canonical share code copied',
                      tone: 'success',
                    });
                  }}
                >
                  <Copy size={14} />
                  Copy Code
                </button>
              )}
              <Link
                href={submitUrl}
                class="lum-btn lum-bg-lum-accent px-4 py-2 text-xs"
              >
                <Upload size={14} />
                Publish to Catalog
              </Link>
            </div>
          </div>

          <div class="grid gap-4 md:grid-cols-2">
            {decoded.value.islands.map((item, idx) => (
              <div
                key={idx}
                class="lum-card gap-4 p-5"
              >
                <div class="flex items-start justify-between gap-2">
                  <div>
                    <h3 class="text-base font-bold">{item.name}</h3>
                    {item.description && (
                      <p class="text-lum-text-secondary text-xs">
                        {item.description}
                      </p>
                    )}
                  </div>
                  <span class="rounded-full bg-lum-input-bg px-2.5 py-0.5 text-xs font-medium text-lum-accent">
                    {formatVisibilityLabel(
                      item.island.visibility,
                      item.island.condition
                    )}
                  </span>
                </div>

                <div class="flex items-center justify-center rounded-2xl bg-lum-card-bg p-5">
                  <IslandPreview island={item.island} mode="interactive" />
                </div>

                <div class="flex flex-wrap gap-2 text-xs text-lum-text-secondary">
                  <span class="rounded-lg bg-lum-input-bg px-2.5 py-1">
                    Tap: {formatTapActionLabel(item.island.tapAction)}
                  </span>
                  <span class="rounded-lg bg-lum-input-bg px-2.5 py-1 font-mono">
                    {item.island.MAIN.width}×{item.island.MAIN.height} dp
                  </span>
                  {item.island.notificationRule.package && (
                    <span class="rounded-lg bg-lum-input-bg px-2.5 py-1 font-mono">
                      {item.island.notificationRule.package}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>

          <details class="lum-card p-5">
            <summary class="cursor-pointer text-xs font-semibold tracking-wide text-lum-text-secondary uppercase">
              Decoded Canonical JSON
            </summary>
            <pre class="mt-3 max-h-96 overflow-auto rounded-xl bg-black/50 p-3.5 font-mono text-xs leading-relaxed text-lum-accent/90">
              {formatShareJson(decoded.value.envelope)}
            </pre>
          </details>
        </div>
      )}
    </section>
  );
});

export const head: DocumentHead = {
  title: 'Share Code Inspector — LarpDot',
  meta: [
    {
      name: 'description',
      content:
        'Decode, preview, and inspect any LarpDot LARPDOT1: share code or preset JSON locally in your browser.',
    },
  ],
};

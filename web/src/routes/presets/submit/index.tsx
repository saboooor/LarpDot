import { $, component$, useSignal } from '@qwik.dev/core';
import {
  Form,
  Link,
  routeAction$,
  routeLoader$,
  type DocumentHead,
} from '@qwik.dev/router';
import AlertCircle from 'lucide-icons-qwik/icons/AlertCircle';
import CheckCircle2 from 'lucide-icons-qwik/icons/CheckCircle2';
import ChevronLeft from 'lucide-icons-qwik/icons/ChevronLeft';
import FileUp from 'lucide-icons-qwik/icons/FileUp';
import Package from 'lucide-icons-qwik/icons/Package';
import Sparkles from 'lucide-icons-qwik/icons/Sparkles';
import Upload from 'lucide-icons-qwik/icons/Upload';
import { IslandPreview } from '~/components/IslandPreview';
import { getDB, presets } from '~/util/db';
import {
  applyMetadataToEnvelope,
  createPresetSlug,
  decodeShareInput,
  encodeShareCode,
  formatAppDisplayName,
  formatVisibilityLabel,
  summarizeEnvelopeTrigger,
  type DecodedLarpDotShare,
} from '~/util/presets';

function readFormField(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

export const useSubmitPreset = routeAction$(
  async (formData, { fail, redirect }) => {
    const shareInput = readFormField(formData.shareInput);
    const authorInput = readFormField(formData.author);
    const nameInput = readFormField(formData.name);
    const descriptionInput = readFormField(formData.description);
    const appNameInput = readFormField(formData.appName);

    if (!shareInput) {
      return fail(400, {
        error: 'Paste a LARPDOT1: share code or upload a LarpDot JSON file.',
      });
    }

    const decoded = await decodeShareInput(shareInput);
    if (!decoded) {
      return fail(400, {
        error:
          'Could not decode that preset. Make sure it is a valid LARPDOT1: share code or exported LarpDot JSON file.',
      });
    }

    const author = (authorInput || 'Community').slice(0, 40);
    const finalName = (nameInput || decoded.name).slice(0, 48);
    const finalDescription = descriptionInput.slice(0, 160);
    const resolvedAppName =
      formatAppDisplayName(
        decoded.appPackage,
        appNameInput || decoded.appName
      ) ?? null;

    const normalizedEnvelope = applyMetadataToEnvelope(decoded, {
      name: finalName,
      description: finalDescription,
      appName: resolvedAppName,
    });

    const canonicalShareCode = await encodeShareCode(normalizedEnvelope);
    const trigger = summarizeEnvelopeTrigger(normalizedEnvelope);
    const slug = createPresetSlug(finalName);

    try {
      const db = getDB();
      await db.insert(presets).values({
        slug,
        name: finalName,
        author,
        description: finalDescription,
        isBundle: decoded.isBundle,
        appPackage: decoded.appPackage,
        appName: resolvedAppName,
        islandCount: decoded.islands.length,
        visibility: trigger.visibility,
        condition: trigger.condition,
        payload: normalizedEnvelope,
        shareCode: canonicalShareCode,
      });
    } catch (err) {
      return fail(500, {
        error:
          err instanceof Error
            ? `Failed to save preset: ${err.message}`
            : 'Failed to save preset to the catalog.',
      });
    }

    throw redirect(303, `/presets/${slug}`);
  }
);

export const usePrefilledPreset = routeLoader$(async ({ query }) => {
  const rawCode = (query.get('code') ?? '').trim();
  if (!rawCode) {
    return {
      rawCode: '',
      decoded: null as DecodedLarpDotShare | null,
      error: null as string | null,
    };
  }
  const decoded = await decodeShareInput(rawCode);
  return {
    rawCode,
    decoded,
    error: decoded
      ? null
      : 'Not a recognized LARPDOT1: share code or LarpDot preset JSON.',
  };
});

export default component$(() => {
  const action = useSubmitPreset();
  const prefilled = usePrefilledPreset().value;
  const shareInput = useSignal(prefilled.rawCode);
  const decoded = useSignal<DecodedLarpDotShare | null>(prefilled.decoded);
  const parseError = useSignal<string | null>(prefilled.error);

  const name = useSignal(prefilled.decoded?.name ?? '');
  const description = useSignal(prefilled.decoded?.description ?? '');
  const author = useSignal('');
  const appName = useSignal(prefilled.decoded?.appName ?? '');

  const handleDecode = $(async (raw: string) => {
    shareInput.value = raw;
    if (!raw.trim()) {
      decoded.value = null;
      parseError.value = null;
      return;
    }

    const result = await decodeShareInput(raw);
    if (!result) {
      decoded.value = null;
      parseError.value =
        'Not a recognized LARPDOT1: share code or LarpDot preset JSON.';
      return;
    }

    parseError.value = null;
    decoded.value = result;
    name.value = result.name;
    description.value = result.description;
    appName.value = result.appName ?? '';
  });

  return (
    <section class="mx-auto max-w-4xl px-4 pt-10 pb-16 sm:px-6">
      <div class="mb-6">
        <Link
          href="/presets"
          class="lum-btn lum-bg-transparent text-lum-text-secondary -ml-2 mb-3 text-xs hover:text-white"
        >
          <ChevronLeft size={16} />
          Back to Preset Catalog
        </Link>

        <h1 class="flex items-center gap-2.5 text-2xl font-extrabold tracking-tight text-white sm:text-3xl">
          <Upload size={26} class="text-indigo-300" />
          Submit a Custom Island Preset
        </h1>
        <p class="text-lum-text-secondary mt-1.5 text-sm">
          Export any Custom Island or App Bundle from LarpDot as a{' '}
          <code class="text-indigo-300">LARPDOT1:</code> share code or{' '}
          <code class="text-indigo-300">.json</code> file and paste it below.
        </p>
      </div>

      {action.value?.failed && (
        <div class="lum-card mb-6 flex items-start gap-2.5 border border-rose-400/30 bg-rose-950/45 p-4 text-sm text-rose-100">
          <AlertCircle size={18} class="mt-0.5 shrink-0 text-rose-300" />
          <span>{action.value.error}</span>
        </div>
      )}

      <Form action={action} class="flex flex-col gap-6">
        {/* Step 1: Share code or JSON */}
        <div class="lum-card lum-grad-bg-lum-card-bg/85 border border-white/8 p-5">
          <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
            <label
              for="shareInput"
              class="text-sm font-semibold text-white"
            >
              1. Paste share code or JSON
            </label>
            <label class="lum-btn rounded-lum-1 cursor-pointer bg-white/[0.06] px-3 py-1.5 text-xs font-medium text-white/85 hover:bg-white/12">
              <FileUp size={14} />
              Load .json file
              <input
                type="file"
                accept=".json,application/json,text/plain"
                class="hidden"
                onChange$={async (_, el) => {
                  const file = el.files?.[0];
                  if (!file) return;
                  const text = await file.text();
                  await handleDecode(text);
                }}
              />
            </label>
          </div>

          <textarea
            id="shareInput"
            name="shareInput"
            rows={5}
            required
            placeholder="LARPDOT1:H4sIAAAAAAAA..."
            class="lum-input rounded-lum-1 w-full font-mono text-xs leading-relaxed"
            value={shareInput.value}
            onInput$={(_, el) => void handleDecode(el.value)}
          />

          {parseError.value && (
            <p class="mt-2.5 flex items-center gap-1.5 text-xs text-rose-300">
              <AlertCircle size={14} />
              {parseError.value}
            </p>
          )}

          {decoded.value && (
            <div class="mt-4 rounded-2xl border border-emerald-400/20 bg-emerald-950/20 p-4">
              <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
                <span class="flex items-center gap-1.5 text-xs font-semibold text-emerald-300">
                  <CheckCircle2 size={15} />
                  Valid {decoded.value.isBundle ? 'App Bundle' : 'Preset'} (
                  {decoded.value.islands.length}{' '}
                  {decoded.value.islands.length === 1 ? 'island' : 'islands'})
                </span>
                <span class="inline-flex items-center gap-1 rounded-full bg-white/[0.08] px-2.5 py-0.5 text-[11px] text-white/80">
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

              <div class="grid gap-3 sm:grid-cols-2">
                {decoded.value.islands.map((item, idx) => (
                  <div
                    key={idx}
                    class="flex flex-col items-center gap-2 rounded-xl border border-white/8 bg-[#0a0d13]/90 p-3"
                  >
                    <div class="flex w-full items-center justify-between text-xs">
                      <span class="truncate font-semibold text-white">
                        {item.name}
                      </span>
                      <span class="text-lum-text-secondary text-[11px]">
                        {formatVisibilityLabel(
                          item.island.visibility,
                          item.island.condition
                        )}
                      </span>
                    </div>
                    <IslandPreview island={item.island} mode="interactive" />
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Step 2: Metadata */}
        <div class="lum-card lum-grad-bg-lum-card-bg/85 grid gap-4 border border-white/8 p-5 sm:grid-cols-2">
          <div class="sm:col-span-2">
            <h2 class="text-sm font-semibold text-white">
              2. Catalog details
            </h2>
          </div>

          <div class="flex flex-col gap-1.5">
            <label for="preset-name" class="text-xs font-medium text-white/85">
              Title
            </label>
            <input
              id="preset-name"
              name="name"
              type="text"
              maxLength={48}
              required
              placeholder="e.g. Spotify Compact Waveform"
              class="lum-input rounded-lum-1 text-sm"
              value={name.value}
              onInput$={(_, el) => {
                name.value = el.value;
              }}
            />
          </div>

          <div class="flex flex-col gap-1.5">
            <label
              for="preset-author"
              class="text-xs font-medium text-white/85"
            >
              Author
            </label>
            <input
              id="preset-author"
              name="author"
              type="text"
              maxLength={40}
              required
              placeholder="Your name or handle"
              class="lum-input rounded-lum-1 text-sm"
              value={author.value}
              onInput$={(_, el) => {
                author.value = el.value;
              }}
            />
          </div>

          <div class="flex flex-col gap-1.5 sm:col-span-2">
            <label
              for="preset-description"
              class="text-xs font-medium text-white/85"
            >
              Description{' '}
              <span class="text-lum-text-secondary font-normal">(optional)</span>
            </label>
            <input
              id="preset-description"
              name="description"
              type="text"
              maxLength={160}
              placeholder="Short note on when this island triggers and what it shows"
              class="lum-input rounded-lum-1 text-sm"
              value={description.value}
              onInput$={(_, el) => {
                description.value = el.value;
              }}
            />
          </div>

          {decoded.value?.appPackage && (
            <div class="flex flex-col gap-1.5 sm:col-span-2">
              <label
                for="preset-app-name"
                class="text-xs font-medium text-white/85"
              >
                App display name{' '}
                <span class="text-lum-text-secondary font-mono font-normal">
                  ({decoded.value.appPackage})
                </span>
              </label>
              <input
                id="preset-app-name"
                name="appName"
                type="text"
                maxLength={64}
                placeholder="e.g. Spotify"
                class="lum-input rounded-lum-1 text-sm"
                value={appName.value}
                onInput$={(_, el) => {
                  appName.value = el.value;
                }}
              />
            </div>
          )}

          <div class="flex items-center justify-end gap-3 pt-2 sm:col-span-2">
            <Link
              href="/presets"
              class="lum-btn lum-bg-transparent rounded-lum-1 text-sm"
            >
              Cancel
            </Link>
            <button
              type="submit"
              disabled={!decoded.value || action.isRunning}
              class="lum-btn lum-grad-bg-lum-accent rounded-lum-1 px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-45"
            >
              <Upload size={16} />
              {action.isRunning ? 'Publishing...' : 'Publish to Catalog'}
            </button>
          </div>
        </div>
      </Form>
    </section>
  );
});

export const head: DocumentHead = {
  title: 'Submit Preset — LarpDot',
  meta: [
    {
      name: 'description',
      content:
        'Publish a LarpDot Custom Island preset or multi-island app bundle to the community catalog.',
    },
  ],
};

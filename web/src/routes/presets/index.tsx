import { $, component$, useSignal } from '@qwik.dev/core';
import {
  Link,
  routeLoader$,
  useNavigate,
  type DocumentHead,
} from '@qwik.dev/router';
import {
  and,
  asc,
  count,
  desc,
  eq,
  isNull,
  like,
  or,
  sql,
  type SQL,
} from 'drizzle-orm';
import ChevronLeft from 'lucide-icons-qwik/icons/ChevronLeft';
import ChevronRight from 'lucide-icons-qwik/icons/ChevronRight';
import Layers from 'lucide-icons-qwik/icons/Layers';
import Package from 'lucide-icons-qwik/icons/Package';
import Search from 'lucide-icons-qwik/icons/Search';
import SearchCode from 'lucide-icons-qwik/icons/SearchCode';
import Sparkles from 'lucide-icons-qwik/icons/Sparkles';
import Upload from 'lucide-icons-qwik/icons/Upload';
import { PresetCard } from '~/components/PresetCard';
import { getDB, presets, type CatalogPreset } from '~/util/db';
import { formatAppDisplayName } from '~/util/presets';

interface AppCategoryOption {
  key: string;
  label: string;
  total: number;
}

export const useCatalogData = routeLoader$(async ({ query }) => {
  const search = (query.get('search') ?? '').trim();
  const app = (query.get('app') ?? '').trim();
  const kind = (query.get('kind') ?? 'all').trim();
  const sortBy = (query.get('sortBy') ?? 'createdAt').trim();
  const sortOrder = (query.get('sortOrder') ?? 'desc').trim();
  const page = Math.max(1, Number.parseInt(query.get('page') ?? '1', 10) || 1);
  const perPage = Math.min(
    48,
    Math.max(6, Number.parseInt(query.get('perPage') ?? '12', 10) || 12)
  );

  try {
    const db = getDB();
    const conditions: SQL[] = [];

    if (search) {
      const pattern = `%${search}%`;
      const searchCond = or(
        like(presets.name, pattern),
        like(presets.description, pattern),
        like(presets.author, pattern),
        like(presets.appName, pattern),
        like(presets.appPackage, pattern)
      );
      if (searchCond) conditions.push(searchCond);
    }

    if (app === '__general__') {
      conditions.push(isNull(presets.appPackage));
    } else if (app) {
      conditions.push(eq(presets.appPackage, app));
    }

    if (kind === 'single') {
      conditions.push(eq(presets.isBundle, false));
    } else if (kind === 'bundle') {
      conditions.push(eq(presets.isBundle, true));
    }

    const whereClause = conditions.length > 0 ? and(...conditions) : undefined;

    const orderColumn =
      sortBy === 'copies'
        ? presets.copies
        : sortBy === 'name'
          ? presets.name
          : presets.createdAt;
    const orderExpr = sortOrder === 'asc' ? asc(orderColumn) : desc(orderColumn);

    const [items, totalRow, appRows] = await Promise.all([
      db
        .select()
        .from(presets)
        .where(whereClause)
        .orderBy(orderExpr, desc(presets.id))
        .limit(perPage)
        .offset((page - 1) * perPage)
        .all(),
      db
        .select({ total: count() })
        .from(presets)
        .where(whereClause)
        .get(),
      db
        .select({
          appPackage: presets.appPackage,
          appName: sql<string | null>`max(${presets.appName})`,
          total: count(),
        })
        .from(presets)
        .groupBy(presets.appPackage)
        .orderBy(desc(count()))
        .limit(24)
        .all(),
    ]);

    const appCategories: AppCategoryOption[] = appRows.map((row) => ({
      key: row.appPackage ?? '__general__',
      label: row.appPackage
        ? (formatAppDisplayName(row.appPackage, row.appName) ?? row.appPackage)
        : 'General',
      total: Number(row.total),
    }));

    return {
      items,
      totalCount: Number(totalRow?.total ?? 0),
      appCategories,
      search,
      app,
      kind,
      sortBy,
      sortOrder,
      page,
      perPage,
      dbError: null as string | null,
    };
  } catch (err) {
    return {
      items: [] as CatalogPreset[],
      totalCount: 0,
      appCategories: [] as AppCategoryOption[],
      search,
      app,
      kind,
      sortBy,
      sortOrder,
      page,
      perPage,
      dbError: err instanceof Error ? err.message : 'Database unavailable',
    };
  }
});

export default component$(() => {
  const nav = useNavigate();
  const data = useCatalogData().value;
  const searchTimer = useSignal<number | undefined>(undefined);

  const totalPages = Math.max(1, Math.ceil(data.totalCount / data.perPage));

  const updateURL = $((updates: Record<string, string | number>) => {
    const url = new URL(window.location.href);
    Object.entries(updates).forEach(([key, value]) => {
      if (
        value === '' ||
        (key === 'kind' && value === 'all') ||
        (key === 'page' && Number(value) <= 1)
      ) {
        url.searchParams.delete(key);
      } else {
        url.searchParams.set(key, String(value));
      }
    });
    void nav(url.pathname + url.search);
  });

  const onSearchInput = $((value: string) => {
    if (searchTimer.value) window.clearTimeout(searchTimer.value);
    searchTimer.value = window.setTimeout(() => {
      void updateURL({ search: value, page: 1 });
    }, 280);
  });

  return (
    <section class="mx-auto max-w-6xl px-4 pt-10 pb-12 sm:px-6">
      <div class="mb-6 flex flex-col justify-between gap-4 pb-6 sm:flex-row sm:items-end">
        <div>
          <h1 class="flex items-center gap-2.5 text-2xl font-extrabold tracking-tight sm:text-3xl">
            <Layers size={28} class="text-lum-accent" />
            Custom Island Presets
          </h1>
          <p class="text-lum-text-secondary mt-1.5 text-sm sm:text-base">
            Browse single islands and full per-app bundles created for LarpDot.
            Copy any <code class="text-lum-accent">LARPDOT1:</code> code to
            import directly on your phone.
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <Link
            href="/presets/inspect"
            class="lum-btn px-3.5 py-2.5 text-sm font-medium"
          >
            <SearchCode size={16} />
            Inspect Code
          </Link>
          <Link
            href="/presets/submit"
            class="lum-btn lum-bg-lum-accent w-fit shrink-0 px-4 py-2.5 text-sm"
          >
            <Upload size={16} />
            Submit Preset or Bundle
          </Link>
        </div>
      </div>

      {/* Filter Toolbar */}
      <div class="lum-card mb-5 gap-3 p-3">
        <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
          <div class="relative flex flex-1 items-center">
            <Search
              size={16}
              class="text-lum-text-secondary pointer-events-none absolute left-3"
            />
            <input
              type="search"
              class="lum-input w-full py-2 pr-3 pl-9 text-sm"
              placeholder="Search presets, apps, package names, or authors..."
              value={data.search}
              onInput$={(_, el) => void onSearchInput(el.value)}
            />
          </div>

          <div class="flex flex-wrap items-center gap-2">
            <div class="inline-flex rounded-xl bg-lum-card-bg p-1 text-xs">
              {(
                [
                  { id: 'all', label: 'All' },
                  { id: 'single', label: 'Single' },
                  { id: 'bundle', label: 'Bundles' },
                ] as const
              ).map((option) => (
                <button
                  key={option.id}
                  type="button"
                  onClick$={() => void updateURL({ kind: option.id, page: 1 })}
                  class={{
                    'rounded-lg px-3 py-1.5 font-medium transition-colors': true,
                    'bg-lum-accent text-white': data.kind === option.id,
                    'text-lum-text-secondary hover:text-lum-text':
                      data.kind !== option.id,
                  }}
                >
                  {option.label}
                </button>
              ))}
            </div>

            <select
              aria-label="Sort presets"
              class="lum-input py-2 pr-8 pl-3 text-xs font-medium"
              value={`${data.sortBy}-${data.sortOrder}`}
              onChange$={(_, el) => {
                const [sortBy, sortOrder] = el.value.split('-');
                void updateURL({ sortBy, sortOrder, page: 1 });
              }}
            >
              <option value="createdAt-desc">Newest first</option>
              <option value="copies-desc">Most copied</option>
              <option value="name-asc">Name A–Z</option>
              <option value="name-desc">Name Z–A</option>
              <option value="createdAt-asc">Oldest first</option>
            </select>
          </div>
        </div>

        {/* App category chips */}
        {data.appCategories.length > 0 && (
          <div class="flex flex-wrap items-center gap-1.5 pt-2.5">
            <button
              type="button"
              onClick$={() => void updateURL({ app: '', page: 1 })}
              class={{
                'rounded-full px-3 py-1 text-xs font-medium transition-colors': true,
                'bg-lum-accent text-white': !data.app,
                'bg-lum-input-bg text-lum-text-secondary hover:bg-lum-input-hover-bg hover:text-lum-text':
                  Boolean(data.app),
              }}
            >
              All apps
            </button>
            {data.appCategories.map((cat) => (
              <button
                key={cat.key}
                type="button"
                onClick$={() => void updateURL({ app: cat.key, page: 1 })}
                class={{
                  'inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors': true,
                  'bg-lum-accent text-white': data.app === cat.key,
                  'bg-lum-input-bg text-lum-text-secondary hover:bg-lum-input-hover-bg hover:text-lum-text':
                    data.app !== cat.key,
                }}
              >
                <span>{cat.label}</span>
                <span class="opacity-65">{cat.total}</span>
              </button>
            ))}
          </div>
        )}
      </div>

      {data.dbError && (
        <div class="lum-card mb-6 bg-amber-950/35 p-4 text-sm text-amber-100">
          Catalog database is not migrated yet ({data.dbError}). Run{' '}
          <code class="font-mono">pnpm wrangler:migrate-local</code> for local
          development.
        </div>
      )}

      {/* Results count */}
      <div class="text-lum-text-secondary mb-4 flex items-center justify-between text-xs">
        <span>
          Showing <strong>{data.items.length}</strong> of{' '}
          <strong>{data.totalCount}</strong>{' '}
          {data.totalCount === 1 ? 'entry' : 'entries'}
        </span>
        {(data.search || data.app || data.kind !== 'all') && (
          <button
            type="button"
            class="text-lum-accent transition-colors hover:text-lum-accent"
            onClick$={() =>
              void updateURL({ search: '', app: '', kind: 'all', page: 1 })
            }
          >
            Clear filters
          </button>
        )}
      </div>

      {/* Grid */}
      {data.items.length > 0 ? (
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.items.map((preset) => (
            <PresetCard key={preset.id} preset={preset} />
          ))}
        </div>
      ) : (
        <div class="lum-card items-center justify-center gap-3 px-6 py-16 text-center">
          <div class="flex h-12 w-12 items-center justify-center rounded-2xl bg-lum-input-bg text-lum-accent">
            {data.kind === 'bundle' ? (
              <Package size={24} />
            ) : (
              <Sparkles size={24} />
            )}
          </div>
          <h2 class="text-lg font-bold">No presets found</h2>
          <p class="text-lum-text-secondary max-w-md text-sm">
            {data.search || data.app || data.kind !== 'all'
              ? 'Try clearing your filters or searching with different keywords.'
              : 'Be the first to share a LarpDot Custom Island preset or app bundle with the community.'}
          </p>
          <div class="mt-2 flex gap-2">
            {(data.search || data.app || data.kind !== 'all') && (
              <button
                type="button"
                class="lum-btn text-sm"
                onClick$={() =>
                  void updateURL({ search: '', app: '', kind: 'all', page: 1 })
                }
              >
                Reset filters
              </button>
            )}
            <Link
              href="/presets/submit"
              class="lum-btn lum-bg-lum-accent text-sm"
            >
              <Upload size={16} />
              Submit Preset
            </Link>
          </div>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div class="mt-8 flex items-center justify-center gap-2">
          <button
            type="button"
            disabled={data.page <= 1}
            onClick$={() => void updateURL({ page: data.page - 1 })}
            class="lum-btn text-xs disabled:opacity-40"
          >
            <ChevronLeft size={16} />
            Previous
          </button>
          <span class="text-lum-text-secondary px-3 text-xs">
            Page <strong>{data.page}</strong> of{' '}
            <strong>{totalPages}</strong>
          </span>
          <button
            type="button"
            disabled={data.page >= totalPages}
            onClick$={() => void updateURL({ page: data.page + 1 })}
            class="lum-btn text-xs disabled:opacity-40"
          >
            Next
            <ChevronRight size={16} />
          </button>
        </div>
      )}
    </section>
  );
});

export const head: DocumentHead = {
  title: 'Preset Catalog — LarpDot',
  meta: [
    {
      name: 'description',
      content:
        'Browse, preview, and copy community Custom Island presets and app bundles for LarpDot on Android.',
    },
  ],
};

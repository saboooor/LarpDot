import type { RequestHandler } from '@qwik.dev/router';
import {
  and,
  asc,
  count,
  desc,
  eq,
  isNull,
  like,
  or,
  type SQL,
} from 'drizzle-orm';
import { getDB, presets } from '~/util/db';

export const onGet: RequestHandler = async ({ query, json }) => {
  const search = (query.get('search') ?? '').trim();
  const app = (query.get('app') ?? '').trim();
  const kind = (query.get('kind') ?? 'all').trim();
  const sortBy = (query.get('sortBy') ?? 'createdAt').trim();
  const sortOrder = (query.get('sortOrder') ?? 'desc').trim();
  const page = Math.max(1, Number.parseInt(query.get('page') ?? '1', 10) || 1);
  const perPage = Math.min(
    50,
    Math.max(1, Number.parseInt(query.get('perPage') ?? '20', 10) || 20)
  );

  try {
    const db = getDB();
    const conditions: SQL[] = [];

    if (search) {
      const pattern = `%${search}%`;
      const cond = or(
        like(presets.name, pattern),
        like(presets.description, pattern),
        like(presets.author, pattern),
        like(presets.appName, pattern),
        like(presets.appPackage, pattern)
      );
      if (cond) conditions.push(cond);
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
    const orderCol =
      sortBy === 'copies'
        ? presets.copies
        : sortBy === 'name'
          ? presets.name
          : presets.createdAt;
    const orderExpr = sortOrder === 'asc' ? asc(orderCol) : desc(orderCol);

    const [items, totalRow] = await Promise.all([
      db
        .select()
        .from(presets)
        .where(whereClause)
        .orderBy(orderExpr, desc(presets.id))
        .limit(perPage)
        .offset((page - 1) * perPage)
        .all(),
      db.select({ total: count() }).from(presets).where(whereClause).get(),
    ]);

    json(200, {
      page,
      perPage,
      total: Number(totalRow?.total ?? 0),
      items,
    });
  } catch (err) {
    json(503, {
      error: err instanceof Error ? err.message : 'Database unavailable',
    });
  }
};

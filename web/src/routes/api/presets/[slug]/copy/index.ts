import type { RequestHandler } from '@qwik.dev/router';
import { eq, sql } from 'drizzle-orm';
import { getDB, presets } from '~/util/db';

export const onPost: RequestHandler = async ({ params, json }) => {
  const slug = params.slug?.trim();
  if (!slug) {
    json(400, { error: 'Missing preset slug' });
    return;
  }

  try {
    const db = getDB();
    const updated = await db
      .update(presets)
      .set({ copies: sql`${presets.copies} + 1` })
      .where(eq(presets.slug, slug))
      .returning({ slug: presets.slug, copies: presets.copies })
      .get();

    if (!updated) {
      json(404, { error: 'Preset not found' });
      return;
    }

    json(200, updated);
  } catch (err) {
    json(503, {
      error: err instanceof Error ? err.message : 'Database unavailable',
    });
  }
};

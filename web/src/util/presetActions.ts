import { server$ } from '@qwik.dev/router';
import { eq, sql } from 'drizzle-orm';
import { getDB, presets } from './db';

export const recordPresetCopy = server$(async function (presetId: number) {
  if (!Number.isInteger(presetId) || presetId <= 0) return null;
  const db = getDB();
  const updated = await db
    .update(presets)
    .set({ copies: sql`${presets.copies} + 1` })
    .where(eq(presets.id, presetId))
    .returning({ copies: presets.copies })
    .get();
  return updated?.copies ?? null;
});

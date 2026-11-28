import type { DrizzleD1Database } from 'drizzle-orm/d1';
import type * as DatabaseSchema from '../../drizzle/schema';

export * from '../../drizzle/schema';

export type AppDatabase = DrizzleD1Database<typeof DatabaseSchema>;

let _db: AppDatabase | undefined;

export function getDB(): AppDatabase {
  if (!_db) throw new Error('DB not initialized');
  return _db;
}

export async function initializeDbIfNeeded(
  factory: () => Promise<AppDatabase>
): Promise<void> {
  if (!_db) _db = await factory();
}

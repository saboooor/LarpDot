import { index, integer, sqliteTable, text } from 'drizzle-orm/sqlite-core';
import type { LarpDotShareEnvelope } from '../src/util/presets';

export const presets = sqliteTable(
  'presets',
  {
    id: integer('id').primaryKey({ autoIncrement: true }),
    slug: text('slug').notNull().unique(),
    name: text('name').notNull(),
    author: text('author').notNull(),
    description: text('description').notNull().default(''),
    isBundle: integer('is_bundle', { mode: 'boolean' }).notNull().default(false),
    appPackage: text('app_package'),
    appName: text('app_name'),
    islandCount: integer('island_count').notNull().default(1),
    visibility: text('visibility').notNull().default('IDLE'),
    condition: text('condition'),
    payload: text('payload', { mode: 'json' })
      .$type<LarpDotShareEnvelope>()
      .notNull(),
    shareCode: text('share_code').notNull(),
    copies: integer('copies').notNull().default(0),
    createdAt: integer('created_at', { mode: 'timestamp_ms' })
      .notNull()
      .$defaultFn(() => new Date()),
  },
  (table) => [
    index('presets_app_package_idx').on(table.appPackage),
    index('presets_is_bundle_idx').on(table.isBundle),
    index('presets_copies_idx').on(table.copies),
    index('presets_created_at_idx').on(table.createdAt),
  ]
);

export type CatalogPreset = typeof presets.$inferSelect;
export type NewCatalogPreset = typeof presets.$inferInsert;

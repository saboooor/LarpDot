CREATE TABLE `presets` (
	`id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`slug` text NOT NULL,
	`name` text NOT NULL,
	`author` text NOT NULL,
	`description` text DEFAULT '' NOT NULL,
	`is_bundle` integer DEFAULT false NOT NULL,
	`app_package` text,
	`app_name` text,
	`island_count` integer DEFAULT 1 NOT NULL,
	`visibility` text DEFAULT 'IDLE' NOT NULL,
	`condition` text,
	`payload` text NOT NULL,
	`share_code` text NOT NULL,
	`copies` integer DEFAULT 0 NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `presets_slug_unique` ON `presets` (`slug`);--> statement-breakpoint
CREATE INDEX `presets_app_package_idx` ON `presets` (`app_package`);--> statement-breakpoint
CREATE INDEX `presets_is_bundle_idx` ON `presets` (`is_bundle`);--> statement-breakpoint
CREATE INDEX `presets_copies_idx` ON `presets` (`copies`);--> statement-breakpoint
CREATE INDEX `presets_created_at_idx` ON `presets` (`created_at`);
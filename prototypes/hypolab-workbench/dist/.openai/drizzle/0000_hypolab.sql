CREATE TABLE `methods` (
  `id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
  `name` text NOT NULL,
  `category` text NOT NULL,
  `scenario` text NOT NULL,
  `steps` text DEFAULT '' NOT NULL,
  `metrics` text DEFAULT '' NOT NULL,
  `source` text DEFAULT '' NOT NULL,
  `created_at` text DEFAULT CURRENT_TIMESTAMP NOT NULL
);
--> statement-breakpoint
CREATE TABLE `hypotheses` (
  `id` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
  `title` text NOT NULL,
  `statement` text NOT NULL,
  `rationale` text NOT NULL,
  `technical_details` text NOT NULL,
  `methods` text NOT NULL,
  `datasets` text NOT NULL,
  `metrics` text NOT NULL,
  `novelty` integer NOT NULL,
  `consistency` integer NOT NULL,
  `testability` integer NOT NULL,
  `status` text DEFAULT '待验证' NOT NULL,
  `created_at` text DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE `hypotheses` ADD `cited_evidence_ids` text DEFAULT '[]' NOT NULL;
--> statement-breakpoint
ALTER TABLE `hypotheses` ADD `validation_plan` text DEFAULT '{}' NOT NULL;
--> statement-breakpoint
ALTER TABLE `hypotheses` ADD `feasibility` integer DEFAULT 0 NOT NULL;
--> statement-breakpoint
ALTER TABLE `hypotheses` ADD `confidence` integer DEFAULT 0 NOT NULL;

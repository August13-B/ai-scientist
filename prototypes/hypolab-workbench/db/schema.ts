import { sql } from "drizzle-orm";
import { integer, sqliteTable, text } from "drizzle-orm/sqlite-core";

export const methods = sqliteTable("methods", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  name: text("name").notNull(),
  category: text("category").notNull(),
  scenario: text("scenario").notNull(),
  steps: text("steps").notNull().default(""),
  metrics: text("metrics").notNull().default(""),
  source: text("source").notNull().default(""),
  createdAt: text("created_at").notNull().default(sql`CURRENT_TIMESTAMP`),
});

export const hypotheses = sqliteTable("hypotheses", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  title: text("title").notNull(),
  statement: text("statement").notNull(),
  rationale: text("rationale").notNull(),
  technicalDetails: text("technical_details").notNull(),
  methods: text("methods").notNull(),
  datasets: text("datasets").notNull(),
  metrics: text("metrics").notNull(),
  novelty: integer("novelty").notNull(),
  consistency: integer("consistency").notNull(),
  testability: integer("testability").notNull(),
  status: text("status").notNull().default("待验证"),
  createdAt: text("created_at").notNull().default(sql`CURRENT_TIMESTAMP`),
});

import { desc } from "drizzle-orm";
import { getDb } from "../../../db";
import { hypotheses } from "../../../db/schema";
import { ContractError, validateRequest } from "../../../lib/hypothesis-contract";
import { generateHypotheses } from "../../../lib/hypothesis-agent";

function hydrate(row: typeof hypotheses.$inferSelect) {
  return { ...row, citedEvidenceIds: JSON.parse(row.citedEvidenceIds), validationPlan: JSON.parse(row.validationPlan) };
}

export async function GET() {
  try {
    const rows = await getDb().select().from(hypotheses).orderBy(desc(hypotheses.id)).limit(50);
    return Response.json({ hypotheses: rows.map(hydrate) });
  } catch (error) {
    console.error("Failed to read hypotheses", error);
    return Response.json({ error: "HYPOTHESIS_READ_FAILED", message: "假设记录读取失败" }, { status: 500 });
  }
}

export async function POST(request: Request) {
  let input;
  try { input = validateRequest(await request.json()); }
  catch (error) { return Response.json({ error: "INVALID_REQUEST", message: error instanceof Error ? error.message : "请求格式错误" }, { status: 400 }); }

  let generated;
  try { generated = await generateHypotheses(input); }
  catch (error) {
    console.error("Hypothesis generation failed", error);
    if (error instanceof ContractError || error instanceof SyntaxError) return Response.json({ error: "INVALID_MODEL_OUTPUT", message: error.message }, { status: 422 });
    if (error instanceof Error && error.message === "GENERATOR_NOT_CONFIGURED") return Response.json({ error: "GENERATOR_NOT_CONFIGURED", message: "尚未配置团队假设接口或通义千问密钥" }, { status: 503 });
    return Response.json({ error: "AGENT_SERVICE_FAILED", message: "假设生成服务调用失败，请稍后重试" }, { status: 502 });
  }

  const rows = generated.map((item) => ({ title: item.title, statement: item.statement, rationale: item.rationale,
    citedEvidenceIds: JSON.stringify(item.citedEvidenceIds), validationPlan: JSON.stringify(item.validationPlan),
    technicalDetails: item.validationPlan.falsificationCriteria, methods: item.validationPlan.method,
    datasets: item.validationPlan.dataset, metrics: item.validationPlan.metrics.join("、"), novelty: item.novelty,
    feasibility: item.feasibility, confidence: item.confidence, consistency: item.confidence,
    testability: item.feasibility, status: "待验证" }));
  try {
    const saved = await getDb().insert(hypotheses).values(rows).returning();
    return Response.json({ hypotheses: saved.map(hydrate) }, { status: 201 });
  } catch (error) {
    console.error("Failed to persist generated hypotheses", error);
    return Response.json({ error: "HYPOTHESIS_PERSIST_FAILED", message: "假设已生成，但数据库保存失败，未创建任何记录" }, { status: 500 });
  }
}

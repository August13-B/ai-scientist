export type EvidenceItem = {
  id: string;
  type: "paper" | "table" | "image" | "dataset" | "observation";
  title?: string;
  content: string;
  source?: string;
  reliability?: number;
  metadata?: Record<string, unknown>;
};

export type GenerateHypothesesRequest = {
  researchQuestion: string;
  researchGap: string;
  evidenceItems: EvidenceItem[];
  dataConditions: string;
  constraints: string[];
  candidateCount: number;
};

export type ValidationPlan = {
  method: string;
  variables: string[];
  dataset: string;
  metrics: string[];
  falsificationCriteria: string;
};

export type GeneratedHypothesis = {
  title: string;
  statement: string;
  rationale: string;
  citedEvidenceIds: string[];
  novelty: number;
  feasibility: number;
  confidence: number;
  validationPlan: ValidationPlan;
};

export class ContractError extends Error {}

export function validateRequest(value: unknown): GenerateHypothesesRequest {
  const input = value as Partial<GenerateHypothesesRequest>;
  if (!input || typeof input !== "object") throw new ContractError("请求内容必须是 JSON 对象");
  if (!input.researchQuestion?.trim()) throw new ContractError("科研问题不能为空");
  if (!input.researchGap?.trim()) throw new ContractError("ResearchGap 不能为空");
  if (!input.dataConditions?.trim()) throw new ContractError("数据条件不能为空");
  if (!Array.isArray(input.evidenceItems) || input.evidenceItems.length === 0) throw new ContractError("至少需要一条 EvidenceItem");
  const ids = new Set<string>();
  for (const evidence of input.evidenceItems) {
    if (!evidence?.id?.trim() || !evidence.content?.trim()) throw new ContractError("每条证据必须包含 id 和 content");
    if (ids.has(evidence.id)) throw new ContractError(`证据 ID 重复：${evidence.id}`);
    ids.add(evidence.id);
  }
  return {
    researchQuestion: input.researchQuestion.trim(),
    researchGap: input.researchGap.trim(),
    evidenceItems: input.evidenceItems,
    dataConditions: input.dataConditions.trim(),
    constraints: Array.isArray(input.constraints) ? input.constraints.filter(Boolean) : [],
    candidateCount: Math.min(5, Math.max(1, Number(input.candidateCount) || 3)),
  };
}

export function validateHypotheses(value: unknown, input: GenerateHypothesesRequest): GeneratedHypothesis[] {
  const source = value as { hypotheses?: unknown };
  if (!Array.isArray(source?.hypotheses) || source.hypotheses.length === 0) throw new ContractError("模型没有返回 hypotheses 数组");
  const allowedIds = new Set(input.evidenceItems.map((item) => item.id));
  return source.hypotheses.slice(0, input.candidateCount).map((raw, index) => {
    const item = raw as Partial<GeneratedHypothesis>;
    if (!item.title?.trim() || !item.statement?.trim() || !item.rationale?.trim()) throw new ContractError(`第 ${index + 1} 条假设缺少必要文本字段`);
    if (!Array.isArray(item.citedEvidenceIds) || item.citedEvidenceIds.length === 0) throw new ContractError(`第 ${index + 1} 条假设没有引用证据`);
    const invalidId = item.citedEvidenceIds.find((id) => !allowedIds.has(id));
    if (invalidId) throw new ContractError(`模型引用了不存在的证据 ID：${invalidId}`);
    const plan = item.validationPlan;
    if (!plan?.method?.trim() || !plan.dataset?.trim() || !plan.falsificationCriteria?.trim() || !Array.isArray(plan.variables) || !Array.isArray(plan.metrics)) throw new ContractError(`第 ${index + 1} 条假设的验证方案不完整`);
    const score = (name: string, number: unknown) => {
      if (typeof number !== "number" || number < 0 || number > 100) throw new ContractError(`${name} 必须是 0-100 的数字`);
      return Math.round(number);
    };
    return { title: item.title.trim(), statement: item.statement.trim(), rationale: item.rationale.trim(), citedEvidenceIds: item.citedEvidenceIds, novelty: score("创新性", item.novelty), feasibility: score("可行性", item.feasibility), confidence: score("置信度", item.confidence), validationPlan: plan };
  });
}

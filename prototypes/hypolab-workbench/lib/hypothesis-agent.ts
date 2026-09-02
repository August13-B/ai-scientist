import { GenerateHypothesesRequest, GeneratedHypothesis, validateHypotheses } from "./hypothesis-contract";

const OUTPUT_SCHEMA = `只返回 JSON：{"hypotheses":[{"title":"","statement":"","rationale":"","citedEvidenceIds":["E001"],"novelty":0,"feasibility":0,"confidence":0,"validationPlan":{"method":"","variables":[""],"dataset":"","metrics":[""],"falsificationCriteria":""}}]}。证据 ID 只能来自输入，不得虚构文献或证据。`;

export async function generateHypotheses(input: GenerateHypothesesRequest, fetcher: typeof fetch = fetch): Promise<GeneratedHypothesis[]> {
  const teamUrl = process.env.TEAM_HYPOTHESIS_API_URL?.trim();
  const qwenKey = process.env.QWEN_API_KEY?.trim() || process.env.DASHSCOPE_API_KEY?.trim();
  let response: Response;
  if (teamUrl) {
    response = await fetcher(teamUrl, { method: "POST", headers: { "Content-Type": "application/json", ...(process.env.TEAM_HYPOTHESIS_API_TOKEN ? { Authorization: `Bearer ${process.env.TEAM_HYPOTHESIS_API_TOKEN}` } : {}) }, body: JSON.stringify(input), signal: AbortSignal.timeout(60_000) });
  } else if (qwenKey) {
    response = await fetcher(process.env.QWEN_BASE_URL || "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", {
      method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${qwenKey}` }, signal: AbortSignal.timeout(60_000),
      body: JSON.stringify({ model: process.env.QWEN_MODEL || "qwen-plus", response_format: { type: "json_object" }, messages: [{ role: "system", content: `你是科研假设生成 Agent。根据结构化证据生成可证伪且有依据的科学假设。${OUTPUT_SCHEMA}` }, { role: "user", content: JSON.stringify(input) }], temperature: 0.5 }),
    });
  } else {
    throw new Error("GENERATOR_NOT_CONFIGURED");
  }
  if (!response.ok) throw new Error(`GENERATOR_HTTP_${response.status}`);
  const payload = await response.json() as Record<string, unknown>;
  const content = teamUrl ? payload : (payload.choices as Array<{ message?: { content?: string } }> | undefined)?.[0]?.message?.content;
  const decoded = typeof content === "string" ? JSON.parse(content) : content;
  return validateHypotheses(decoded, input);
}

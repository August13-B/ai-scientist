import assert from "node:assert/strict";
import test from "node:test";
import { validateHypotheses, validateRequest } from "../lib/hypothesis-contract.ts";

const cases = [
  { question: "睡眠节律变化能否提前预测大学生心理压力风险？", gap: "缺少纵向验证", evidence: { id: "E001", type: "paper" as const, content: "睡眠波动与压力评分相关" }, statement: "睡眠节律波动增大将先于压力评分上升", method: "时序交叉验证" },
  { question: "土壤含水率是否影响水稻产量？", gap: "缺少不同灌溉区间的因果比较", evidence: { id: "E101", type: "dataset" as const, content: "包含土壤含水率和水稻亩产量" }, statement: "拔节期适度含水率将提高水稻最终产量", method: "随机区组实验" },
];

test("不同科研问题生成不同假设并引用各自证据", () => {
  const results = cases.map((item) => {
    const input = validateRequest({ researchQuestion:item.question, researchGap:item.gap, evidenceItems:[item.evidence], dataConditions:"具备独立验证集", constraints:["必须可证伪"], candidateCount:1 });
    return validateHypotheses({ hypotheses:[{ title:item.statement, statement:item.statement, rationale:`依据 ${item.evidence.id}`, citedEvidenceIds:[item.evidence.id], novelty:80, feasibility:82, confidence:79, validationPlan:{ method:item.method, variables:["自变量","因变量"], dataset:"独立验证集", metrics:["效应量"], falsificationCriteria:"主要效应不显著则否定" } }] }, input)[0];
  });
  assert.notEqual(results[0].statement, results[1].statement);
  assert.deepEqual(results[0].citedEvidenceIds, ["E001"]);
  assert.deepEqual(results[1].citedEvidenceIds, ["E101"]);
});

test("拒绝模型虚构证据 ID", () => {
  const input = validateRequest({ researchQuestion:cases[0].question, researchGap:cases[0].gap, evidenceItems:[cases[0].evidence], dataConditions:"纵向数据", constraints:[], candidateCount:1 });
  assert.throws(() => validateHypotheses({ hypotheses:[{ title:"错误", statement:"错误引用", rationale:"无", citedEvidenceIds:["E999"], novelty:50, feasibility:50, confidence:50, validationPlan:{ method:"实验", variables:[], dataset:"数据", metrics:[], falsificationCriteria:"不显著" } }] }, input), /不存在的证据/);
});

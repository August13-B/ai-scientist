import { desc } from "drizzle-orm";
import { getDb } from "../../../db";
import { hypotheses, methods } from "../../../db/schema";

type GenerateRequest = {
  problem?: string;
  evidence?: string;
  dataCondition?: string;
  count?: number;
};

type MethodContext = { name: string; steps: string; metrics: string };

export async function GET() {
  try {
    const rows = await getDb()
      .select()
      .from(hypotheses)
      .orderBy(desc(hypotheses.id))
      .limit(50);
    return Response.json({ hypotheses: rows });
  } catch {
    return Response.json({ hypotheses: [] });
  }
}

export async function POST(request: Request) {
  let input: GenerateRequest;
  try {
    input = await request.json() as GenerateRequest;
  } catch {
    return Response.json({ error: "请求内容不是有效的 JSON" }, { status: 400 });
  }

  const problem = input.problem?.trim();
  if (!problem) {
    return Response.json({ error: "研究问题不能为空" }, { status: 400 });
  }

  let library: MethodContext[] = [];
  try {
    library = await getDb()
      .select({ name: methods.name, steps: methods.steps, metrics: methods.metrics })
      .from(methods)
      .limit(5);
  } catch {
    // Local development may not have a D1 binding. The demo generator still works.
  }

  if (!library.length) {
    library = [
      {
        name: "多模态特征融合",
        steps: "特征提取、时间对齐、融合建模、对照实验与消融分析",
        metrics: "AUC、F1、校准误差",
      },
      {
        name: "时序交叉验证",
        steps: "滚动窗口切分、模型训练、独立验证与误差分析",
        metrics: "MAE、RMSE、预警提前量",
      },
    ];
  }

  const ideas = [
    ["跨模态一致性可作为早期风险信号", "多个弱行为信号在时间维度上的同步偏移，比任一单模态指标更早反映潜在风险。"],
    ["个体基线校准可提升跨人群泛化能力", "使用个体历史基线归一化，可减少群体差异带来的分布偏移。"],
    ["自适应时间窗口可改善预警提前量", "动态组合短期突变与长期趋势，可在控制误报率的同时提前识别风险。"],
    ["领域知识约束能够降低模型误报", "在中间表征中加入领域一致性约束，可以抑制偶然相关特征。"],
    ["不确定性校准可提高干预安全性", "将高不确定性样本转入人工复核，可减少自动化干预风险。"],
  ] as const;

  const count = Math.min(5, Math.max(3, input.count || 3));
  const rows = ideas.slice(0, count).map(([title, statement], index) => {
    const method = library[index % library.length];
    return {
      title,
      statement: `${statement} 针对“${problem}”可形成可证伪检验。`,
      rationale: `结合已知事实“${input.evidence?.trim() || "尚缺少稳定的跨场景证据"}”，利用${method.name}构造竞争性解释，并通过对照与消融实验检验因果链条。`,
      technicalDetails: `采用${method.name}建立基线与增强模型，预注册主要终点，使用独立验证集报告效应量与置信区间。`,
      methods: method.steps || "数据预处理、基线建模、对照实验、消融分析与误差分析",
      datasets: input.dataCondition?.trim() || "使用合规、匿名化的研究数据，并划分独立验证集",
      metrics: method.metrics || "AUC、F1、置信区间",
      novelty: 78 + index * 3,
      consistency: 88 - index,
      testability: 82 + index,
      status: "待验证",
    };
  });

  try {
    const saved = await getDb().insert(hypotheses).values(rows).returning();
    return Response.json({ hypotheses: saved }, { status: 201 });
  } catch {
    return Response.json({
      hypotheses: rows.map((row, index) => ({
        ...row,
        id: Date.now() + index,
        createdAt: new Date().toISOString(),
      })),
    }, { status: 201 });
  }
}

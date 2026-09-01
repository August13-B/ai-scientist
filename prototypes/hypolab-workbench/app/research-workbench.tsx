"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";

type Method = {
  id: number;
  name: string;
  category: string;
  scenario: string;
  steps: string;
  metrics: string;
  source: string;
};

type Hypothesis = {
  id: number;
  title: string;
  statement: string;
  rationale: string;
  technicalDetails: string;
  methods: string;
  datasets: string;
  metrics: string;
  novelty: number;
  consistency: number;
  testability: number;
  status: string;
  createdAt: string;
};

const seedMethods: Method[] = [
  { id: 1, name: "时序交叉验证", category: "统计学习", scenario: "具有时间依赖的数据预测", steps: "按时间窗口切分训练集与验证集，滚动评估", metrics: "MAE、RMSE、R²", source: "Hyndman & Athanasopoulos" },
  { id: 2, name: "图神经网络", category: "深度学习", scenario: "节点关系显著的图结构数据", steps: "构图、消息传递、节点聚合、任务头训练", metrics: "AUC、F1、Hits@K", source: "Kipf & Welling, 2017" },
  { id: 3, name: "双重差分法", category: "因果推断", scenario: "政策或干预效果评估", steps: "确定处理组与对照组，检验平行趋势，估计交互项", metrics: "ATT、置信区间、安慰剂检验", source: "Card & Krueger, 1994" },
];

const emptyMethod = { name: "", category: "机器学习", scenario: "", steps: "", metrics: "", source: "" };

export function ResearchWorkbench() {
  const [active, setActive] = useState("工作台");
  const [methods, setMethods] = useState<Method[]>(seedMethods);
  const [hypotheses, setHypotheses] = useState<Hypothesis[]>([]);
  const [query, setQuery] = useState("");
  const [showMethodForm, setShowMethodForm] = useState(false);
  const [methodForm, setMethodForm] = useState(emptyMethod);
  const [problem, setProblem] = useState("如何利用多模态行为数据更早识别大学生心理压力风险？");
  const [evidence, setEvidence] = useState("已有研究表明睡眠节律、社交活动下降和语言情绪变化与压力水平有关，但单一模态模型的跨人群泛化能力有限。");
  const [dataCondition, setDataCondition] = useState("可获得匿名化的睡眠、运动、校园活动与周记文本数据，样本约 2,000 人，持续 12 周。");
  const [count, setCount] = useState(3);
  const [generating, setGenerating] = useState(false);
  const [toast, setToast] = useState("");

  useEffect(() => {
    fetch("/api/methods").then((r) => r.ok ? r.json() : null).then((d) => d?.methods?.length && setMethods(d.methods)).catch(() => undefined);
    fetch("/api/hypotheses").then((r) => r.ok ? r.json() : null).then((d) => d?.hypotheses && setHypotheses(d.hypotheses)).catch(() => undefined);
  }, []);

  const filteredMethods = useMemo(() => methods.filter((item) =>
    `${item.name}${item.category}${item.scenario}`.toLowerCase().includes(query.toLowerCase())), [methods, query]);

  function notify(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(""), 2400);
  }

  async function addMethod(event: FormEvent) {
    event.preventDefault();
    if (!methodForm.name.trim() || !methodForm.scenario.trim()) return;
    const fallback = { ...methodForm, id: Date.now() };
    try {
      const response = await fetch("/api/methods", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(methodForm) });
      const data = await response.json();
      setMethods((current) => [data.method ?? fallback, ...current]);
    } catch { setMethods((current) => [fallback, ...current]); }
    setMethodForm(emptyMethod);
    setShowMethodForm(false);
    notify("方法已加入知识库");
  }

  async function removeMethod(id: number) {
    setMethods((current) => current.filter((item) => item.id !== id));
    await fetch(`/api/methods?id=${id}`, { method: "DELETE" }).catch(() => undefined);
    notify("方法已移除");
  }

  async function generate() {
    if (!problem.trim()) return notify("请先填写研究问题");
    setGenerating(true);
    try {
      const response = await fetch("/api/hypotheses", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ problem, evidence, dataCondition, count, methodIds: methods.slice(0, 5).map((m) => m.id) }),
      });
      if (!response.ok) throw new Error("generate failed");
      const data = await response.json();
      setHypotheses((current) => [...data.hypotheses, ...current]);
      setActive("假设记录");
      notify(`已生成 ${data.hypotheses.length} 个候选假设`);
    } catch {
      const demo = buildDemoHypotheses(problem, dataCondition, methods, count);
      setHypotheses((current) => [...demo, ...current]);
      setActive("假设记录");
      notify("已使用本地推理模板生成候选假设");
    } finally { setGenerating(false); }
  }

  function exportReport(item: Hypothesis) {
    const body = `科学假设研究方案\n\n研究假设\n${item.statement}\n\n推理依据（Rationale）\n${item.rationale}\n\n技术细节（Technical Details）\n${item.technicalDetails}\n\n方法（Methods）\n${item.methods}\n\n数据条件\n${item.datasets}\n\n评价指标\n${item.metrics}\n\n质量评分\n创新性 ${item.novelty} / 逻辑自洽性 ${item.consistency} / 可验证性 ${item.testability}\n`;
    const blob = new Blob([body], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a"); a.href = url; a.download = `${item.title}.txt`; a.click(); URL.revokeObjectURL(url);
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">H</span><div><strong>HypoLab</strong><small>科研假设工作台</small></div></div>
        <nav aria-label="主导航">
          {["工作台", "方法知识库", "假设记录"].map((item, index) => (
            <button key={item} onClick={() => setActive(item)} className={active === item ? "active" : ""}><span>{["✦", "▦", "◷"][index]}</span>{item}</button>
          ))}
        </nav>
        <div className="sidebar-note"><span>V1.0</span><p>方法库已连接</p><small>{methods.length} 条方法 · {hypotheses.length} 条假设</small></div>
        <div className="profile"><span>黄</span><div><strong>黄晴昀</strong><small>研究员</small></div></div>
      </aside>

      <section className="content">
        <header className="topbar"><div><span className="eyebrow">AI SCIENTIST · RESEARCH OS</span><h1>{active}</h1></div><button className="ghost" onClick={() => notify("所有本地更改均已保存")}>● 系统正常</button></header>

        {active === "工作台" && <div className="workspace-grid">
          <section className="panel composer">
            <div className="section-heading"><div><span className="step">01</span><h2>描述研究上下文</h2></div><span className="status">草稿自动保存</span></div>
            <label>研究问题<textarea value={problem} onChange={(e) => setProblem(e.target.value)} rows={3} /></label>
            <label>已知事实与研究空白<textarea value={evidence} onChange={(e) => setEvidence(e.target.value)} rows={4} /></label>
            <label>可用数据与实验条件<textarea value={dataCondition} onChange={(e) => setDataCondition(e.target.value)} rows={3} /></label>
            <div className="generate-row"><div><span>候选数量</span><div className="count-picker">{[3,4,5].map((n) => <button key={n} onClick={() => setCount(n)} className={count === n ? "selected" : ""}>{n}</button>)}</div></div><button className="primary" onClick={generate} disabled={generating}>{generating ? "正在构建推理链…" : "生成科学假设 →"}</button></div>
          </section>
          <aside className="right-rail">
            <section className="panel insight"><span className="step">02</span><h2>方法增强</h2><p>系统将从方法库检索与你的研究问题最相关的验证路径。</p>
              <div className="method-stack">{methods.slice(0,3).map((m) => <div key={m.id}><span>{m.category.slice(0,1)}</span><div><strong>{m.name}</strong><small>{m.metrics}</small></div></div>)}</div>
              <button className="text-button" onClick={() => setActive("方法知识库")}>管理方法库 →</button>
            </section>
            <section className="panel quality"><span className="tiny-label">生成约束</span>{["必须给出可证伪陈述", "必须匹配具体实验方法", "禁止虚构参考文献"].map((x) => <p key={x}><b>✓</b>{x}</p>)}</section>
          </aside>
        </div>}

        {active === "方法知识库" && <section className="panel library">
          <div className="section-heading"><div><span className="step">KB</span><div><h2>科研方法知识库</h2><p>结构化沉淀可复用的统计、机器学习与实验方法</p></div></div><button className="primary compact" onClick={() => setShowMethodForm(true)}>＋ 新增方法</button></div>
          <div className="toolbar"><input aria-label="搜索方法" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="搜索名称、类别或适用场景…"/><span>{filteredMethods.length} 条记录</span></div>
          <div className="method-table"><div className="table-head"><span>方法</span><span>适用场景</span><span>评价指标</span><span>来源</span><span></span></div>{filteredMethods.map((m) => <div className="table-row" key={m.id}><span><strong>{m.name}</strong><small>{m.category}</small></span><span>{m.scenario}</span><span>{m.metrics}</span><span>{m.source}</span><button aria-label={`删除${m.name}`} onClick={() => removeMethod(m.id)}>×</button></div>)}</div>
        </section>}

        {active === "假设记录" && <section className="history">
          <div className="history-intro"><div><span className="eyebrow">STRUCTURED OUTPUT</span><h2>候选科学假设</h2><p>每条结果都包含推理链、验证方法与三维质量评分。</p></div><button className="primary compact" onClick={() => setActive("工作台")}>＋ 新建任务</button></div>
          {hypotheses.length === 0 ? <div className="empty"><span>◇</span><h3>还没有生成记录</h3><p>从工作台输入研究问题，生成第一组可验证假设。</p><button className="primary compact" onClick={() => setActive("工作台")}>开始生成</button></div> : hypotheses.map((h, index) => <article className="hypothesis-card" key={`${h.id}-${index}`}>
            <div className="card-index">H{String(index + 1).padStart(2,"0")}</div><div className="card-main"><div className="card-top"><div><span className="tag">{h.status}</span><h3>{h.title}</h3></div><button className="export" onClick={() => exportReport(h)}>导出报告</button></div><p className="statement">{h.statement}</p>
              <details open><summary>推理依据 · Rationale</summary><p>{h.rationale}</p></details><div className="two-col"><div><b>技术细节</b><p>{h.technicalDetails}</p></div><div><b>实验方法</b><p>{h.methods}</p></div></div>
              <div className="score-row">{[["创新性",h.novelty],["自洽性",h.consistency],["可验证性",h.testability]].map(([label,score]) => <div key={String(label)}><span>{label}<b>{score}</b></span><i><em style={{width:`${score}%`}} /></i></div>)}</div>
            </div></article>)}
        </section>}
      </section>

      {showMethodForm && <div className="modal-backdrop" onMouseDown={() => setShowMethodForm(false)}><form className="modal" onSubmit={addMethod} onMouseDown={(e) => e.stopPropagation()}><div className="modal-title"><div><span className="eyebrow">KNOWLEDGE ENTRY</span><h2>新增科研方法</h2></div><button type="button" onClick={() => setShowMethodForm(false)}>×</button></div><div className="form-grid"><label>方法名称<input required value={methodForm.name} onChange={(e) => setMethodForm({...methodForm,name:e.target.value})}/></label><label>方法类别<select value={methodForm.category} onChange={(e) => setMethodForm({...methodForm,category:e.target.value})}><option>机器学习</option><option>深度学习</option><option>统计学习</option><option>因果推断</option><option>实验范式</option></select></label><label className="wide">适用场景<textarea required rows={2} value={methodForm.scenario} onChange={(e) => setMethodForm({...methodForm,scenario:e.target.value})}/></label><label className="wide">实施步骤<textarea rows={3} value={methodForm.steps} onChange={(e) => setMethodForm({...methodForm,steps:e.target.value})}/></label><label>评价指标<input value={methodForm.metrics} onChange={(e) => setMethodForm({...methodForm,metrics:e.target.value})}/></label><label>论文来源<input value={methodForm.source} onChange={(e) => setMethodForm({...methodForm,source:e.target.value})}/></label></div><div className="modal-actions"><button type="button" className="ghost" onClick={() => setShowMethodForm(false)}>取消</button><button className="primary compact">保存到知识库</button></div></form></div>}
      {toast && <div className="toast">✓ {toast}</div>}
    </main>
  );
}

function buildDemoHypotheses(problem: string, data: string, methods: Method[], count: number): Hypothesis[] {
  const templates = [
    ["跨模态一致性可作为早期风险信号", "当多个弱行为信号在时间维度上同步偏移时，其联合表示比任一单模态指标更早、更稳定地反映潜在风险。"],
    ["个体基线校准能提升跨人群泛化能力", "以个体历史基线进行归一化，可减少人口属性与日常习惯差异造成的分布偏移，从而提升模型泛化表现。"],
    ["时间窗口自适应机制可改善预警提前量", "动态选择不同长度的观测窗口，能够同时捕获短期突变和长期趋势，在不显著增加误报率的情况下提前预警。"],
    ["可解释的中间表征有助于降低误报", "加入与领域知识一致的中间概念约束，可以抑制偶然相关特征，提高预测的稳定性与可解释性。"],
    ["不确定性估计可提高实际干预安全性", "对预测结果进行不确定性校准，并将高不确定样本转入人工复核，可降低自动化决策风险。"],
  ];
  return templates.slice(0, count).map(([title, statement], i) => ({ id: Date.now()+i, title, statement: `${statement} 研究问题：${problem}`, rationale: `基于现有事实与方法库中的${methods[i % methods.length]?.name ?? "对照实验"}，推导变量间的可检验关系，并通过消融实验排除替代解释。`, technicalDetails: `建立多模态特征表征，采用${methods[i % methods.length]?.name ?? "统计检验"}完成建模与稳健性验证；设置独立验证集并报告置信区间。`, methods: methods[i % methods.length]?.steps || "数据预处理、基线建模、对照实验、消融分析与误差分析。", datasets: data, metrics: methods[i % methods.length]?.metrics || "AUC、F1、校准误差", novelty: 78+i*3, consistency: 86-i, testability: 83+i, status: "待验证", createdAt: new Date().toISOString() }));
}

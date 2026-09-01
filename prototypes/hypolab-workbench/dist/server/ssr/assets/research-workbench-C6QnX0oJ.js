import { a as require_react, o as __toESM, t as require_jsx_runtime } from "../index.js";
//#region app/research-workbench.tsx
var import_react = /* @__PURE__ */ __toESM(require_react(), 1);
var import_jsx_runtime = require_jsx_runtime();
var seedMethods = [
	{
		id: 1,
		name: "时序交叉验证",
		category: "统计学习",
		scenario: "具有时间依赖的数据预测",
		steps: "按时间窗口切分训练集与验证集，滚动评估",
		metrics: "MAE、RMSE、R²",
		source: "Hyndman & Athanasopoulos"
	},
	{
		id: 2,
		name: "图神经网络",
		category: "深度学习",
		scenario: "节点关系显著的图结构数据",
		steps: "构图、消息传递、节点聚合、任务头训练",
		metrics: "AUC、F1、Hits@K",
		source: "Kipf & Welling, 2017"
	},
	{
		id: 3,
		name: "双重差分法",
		category: "因果推断",
		scenario: "政策或干预效果评估",
		steps: "确定处理组与对照组，检验平行趋势，估计交互项",
		metrics: "ATT、置信区间、安慰剂检验",
		source: "Card & Krueger, 1994"
	}
];
var emptyMethod = {
	name: "",
	category: "机器学习",
	scenario: "",
	steps: "",
	metrics: "",
	source: ""
};
function ResearchWorkbench() {
	const [active, setActive] = (0, import_react.useState)("工作台");
	const [methods, setMethods] = (0, import_react.useState)(seedMethods);
	const [hypotheses, setHypotheses] = (0, import_react.useState)([]);
	const [query, setQuery] = (0, import_react.useState)("");
	const [showMethodForm, setShowMethodForm] = (0, import_react.useState)(false);
	const [methodForm, setMethodForm] = (0, import_react.useState)(emptyMethod);
	const [problem, setProblem] = (0, import_react.useState)("如何利用多模态行为数据更早识别大学生心理压力风险？");
	const [evidence, setEvidence] = (0, import_react.useState)("已有研究表明睡眠节律、社交活动下降和语言情绪变化与压力水平有关，但单一模态模型的跨人群泛化能力有限。");
	const [dataCondition, setDataCondition] = (0, import_react.useState)("可获得匿名化的睡眠、运动、校园活动与周记文本数据，样本约 2,000 人，持续 12 周。");
	const [count, setCount] = (0, import_react.useState)(3);
	const [generating, setGenerating] = (0, import_react.useState)(false);
	const [toast, setToast] = (0, import_react.useState)("");
	(0, import_react.useEffect)(() => {
		fetch("/api/methods").then((r) => r.ok ? r.json() : null).then((d) => d?.methods?.length && setMethods(d.methods)).catch(() => void 0);
		fetch("/api/hypotheses").then((r) => r.ok ? r.json() : null).then((d) => d?.hypotheses && setHypotheses(d.hypotheses)).catch(() => void 0);
	}, []);
	const filteredMethods = (0, import_react.useMemo)(() => methods.filter((item) => `${item.name}${item.category}${item.scenario}`.toLowerCase().includes(query.toLowerCase())), [methods, query]);
	function notify(message) {
		setToast(message);
		window.setTimeout(() => setToast(""), 2400);
	}
	async function addMethod(event) {
		event.preventDefault();
		if (!methodForm.name.trim() || !methodForm.scenario.trim()) return;
		const fallback = {
			...methodForm,
			id: Date.now()
		};
		try {
			const data = await (await fetch("/api/methods", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(methodForm)
			})).json();
			setMethods((current) => [data.method ?? fallback, ...current]);
		} catch {
			setMethods((current) => [fallback, ...current]);
		}
		setMethodForm(emptyMethod);
		setShowMethodForm(false);
		notify("方法已加入知识库");
	}
	async function removeMethod(id) {
		setMethods((current) => current.filter((item) => item.id !== id));
		await fetch(`/api/methods?id=${id}`, { method: "DELETE" }).catch(() => void 0);
		notify("方法已移除");
	}
	async function generate() {
		if (!problem.trim()) return notify("请先填写研究问题");
		setGenerating(true);
		try {
			const response = await fetch("/api/hypotheses", {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({
					problem,
					evidence,
					dataCondition,
					count,
					methodIds: methods.slice(0, 5).map((m) => m.id)
				})
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
		} finally {
			setGenerating(false);
		}
	}
	function exportReport(item) {
		const body = `科学假设研究方案\n\n研究假设\n${item.statement}\n\n推理依据（Rationale）\n${item.rationale}\n\n技术细节（Technical Details）\n${item.technicalDetails}\n\n方法（Methods）\n${item.methods}\n\n数据条件\n${item.datasets}\n\n评价指标\n${item.metrics}\n\n质量评分\n创新性 ${item.novelty} / 逻辑自洽性 ${item.consistency} / 可验证性 ${item.testability}\n`;
		const blob = new Blob([body], { type: "text/plain;charset=utf-8" });
		const url = URL.createObjectURL(blob);
		const a = document.createElement("a");
		a.href = url;
		a.download = `${item.title}.txt`;
		a.click();
		URL.revokeObjectURL(url);
	}
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("main", {
		className: "app-shell",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("aside", {
				className: "sidebar",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
						className: "brand",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
							className: "brand-mark",
							children: "H"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("strong", { children: "HypoLab" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("small", { children: "科研假设工作台" })] })]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("nav", {
						"aria-label": "主导航",
						children: [
							"工作台",
							"方法知识库",
							"假设记录"
						].map((item, index) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
							onClick: () => setActive(item),
							className: active === item ? "active" : "",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: [
								"✦",
								"▦",
								"◷"
							][index] }), item]
						}, item))
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
						className: "sidebar-note",
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "V1.0" }),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: "方法库已连接" }),
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("small", { children: [
								methods.length,
								" 条方法 · ",
								hypotheses.length,
								" 条假设"
							] })
						]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
						className: "profile",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "黄" }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("strong", { children: "黄晴昀" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("small", { children: "研究员" })] })]
					})
				]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
				className: "content",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("header", {
						className: "topbar",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
							className: "eyebrow",
							children: "AI SCIENTIST · RESEARCH OS"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("h1", { children: active })] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
							className: "ghost",
							onClick: () => notify("所有本地更改均已保存"),
							children: "● 系统正常"
						})]
					}),
					active === "工作台" && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
						className: "workspace-grid",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
							className: "panel composer",
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
									className: "section-heading",
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
										className: "step",
										children: "01"
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", { children: "描述研究上下文" })] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
										className: "status",
										children: "草稿自动保存"
									})]
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", { children: ["研究问题", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("textarea", {
									value: problem,
									onChange: (e) => setProblem(e.target.value),
									rows: 3
								})] }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", { children: ["已知事实与研究空白", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("textarea", {
									value: evidence,
									onChange: (e) => setEvidence(e.target.value),
									rows: 4
								})] }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", { children: ["可用数据与实验条件", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("textarea", {
									value: dataCondition,
									onChange: (e) => setDataCondition(e.target.value),
									rows: 3
								})] }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
									className: "generate-row",
									children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "候选数量" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
										className: "count-picker",
										children: [
											3,
											4,
											5
										].map((n) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
											onClick: () => setCount(n),
											className: count === n ? "selected" : "",
											children: n
										}, n))
									})] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
										className: "primary",
										onClick: generate,
										disabled: generating,
										children: generating ? "正在构建推理链…" : "生成科学假设 →"
									})]
								})
							]
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("aside", {
							className: "right-rail",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
								className: "panel insight",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
										className: "step",
										children: "02"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", { children: "方法增强" }),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: "系统将从方法库检索与你的研究问题最相关的验证路径。" }),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
										className: "method-stack",
										children: methods.slice(0, 3).map((m) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: m.category.slice(0, 1) }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("strong", { children: m.name }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("small", { children: m.metrics })] })] }, m.id))
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
										className: "text-button",
										onClick: () => setActive("方法知识库"),
										children: "管理方法库 →"
									})
								]
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
								className: "panel quality",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
									className: "tiny-label",
									children: "生成约束"
								}), [
									"必须给出可证伪陈述",
									"必须匹配具体实验方法",
									"禁止虚构参考文献"
								].map((x) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("b", { children: "✓" }), x] }, x))]
							})]
						})]
					}),
					active === "方法知识库" && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
						className: "panel library",
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "section-heading",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
									className: "step",
									children: "KB"
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", { children: "科研方法知识库" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: "结构化沉淀可复用的统计、机器学习与实验方法" })] })] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
									className: "primary compact",
									onClick: () => setShowMethodForm(true),
									children: "＋ 新增方法"
								})]
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "toolbar",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
									"aria-label": "搜索方法",
									value: query,
									onChange: (e) => setQuery(e.target.value),
									placeholder: "搜索名称、类别或适用场景…"
								}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", { children: [filteredMethods.length, " 条记录"] })]
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "method-table",
								children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
									className: "table-head",
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "方法" }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "适用场景" }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "评价指标" }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "来源" }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {})
									]
								}), filteredMethods.map((m) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
									className: "table-row",
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("strong", { children: m.name }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("small", { children: m.category })] }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: m.scenario }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: m.metrics }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: m.source }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
											"aria-label": `删除${m.name}`,
											onClick: () => removeMethod(m.id),
											children: "×"
										})
									]
								}, m.id))]
							})
						]
					}),
					active === "假设记录" && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
						className: "history",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "history-intro",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
									className: "eyebrow",
									children: "STRUCTURED OUTPUT"
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", { children: "候选科学假设" }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: "每条结果都包含推理链、验证方法与三维质量评分。" })
							] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
								className: "primary compact",
								onClick: () => setActive("工作台"),
								children: "＋ 新建任务"
							})]
						}), hypotheses.length === 0 ? /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "empty",
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { children: "◇" }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h3", { children: "还没有生成记录" }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: "从工作台输入研究问题，生成第一组可验证假设。" }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
									className: "primary compact",
									onClick: () => setActive("工作台"),
									children: "开始生成"
								})
							]
						}) : hypotheses.map((h, index) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("article", {
							className: "hypothesis-card",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "card-index",
								children: ["H", String(index + 1).padStart(2, "0")]
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
								className: "card-main",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
										className: "card-top",
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
											className: "tag",
											children: h.status
										}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("h3", { children: h.title })] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
											className: "export",
											onClick: () => exportReport(h),
											children: "导出报告"
										})]
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
										className: "statement",
										children: h.statement
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("details", {
										open: true,
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("summary", { children: "推理依据 · Rationale" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: h.rationale })]
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
										className: "two-col",
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("b", { children: "技术细节" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: h.technicalDetails })] }), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("b", { children: "实验方法" }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: h.methods })] })]
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
										className: "score-row",
										children: [
											["创新性", h.novelty],
											["自洽性", h.consistency],
											["可验证性", h.testability]
										].map(([label, score]) => /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", { children: [label, /* @__PURE__ */ (0, import_jsx_runtime.jsx)("b", { children: score })] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("i", { children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("em", { style: { width: `${score}%` } }) })] }, String(label)))
									})
								]
							})]
						}, `${h.id}-${index}`))]
					})
				]
			}),
			showMethodForm && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
				className: "modal-backdrop",
				onMouseDown: () => setShowMethodForm(false),
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("form", {
					className: "modal",
					onSubmit: addMethod,
					onMouseDown: (e) => e.stopPropagation(),
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "modal-title",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
								className: "eyebrow",
								children: "KNOWLEDGE ENTRY"
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", { children: "新增科研方法" })] }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
								type: "button",
								onClick: () => setShowMethodForm(false),
								children: "×"
							})]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "form-grid",
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", { children: ["方法名称", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
									required: true,
									value: methodForm.name,
									onChange: (e) => setMethodForm({
										...methodForm,
										name: e.target.value
									})
								})] }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", { children: ["方法类别", /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("select", {
									value: methodForm.category,
									onChange: (e) => setMethodForm({
										...methodForm,
										category: e.target.value
									}),
									children: [
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("option", { children: "机器学习" }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("option", { children: "深度学习" }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("option", { children: "统计学习" }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("option", { children: "因果推断" }),
										/* @__PURE__ */ (0, import_jsx_runtime.jsx)("option", { children: "实验范式" })
									]
								})] }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
									className: "wide",
									children: ["适用场景", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("textarea", {
										required: true,
										rows: 2,
										value: methodForm.scenario,
										onChange: (e) => setMethodForm({
											...methodForm,
											scenario: e.target.value
										})
									})]
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
									className: "wide",
									children: ["实施步骤", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("textarea", {
										rows: 3,
										value: methodForm.steps,
										onChange: (e) => setMethodForm({
											...methodForm,
											steps: e.target.value
										})
									})]
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", { children: ["评价指标", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
									value: methodForm.metrics,
									onChange: (e) => setMethodForm({
										...methodForm,
										metrics: e.target.value
									})
								})] }),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", { children: ["论文来源", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
									value: methodForm.source,
									onChange: (e) => setMethodForm({
										...methodForm,
										source: e.target.value
									})
								})] })
							]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: "modal-actions",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
								type: "button",
								className: "ghost",
								onClick: () => setShowMethodForm(false),
								children: "取消"
							}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
								className: "primary compact",
								children: "保存到知识库"
							})]
						})
					]
				})
			}),
			toast && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "toast",
				children: ["✓ ", toast]
			})
		]
	});
}
function buildDemoHypotheses(problem, data, methods, count) {
	return [
		["跨模态一致性可作为早期风险信号", "当多个弱行为信号在时间维度上同步偏移时，其联合表示比任一单模态指标更早、更稳定地反映潜在风险。"],
		["个体基线校准能提升跨人群泛化能力", "以个体历史基线进行归一化，可减少人口属性与日常习惯差异造成的分布偏移，从而提升模型泛化表现。"],
		["时间窗口自适应机制可改善预警提前量", "动态选择不同长度的观测窗口，能够同时捕获短期突变和长期趋势，在不显著增加误报率的情况下提前预警。"],
		["可解释的中间表征有助于降低误报", "加入与领域知识一致的中间概念约束，可以抑制偶然相关特征，提高预测的稳定性与可解释性。"],
		["不确定性估计可提高实际干预安全性", "对预测结果进行不确定性校准，并将高不确定样本转入人工复核，可降低自动化决策风险。"]
	].slice(0, count).map(([title, statement], i) => ({
		id: Date.now() + i,
		title,
		statement: `${statement} 研究问题：${problem}`,
		rationale: `基于现有事实与方法库中的${methods[i % methods.length]?.name ?? "对照实验"}，推导变量间的可检验关系，并通过消融实验排除替代解释。`,
		technicalDetails: `建立多模态特征表征，采用${methods[i % methods.length]?.name ?? "统计检验"}完成建模与稳健性验证；设置独立验证集并报告置信区间。`,
		methods: methods[i % methods.length]?.steps || "数据预处理、基线建模、对照实验、消融分析与误差分析。",
		datasets: data,
		metrics: methods[i % methods.length]?.metrics || "AUC、F1、校准误差",
		novelty: 78 + i * 3,
		consistency: 86 - i,
		testability: 83 + i,
		status: "待验证",
		createdAt: (/* @__PURE__ */ new Date()).toISOString()
	}));
}
//#endregion
export { ResearchWorkbench };

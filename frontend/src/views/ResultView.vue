<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTask, getTaskReport } from '@/api/tasks'
import type { ResearchPlan, TaskState } from '@/types/task'
import { getCurrentTaskId, rememberCurrentTask } from '@/utils/currentTask'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const loadError = ref('')
const report = ref<ResearchPlan | null>(null)
const taskState = ref<TaskState | null>(null)

const dimensionNav = [
  { id: 'dimension-1', number: '01', label: '待研究问题', english: 'Problem' },
  { id: 'dimension-2', number: '02', label: '解决思路', english: 'Rationale' },
  { id: 'dimension-3', number: '03', label: '技术手段', english: 'Technology' },
  { id: 'dimension-4', number: '04', label: '数据集', english: 'Datasets' },
  { id: 'dimension-5', number: '05', label: '论文标题', english: 'Title' },
  { id: 'dimension-6', number: '06', label: '论文摘要', english: 'Abstract' },
  { id: 'dimension-7', number: '07', label: '方法论', english: 'Methods' },
  { id: 'dimension-8', number: '08', label: '实验设计', english: 'Experiments' },
  { id: 'dimension-9', number: '09', label: '预期结果', english: 'Results' },
  { id: 'dimension-10', number: '10', label: '参考论文', english: 'References' },
]

function parseTaskId(value: unknown): number | null {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

const taskId = ref<number | null>(parseTaskId(route.query.taskId) ?? getCurrentTaskId())
const hasTask = computed(() => taskId.value !== null)
const hasReport = computed(() => report.value !== null)
const taskQuestion = computed(() => taskState.value?.question?.trim() ?? '')
const bestRanking = computed(() => taskState.value?.evaluation?.rankings?.[0] ?? null)
const bestHypothesis = computed(() => {
  const hypotheses = taskState.value?.hypothesis?.hypotheses ?? []
  const summary = bestRanking.value?.summary
  return hypotheses.find(item => item.summary === summary) ?? hypotheses[0] ?? null
})
const scoreRows = computed(() => {
  const score = bestRanking.value
  if (!score) return []
  return [
    { label: '创新性', value: scoreAsPercent(score.innovation) },
    { label: '可行性', value: scoreAsPercent(score.feasibility) },
    { label: '引用可靠性', value: scoreAsPercent(score.citationReliability) },
    { label: '数据可获得性', value: scoreAsPercent(score.dataAvailability) },
  ]
})
const overallScore = computed(() => scoreAsPercent(bestRanking.value?.overall ?? 0))
const completedAgentCount = computed(() => {
  const state = taskState.value
  if (!state) return 0
  return [
    state.questionQuery,
    state.literature,
    state.knowledgeDiscovery,
    state.hypothesis,
    state.evaluation,
    state.experiment,
    state.debate,
    state.finalReport,
  ].filter(Boolean).length
})
const contentScale = computed(() => {
  const plan = report.value
  if (!plan) return 0
  const content = [
    plan.problemStatement,
    plan.rationale,
    plan.paperTitle,
    plan.paperAbstract,
    plan.results,
    ...plan.technicalDetails,
    ...plan.methods,
    ...(plan.datasets?.source ?? []),
    ...(plan.datasets?.target ?? []),
    ...(plan.experiments?.baselines ?? []),
    ...(plan.experiments?.metrics ?? []),
  ].filter(Boolean).join('')
  return content.length
})

function scoreAsPercent(value: number): number {
  return Math.round(Math.max(0, Math.min(1, value)) * 100)
}

function textOrFallback(value: string | null | undefined, fallback = '暂无内容'): string {
  return value?.trim() || fallback
}

async function loadReport() {
  if (taskId.value === null || loading.value) return

  loading.value = true
  loadError.value = ''
  try {
    const currentTaskId = taskId.value
    const [reportResult, stateResult] = await Promise.allSettled([
      getTaskReport(currentTaskId),
      getTask(currentTaskId),
    ])

    if (stateResult.status === 'fulfilled') {
      taskState.value = stateResult.value
      rememberCurrentTask(currentTaskId, stateResult.value.question)
    } else {
      taskState.value = null
    }

    if (reportResult.status === 'rejected') throw reportResult.reason
    report.value = reportResult.value.report
  } catch (error) {
    report.value = null
    loadError.value = error instanceof Error ? error.message : '读取研究报告失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function goHome() {
  void router.push('/home')
}

function goPipeline() {
  if (taskId.value === null) return
  void router.push({ path: '/pipeline', query: { taskId: String(taskId.value) } })
}

function scrollToSection(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function printReport() {
  if (taskId.value === null || !report.value) return
  ElMessage.info('正在打开 A4 打印预览页')
  void router.push({ path: '/report-print', query: { taskId: String(taskId.value) } })
}

function markdownList(items: string[] | undefined): string {
  return items?.length ? items.map((item) => `- ${item}`).join('\n') : '- 暂无内容'
}

function referenceKind(reference: string): string {
  const value = reference.toLowerCase()
  if (value.startsWith('doi:')) return 'DOI'
  if (value.startsWith('pmid:')) return 'PMID'
  if (value.startsWith('arxiv:')) return 'arXiv'
  if (value.includes('localdoc://')) return '本地证据'
  return '公开链接'
}

function referenceHref(reference: string): string | null {
  const value = reference.trim()
  if (/^doi:/i.test(value)) return `https://doi.org/${value.replace(/^doi:/i, '')}`
  if (/^pmid:/i.test(value)) return `https://pubmed.ncbi.nlm.nih.gov/${value.replace(/^pmid:/i, '')}/`
  if (/^arxiv:/i.test(value)) return `https://arxiv.org/abs/${value.replace(/^arxiv:/i, '')}`
  if (/^url:https?:\/\//i.test(value)) return value.replace(/^url:/i, '')
  if (/^https?:\/\//i.test(value)) return value
  return null
}

function exportMarkdown() {
  const plan = report.value
  if (!plan) return

  const markdown = [
    `# ${textOrFallback(plan.paperTitle, '科学假设与研究计划')}`,
    '',
    taskId.value ? `> 任务编号：TASK-${taskId.value}` : '',
    taskQuestion.value ? `> 初始科研问题：${taskQuestion.value}` : '',
    '',
    '## 摘要（Paper Abstract）', '', textOrFallback(plan.paperAbstract), '',
    '## 1. 待研究问题（Problem Statement）', '', textOrFallback(plan.problemStatement), '',
    '## 2. 解决思路（Rationale）', '', textOrFallback(plan.rationale), '',
    '## 3. 必要的技术手段（Technical Details）', '', markdownList(plan.technicalDetails), '',
    '## 4. 数据集（Datasets）', '', '### Source', markdownList(plan.datasets?.source), '',
    '### Target', markdownList(plan.datasets?.target), '',
    '## 5. 论文标题（Paper Title）', '', textOrFallback(plan.paperTitle), '',
    '## 6. 论文摘要（Paper Abstract）', '', textOrFallback(plan.paperAbstract), '',
    '## 7. 方法论（Methods）', '', markdownList(plan.methods), '',
    '## 8. 实验设计（Experiments）', '', '### Baselines', markdownList(plan.experiments?.baselines), '',
    '### Metrics', markdownList(plan.experiments?.metrics), '',
    '## 9. 预期结果（Results）', '', textOrFallback(plan.results), '',
    '## 10. 参考论文（References）', '', markdownList(plan.references), '',
  ].filter((line, index, lines) => line || index === 0 || lines[index - 1] !== '').join('\n')

  const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `TASK-${taskId.value ?? 'REPORT'}-科学假设与研究计划.md`
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  ElMessage.success('完整十维报告已导出')
}

watch(
  () => route.query.taskId,
  (value) => {
    const nextTaskId = parseTaskId(value) ?? getCurrentTaskId()
    if (nextTaskId === taskId.value) return
    taskId.value = nextTaskId
    report.value = null
    taskState.value = null
    loadError.value = ''
    void loadReport()
  },
)

onMounted(() => void loadReport())
</script>

<template>
  <div class="result-container" v-loading="loading">
    <div class="header-actions">
      <div>
        <span class="page-kicker">FINAL SCIENTIFIC SYNTHESIS</span>
        <h2>科学假设与研究计划 · 十维终稿</h2>
      </div>
      <div class="action-group">
        <el-button v-if="hasTask" plain @click="goPipeline">返回工作流</el-button>
        <el-button plain :disabled="!hasReport" @click="printReport">打印 / PDF</el-button>
        <el-button type="primary" :disabled="!hasReport" @click="exportMarkdown">导出完整报告</el-button>
      </div>
    </div>

    <el-result
      v-if="!hasTask"
      icon="info"
      title="尚未选择科研任务"
      sub-title="请先从研究启动台创建任务，或从工作流页面进入报告。"
      class="state-card"
    >
      <template #extra><el-button type="primary" @click="goHome">返回研究启动台</el-button></template>
    </el-result>

    <el-result
      v-else-if="loadError"
      icon="error"
      title="报告读取失败"
      :sub-title="loadError"
      class="state-card"
    >
      <template #extra><el-button type="primary" :loading="loading" @click="loadReport">重新加载</el-button></template>
    </el-result>

    <el-empty
      v-else-if="!report && !loading"
      description="研究报告尚未生成，AI 管线可能仍在执行或等待人工审阅。"
      class="state-card"
    >
      <div class="empty-actions">
        <el-button type="primary" plain @click="goPipeline">查看工作流状态</el-button>
        <el-button :loading="loading" @click="loadReport">刷新报告</el-button>
      </div>
    </el-empty>

    <article v-else-if="report" class="report-document">
      <header class="report-hero">
        <div class="hero-grid"></div>
        <div class="hero-orb hero-orb-one"></div>
        <div class="hero-orb hero-orb-two"></div>
        <div class="hero-content">
          <div class="hero-topline">
            <span class="document-code">TASK-{{ taskId }} / AI SCIENTIST REPORT</span>
            <span class="verified-badge"><i></i> 多智能体协作完成</span>
          </div>
          <h1>{{ textOrFallback(report.paperTitle, '未命名研究计划') }}</h1>
          <p v-if="taskQuestion" class="origin-question">
            <span>ORIGINAL QUESTION</span>{{ taskQuestion }}
          </p>
          <div class="hero-abstract">
            <span>EXECUTIVE ABSTRACT</span>
            <p>{{ textOrFallback(report.paperAbstract) }}</p>
          </div>
          <div class="hero-metrics">
            <div><strong>10</strong><span>研究维度</span></div>
            <div><strong>{{ completedAgentCount }}/8</strong><span>Agent 协作链</span></div>
            <div><strong>{{ report.references.length }}</strong><span>可核验引用</span></div>
            <div><strong>{{ overallScore || '—' }}</strong><span>综合评估 / 100</span></div>
            <div><strong>{{ contentScale.toLocaleString() }}</strong><span>内容字符</span></div>
          </div>
        </div>
      </header>

      <div class="pipeline-ribbon">
        <span>问题理解</span><i></i><span>文献证据</span><i></i><span>缺口发现</span><i></i>
        <span>假设推导</span><i></i><span>实验验证</span><i></i><span>报告综合</span>
      </div>

      <div class="report-layout">
        <aside class="report-nav">
          <div class="nav-title"><span>REPORT INDEX</span><strong>十维研究框架</strong></div>
          <button v-for="item in dimensionNav" :key="item.id" type="button" @click="scrollToSection(item.id)">
            <span>{{ item.number }}</span>
            <div><strong>{{ item.label }}</strong><small>{{ item.english }}</small></div>
          </button>
          <div class="trace-note">
            <span>TRACEABILITY</span>
            <p>内容由八个 Agent 逐阶段生成，参考文献经过白名单核验。</p>
          </div>
        </aside>

        <main class="report-content">
          <section id="dimension-1" class="dimension-section emphasis-section">
            <div class="section-heading">
              <span class="section-number">01</span>
              <div><small>PROBLEM STATEMENT</small><h2>待研究问题</h2></div>
              <el-tag type="danger" effect="plain">研究边界</el-tag>
            </div>
            <p class="long-form">{{ textOrFallback(report.problemStatement) }}</p>
            <div v-if="taskState?.questionQuery?.keyConcepts?.length" class="concept-strip">
              <span>关键概念</span>
              <em v-for="concept in taskState.questionQuery.keyConcepts" :key="concept">{{ concept }}</em>
            </div>
          </section>

          <section id="dimension-2" class="dimension-section">
            <div class="section-heading">
              <span class="section-number">02</span>
              <div><small>SCIENTIFIC RATIONALE</small><h2>解决思路与科学依据</h2></div>
              <el-tag type="primary" effect="plain">逻辑推导</el-tag>
            </div>
            <p class="long-form">{{ textOrFallback(report.rationale) }}</p>
            <div v-if="bestHypothesis?.reasoningChain?.length" class="reasoning-chain">
              <div v-for="(step, index) in bestHypothesis.reasoningChain" :key="index" class="reasoning-node">
                <span>{{ String(index + 1).padStart(2, '0') }}</span><p>{{ step }}</p>
              </div>
            </div>
          </section>

          <section id="dimension-3" class="dimension-section">
            <div class="section-heading">
              <span class="section-number">03</span>
              <div><small>TECHNICAL DETAILS</small><h2>必要的技术手段</h2></div>
              <el-tag type="success" effect="plain">工程可执行</el-tag>
            </div>
            <div class="technology-grid">
              <div v-for="(item, index) in report.technicalDetails" :key="index" class="technology-card">
                <span>{{ String(index + 1).padStart(2, '0') }}</span><p>{{ item }}</p>
              </div>
            </div>
          </section>

          <section id="dimension-4" class="dimension-section">
            <div class="section-heading">
              <span class="section-number">04</span>
              <div><small>DATASETS & DOMAIN SPLIT</small><h2>数据集与验证对象</h2></div>
              <el-tag type="warning" effect="plain">来源锁定</el-tag>
            </div>
            <div class="dataset-grid">
              <div class="dataset-panel source-panel">
                <div class="dataset-title"><span>S</span><div><small>SOURCE DOMAIN</small><strong>历史来源数据</strong></div></div>
                <ul><li v-for="(item, index) in report.datasets?.source" :key="index">{{ item }}</li></ul>
              </div>
              <div class="dataset-arrow"><span>DOMAIN<br>TRANSFER</span><i>→</i></div>
              <div class="dataset-panel target-panel">
                <div class="dataset-title"><span>T</span><div><small>TARGET DOMAIN</small><strong>目标域验证数据</strong></div></div>
                <ul><li v-for="(item, index) in report.datasets?.target" :key="index">{{ item }}</li></ul>
              </div>
            </div>
          </section>

          <section id="dimension-5" class="dimension-section title-section">
            <div class="section-heading">
              <span class="section-number">05</span>
              <div><small>PROPOSED PAPER TITLE</small><h2>论文拟题</h2></div>
            </div>
            <div class="proposed-title"><span>“</span><h3>{{ textOrFallback(report.paperTitle) }}</h3><span>”</span></div>
          </section>

          <section id="dimension-6" class="dimension-section abstract-section">
            <div class="section-heading">
              <span class="section-number">06</span>
              <div><small>PAPER ABSTRACT</small><h2>论文摘要</h2></div>
              <el-tag type="info" effect="plain">预期语态</el-tag>
            </div>
            <div class="abstract-paper"><span>ABSTRACT</span><p>{{ textOrFallback(report.paperAbstract) }}</p></div>
          </section>

          <section id="dimension-7" class="dimension-section">
            <div class="section-heading">
              <span class="section-number">07</span>
              <div><small>METHODOLOGY ROADMAP</small><h2>方法论与实施路径</h2></div>
              <el-tag type="success" effect="plain">顺序执行</el-tag>
            </div>
            <div class="method-timeline">
              <div v-for="(item, index) in report.methods" :key="index" class="method-step">
                <div class="timeline-mark"><span>{{ index + 1 }}</span><i></i></div>
                <div><small>PHASE {{ String(index + 1).padStart(2, '0') }}</small><p>{{ item }}</p></div>
              </div>
            </div>
          </section>

          <section id="dimension-8" class="dimension-section">
            <div class="section-heading">
              <span class="section-number">08</span>
              <div><small>EXPERIMENTAL PROTOCOL</small><h2>实验设计</h2></div>
              <el-tag type="primary" effect="plain">预注册方案</el-tag>
            </div>
            <div class="experiment-grid">
              <div class="experiment-column">
                <h3><span>A</span>对比基线 Baselines</h3>
                <div v-for="(item, index) in report.experiments?.baselines" :key="index" class="experiment-item">
                  <b>{{ String(index + 1).padStart(2, '0') }}</b><p>{{ item }}</p>
                </div>
              </div>
              <div class="experiment-column metrics-column">
                <h3><span>M</span>评估指标 Metrics</h3>
                <div v-for="(item, index) in report.experiments?.metrics" :key="index" class="experiment-item">
                  <b>{{ String(index + 1).padStart(2, '0') }}</b><p>{{ item }}</p>
                </div>
              </div>
            </div>
          </section>

          <section id="dimension-9" class="dimension-section results-section">
            <div class="section-heading">
              <span class="section-number">09</span>
              <div><small>EXPECTED RESULTS & DECISION RULES</small><h2>预期结果与验收标准</h2></div>
              <el-tag type="danger" effect="plain">可证伪</el-tag>
            </div>
            <div class="results-manifesto"><span>EXPECTED · NOT OBSERVED</span><p>{{ textOrFallback(report.results) }}</p></div>
            <div v-if="scoreRows.length" class="evaluation-board">
              <div class="overall-score"><strong>{{ overallScore }}</strong><span>综合评分</span><small>满分 100</small></div>
              <div class="score-bars">
                <div v-for="item in scoreRows" :key="item.label" class="score-row">
                  <div><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div>
                  <div class="score-track"><i :style="{ width: `${item.value}%` }"></i></div>
                </div>
              </div>
            </div>
          </section>

          <section id="dimension-10" class="dimension-section references-section">
            <div class="section-heading">
              <span class="section-number">10</span>
              <div><small>VERIFIED REFERENCES</small><h2>可核验参考论文</h2></div>
              <el-tag type="success" effect="dark">{{ report.references.length }} 条已入白名单</el-tag>
            </div>
            <div class="reference-list">
              <div v-for="(reference, index) in report.references" :key="reference" class="reference-card">
                <span class="reference-index">[{{ String(index + 1).padStart(2, '0') }}]</span>
                <div><el-tag size="small" type="success" effect="plain">{{ referenceKind(reference) }}</el-tag><code>{{ reference }}</code></div>
                <a v-if="referenceHref(reference)" :href="referenceHref(reference) ?? undefined" target="_blank" rel="noreferrer">核验来源 ↗</a>
              </div>
            </div>
          </section>
        </main>
      </div>

      <footer class="report-footer">
        <div><strong>AI SCIENTIST</strong><span>基于国产开源大模型 Qwen 的科学假设生成系统</span></div>
        <p>本报告为待验证研究计划，不构成已完成实验结论。</p>
      </footer>
    </article>
  </div>
</template>

<style scoped>
.result-container { min-height: 560px; padding-bottom: 50px; color: #182133; }
.header-actions { display: flex; justify-content: space-between; align-items: flex-end; gap: 20px; margin-bottom: 18px; }
.header-actions h2 { margin: 4px 0 0; color: #1f2937; font-size: 22px; }
.page-kicker { color: #409eff; font-size: 11px; font-weight: 800; letter-spacing: 2px; }
.action-group { display: flex; flex-shrink: 0; gap: 9px; }
.state-card { min-height: 380px; background: #fff; border: 1px solid #e5eaf2; border-radius: 12px; }
.empty-actions { display: flex; justify-content: center; gap: 10px; }
.report-document { overflow: hidden; border: 1px solid #dfe6f1; border-radius: 16px; background: #f7f9fc; box-shadow: 0 18px 55px rgba(26, 50, 86, .12); }
.report-hero { position: relative; overflow: hidden; padding: 54px 60px 38px; color: #fff; background: linear-gradient(132deg, #061b36 0%, #0a3d73 54%, #0878a9 100%); }
.hero-grid { position: absolute; inset: 0; opacity: .13; background-image: linear-gradient(rgba(255,255,255,.25) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.25) 1px, transparent 1px); background-size: 38px 38px; mask-image: linear-gradient(to bottom, #000, transparent); }
.hero-orb { position: absolute; border-radius: 50%; filter: blur(3px); }
.hero-orb-one { top: -160px; right: -110px; width: 410px; height: 410px; background: radial-gradient(circle, rgba(81, 220, 255, .42), rgba(81, 220, 255, 0) 68%); }
.hero-orb-two { bottom: -190px; left: 24%; width: 360px; height: 360px; background: radial-gradient(circle, rgba(122, 105, 255, .34), rgba(122, 105, 255, 0) 68%); }
.hero-content { position: relative; z-index: 1; }
.hero-topline { display: flex; justify-content: space-between; align-items: center; gap: 20px; margin-bottom: 32px; }
.document-code { color: #94c8ef; font-size: 11px; font-weight: 700; letter-spacing: 2px; }
.verified-badge { display: flex; align-items: center; gap: 8px; padding: 7px 12px; border: 1px solid rgba(138, 235, 211, .35); border-radius: 99px; background: rgba(24, 197, 153, .12); color: #b9faea; font-size: 12px; }
.verified-badge i { width: 7px; height: 7px; border-radius: 50%; background: #46e4bd; box-shadow: 0 0 12px #46e4bd; }
.report-hero h1 { max-width: 980px; margin: 0; font-size: clamp(30px, 4vw, 48px); line-height: 1.28; letter-spacing: -.8px; text-shadow: 0 8px 30px rgba(0,0,0,.18); }
.origin-question { max-width: 1000px; margin: 22px 0 0; color: #d9eafa; font-size: 14px; line-height: 1.75; }
.origin-question span { margin-right: 12px; color: #6edcff; font-size: 10px; font-weight: 800; letter-spacing: 1.5px; }
.hero-abstract { max-width: 1050px; margin-top: 30px; padding: 20px 22px; border: 1px solid rgba(255,255,255,.16); border-radius: 12px; background: rgba(255,255,255,.08); backdrop-filter: blur(12px); }
.hero-abstract > span { color: #63dbff; font-size: 10px; font-weight: 800; letter-spacing: 1.8px; }
.hero-abstract p { margin: 9px 0 0; color: #e8f2fb; font-size: 14px; line-height: 1.85; white-space: pre-line; }
.hero-metrics { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 1px; margin-top: 32px; border: 1px solid rgba(255,255,255,.12); border-radius: 11px; background: rgba(255,255,255,.12); overflow: hidden; }
.hero-metrics div { display: flex; flex-direction: column; gap: 4px; padding: 15px 18px; background: rgba(3, 25, 50, .45); }
.hero-metrics strong { font-size: 23px; }
.hero-metrics span { color: #a9cbe4; font-size: 11px; }
.pipeline-ribbon { display: flex; justify-content: center; align-items: center; gap: 13px; padding: 13px 24px; border-bottom: 1px solid #e3eaf3; background: #fff; color: #607087; font-size: 11px; font-weight: 700; letter-spacing: .4px; }
.pipeline-ribbon i { width: 28px; height: 1px; background: linear-gradient(90deg, #5b9dff, #55d9c1); }
.report-layout { display: grid; grid-template-columns: 215px minmax(0, 1fr); gap: 0; align-items: start; }
.report-nav { position: sticky; top: 0; padding: 28px 18px; border-right: 1px solid #e2e8f1; background: #f1f5fa; }
.nav-title { display: flex; flex-direction: column; gap: 4px; margin: 0 8px 15px; }
.nav-title span, .trace-note span { color: #409eff; font-size: 9px; font-weight: 800; letter-spacing: 1.6px; }
.nav-title strong { color: #26364b; font-size: 14px; }
.report-nav button { display: flex; align-items: center; width: 100%; gap: 10px; padding: 9px 10px; border: 0; border-radius: 8px; background: transparent; color: #5b6879; cursor: pointer; text-align: left; transition: .18s ease; }
.report-nav button:hover { color: #1677d2; background: #fff; transform: translateX(3px); box-shadow: 0 4px 13px rgba(49, 91, 137, .08); }
.report-nav button > span { color: #8db3d9; font-family: Georgia, serif; font-size: 12px; }
.report-nav button div { display: flex; flex-direction: column; gap: 1px; }
.report-nav button strong { font-size: 12px; }
.report-nav button small { color: #a0abb9; font-size: 8px; letter-spacing: .5px; }
.trace-note { margin: 21px 7px 0; padding: 14px; border: 1px solid #dbe9f8; border-radius: 9px; background: #eaf4ff; }
.trace-note p { margin: 7px 0 0; color: #718096; font-size: 10px; line-height: 1.6; }
.report-content { padding: 10px 34px 40px; background: #fff; }
.dimension-section { scroll-margin-top: 18px; padding: 38px 6px; border-bottom: 1px solid #e9edf3; }
.dimension-section:last-child { border-bottom: 0; }
.section-heading { display: flex; align-items: center; gap: 14px; margin-bottom: 22px; }
.section-heading .el-tag { margin-left: auto; }
.section-number { display: grid; flex: 0 0 48px; height: 48px; place-items: center; border-radius: 12px; background: linear-gradient(145deg, #0e78dc, #22a8dc); color: #fff; font-family: Georgia, serif; font-size: 16px; box-shadow: 0 7px 18px rgba(38, 146, 216, .23); }
.section-heading div { display: flex; flex-direction: column; gap: 2px; }
.section-heading small { color: #8494a8; font-size: 9px; font-weight: 800; letter-spacing: 1.5px; }
.section-heading h2 { margin: 0; color: #1f2e43; font-size: 22px; }
.long-form { margin: 0; color: #475569; font-size: 14px; line-height: 2; text-align: justify; white-space: pre-line; }
.emphasis-section .long-form { padding: 21px 23px; border-left: 4px solid #e65b66; border-radius: 0 10px 10px 0; background: linear-gradient(90deg, #fff7f7, #fff); }
.concept-strip { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 17px; }
.concept-strip > span { color: #8795a7; font-size: 11px; }
.concept-strip em { padding: 5px 10px; border: 1px solid #cfe5fb; border-radius: 99px; background: #f2f8ff; color: #3178b8; font-size: 11px; font-style: normal; }
.reasoning-chain { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; margin-top: 22px; }
.reasoning-node { min-height: 105px; padding: 15px; border: 1px solid #dce8f4; border-radius: 10px; background: #f8fbff; }
.reasoning-node span { color: #409eff; font-family: Georgia, serif; font-size: 19px; }
.reasoning-node p { margin: 8px 0 0; color: #5e6d80; font-size: 11px; line-height: 1.65; }
.technology-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.technology-card { display: grid; grid-template-columns: 36px 1fr; gap: 12px; padding: 18px; border: 1px solid #e2e9f2; border-radius: 11px; background: linear-gradient(145deg, #fff, #f7faff); transition: .2s ease; }
.technology-card:hover { border-color: #9ecdf5; transform: translateY(-2px); box-shadow: 0 10px 24px rgba(33, 98, 153, .09); }
.technology-card > span { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 9px; background: #e8f4ff; color: #2785d7; font-size: 11px; font-weight: 800; }
.technology-card p { margin: 0; color: #536174; font-size: 12px; line-height: 1.78; }
.dataset-grid { display: grid; grid-template-columns: minmax(0, 1fr) 70px minmax(0, 1fr); align-items: stretch; }
.dataset-panel { padding: 20px; border-radius: 12px; }
.source-panel { border: 1px solid #cfe4f8; background: #f3f9ff; }
.target-panel { border: 1px solid #cceee6; background: #f2fcf9; }
.dataset-title { display: flex; align-items: center; gap: 11px; margin-bottom: 15px; }
.dataset-title > span { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 10px; background: #248add; color: #fff; font-weight: 900; }
.target-panel .dataset-title > span { background: #22aa8a; }
.dataset-title div { display: flex; flex-direction: column; }
.dataset-title small { color: #7990a9; font-size: 8px; letter-spacing: 1px; }
.dataset-title strong { margin-top: 2px; color: #26374b; font-size: 13px; }
.dataset-panel ul { margin: 0; padding-left: 18px; color: #526277; font-size: 11px; line-height: 1.75; }
.dataset-panel li { margin-bottom: 9px; }
.dataset-arrow { display: flex; flex-direction: column; justify-content: center; align-items: center; color: #8ca0b5; text-align: center; }
.dataset-arrow span { font-size: 7px; font-weight: 800; letter-spacing: .8px; }
.dataset-arrow i { color: #35a9bd; font-size: 25px; font-style: normal; }
.title-section { background: radial-gradient(circle at 90% 10%, #edf8ff, transparent 38%); }
.proposed-title { display: flex; justify-content: center; gap: 14px; padding: 30px 28px; border: 1px solid #dbe7f2; border-radius: 13px; background: #f8fbfe; }
.proposed-title span { color: #79b7e7; font-family: Georgia, serif; font-size: 42px; line-height: .8; }
.proposed-title h3 { max-width: 760px; margin: 0; color: #163c63; font-family: Georgia, 'Microsoft YaHei', serif; font-size: 23px; line-height: 1.55; text-align: center; }
.abstract-paper { position: relative; padding: 26px 28px 26px 108px; border: 1px solid #e1e7ef; border-radius: 11px; background: #fbfcfe; }
.abstract-paper > span { position: absolute; top: 29px; left: 24px; color: #3b91d6; font-size: 10px; font-weight: 900; letter-spacing: 1.5px; }
.abstract-paper p { margin: 0; color: #4f5e70; font-size: 13px; line-height: 2; text-align: justify; white-space: pre-line; }
.method-timeline { display: flex; flex-direction: column; }
.method-step { display: grid; grid-template-columns: 48px 1fr; gap: 14px; min-height: 92px; }
.timeline-mark { display: flex; flex-direction: column; align-items: center; }
.timeline-mark span { display: grid; flex: 0 0 34px; width: 34px; place-items: center; border: 2px solid #55a9ea; border-radius: 50%; background: #fff; color: #2585d1; font-size: 11px; font-weight: 800; }
.timeline-mark i { flex: 1; width: 1px; background: linear-gradient(#8fc7ef, #dceaf5); }
.method-step:last-child .timeline-mark i { display: none; }
.method-step > div:last-child { padding: 3px 17px 20px; }
.method-step small { color: #3894dc; font-size: 8px; font-weight: 800; letter-spacing: 1.2px; }
.method-step p { margin: 7px 0 0; color: #536174; font-size: 12px; line-height: 1.8; }
.experiment-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.experiment-column { padding: 20px; border: 1px solid #dfe8f2; border-radius: 12px; background: #f9fbfe; }
.metrics-column { background: #f6fbfa; }
.experiment-column h3 { display: flex; align-items: center; gap: 9px; margin: 0 0 16px; color: #26364a; font-size: 13px; }
.experiment-column h3 span { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 8px; background: #2b8bd6; color: #fff; }
.metrics-column h3 span { background: #22a98a; }
.experiment-item { display: grid; grid-template-columns: 26px 1fr; gap: 9px; padding: 11px 0; border-top: 1px dashed #dbe4ed; }
.experiment-item b { color: #84a2be; font-family: Georgia, serif; font-size: 11px; }
.experiment-item p { margin: 0; color: #58677a; font-size: 11px; line-height: 1.7; }
.results-section { background: linear-gradient(155deg, transparent 60%, #f0f8ff); }
.results-manifesto { padding: 23px 25px; border: 1px solid #f2d8d8; border-radius: 12px; background: #fffafa; }
.results-manifesto > span { color: #d9505d; font-size: 9px; font-weight: 900; letter-spacing: 1.6px; }
.results-manifesto p { margin: 12px 0 0; color: #4c5a6c; font-size: 13px; line-height: 2; text-align: justify; white-space: pre-line; }
.evaluation-board { display: grid; grid-template-columns: 150px 1fr; gap: 24px; margin-top: 18px; padding: 20px; border-radius: 12px; background: #092847; color: #fff; }
.overall-score { display: flex; flex-direction: column; justify-content: center; align-items: center; border-right: 1px solid rgba(255,255,255,.15); }
.overall-score strong { color: #62ddff; font-family: Georgia, serif; font-size: 48px; line-height: 1; }
.overall-score span { margin-top: 6px; font-size: 12px; }
.overall-score small { margin-top: 3px; color: #86a5c0; font-size: 9px; }
.score-bars { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px 22px; }
.score-row > div:first-child { display: flex; justify-content: space-between; margin-bottom: 6px; color: #c6d7e6; font-size: 10px; }
.score-row strong { color: #fff; }
.score-track { overflow: hidden; height: 5px; border-radius: 5px; background: rgba(255,255,255,.13); }
.score-track i { display: block; height: 100%; border-radius: 5px; background: linear-gradient(90deg, #30a2ed, #56e1bd); }
.reference-list { display: flex; flex-direction: column; gap: 9px; }
.reference-card { display: grid; grid-template-columns: 48px minmax(0, 1fr) auto; align-items: center; gap: 10px; padding: 14px 16px; border: 1px solid #e0e7ef; border-radius: 9px; background: #fbfcfe; }
.reference-index { color: #7f94a9; font-family: Georgia, serif; font-size: 12px; }
.reference-card > div { display: flex; align-items: center; min-width: 0; gap: 9px; }
.reference-card code { overflow: hidden; color: #36516d; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.reference-card a { color: #2a8bd7; font-size: 10px; text-decoration: none; }
.report-footer { display: flex; justify-content: space-between; align-items: center; gap: 20px; padding: 24px 38px; border-top: 1px solid #e0e7ef; background: #eef3f8; }
.report-footer div { display: flex; flex-direction: column; }
.report-footer strong { color: #18344f; font-size: 12px; letter-spacing: 1.3px; }
.report-footer span, .report-footer p { margin: 3px 0 0; color: #8795a5; font-size: 9px; }

@media (max-width: 1050px) {
  .report-layout { grid-template-columns: 1fr; }
  .report-nav { position: static; display: grid; grid-template-columns: repeat(5, 1fr); gap: 6px; border-right: 0; border-bottom: 1px solid #e2e8f1; }
  .nav-title, .trace-note { display: none; }
  .hero-metrics { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 768px) {
  .header-actions { align-items: flex-start; flex-direction: column; }
  .action-group { width: 100%; flex-wrap: wrap; }
  .report-hero { padding: 35px 24px 28px; }
  .hero-topline { align-items: flex-start; flex-direction: column; margin-bottom: 22px; }
  .report-hero h1 { font-size: 27px; }
  .hero-metrics { grid-template-columns: repeat(2, 1fr); }
  .pipeline-ribbon { display: none; }
  .report-nav { grid-template-columns: repeat(2, 1fr); }
  .report-content { padding: 8px 18px 30px; }
  .technology-grid, .experiment-grid { grid-template-columns: 1fr; }
  .dataset-grid { grid-template-columns: 1fr; gap: 10px; }
  .dataset-arrow { flex-direction: row; gap: 8px; }
  .dataset-arrow i { transform: rotate(90deg); }
  .abstract-paper { padding: 62px 20px 22px; }
  .evaluation-board { grid-template-columns: 1fr; }
  .overall-score { padding-bottom: 16px; border-right: 0; border-bottom: 1px solid rgba(255,255,255,.15); }
  .score-bars { grid-template-columns: 1fr; }
  .reference-card { grid-template-columns: 38px 1fr; }
  .reference-card a { grid-column: 2; }
  .report-footer { align-items: flex-start; flex-direction: column; }
}
@media print {
  :global(.aside-menu), :global(.top-header), .header-actions, .report-nav, .pipeline-ribbon { display: none !important; }
  :global(.main-content) { padding: 0 !important; }
  .result-container { padding: 0; }
  .report-document { border: 0; box-shadow: none; }
  .report-layout { display: block; }
  .report-content { padding: 10px 24px; }
  .dimension-section { break-inside: avoid; }
  .report-hero { print-color-adjust: exact; -webkit-print-color-adjust: exact; }
}
</style>

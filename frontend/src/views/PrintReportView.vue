<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTask, getTaskReport } from '@/api/tasks'
import type { ResearchPlan } from '@/types/task'

const route = useRoute()
const router = useRouter()
const report = ref<ResearchPlan | null>(null)
const question = ref('')
const loading = ref(true)
const error = ref('')
const taskId = computed(() => Number(route.query.taskId) || null)

async function load() {
  if (!taskId.value) { error.value = '缺少有效任务编号'; loading.value = false; return }
  try {
    const [reportResult, state] = await Promise.all([getTaskReport(taskId.value), getTask(taskId.value)])
    report.value = reportResult.report
    question.value = state.question
    if (!report.value) error.value = '该任务尚未生成最终报告'
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '打印报告读取失败'
  } finally { loading.value = false }
}

function printPdf() {
  ElMessage.success('请在系统打印窗口中选择“另存为 PDF”')
  window.setTimeout(() => window.print(), 180)
}

function back() {
  void router.push({ path: '/result', query: taskId.value ? { taskId: String(taskId.value) } : {} })
}

onMounted(load)
</script>

<template>
  <div class="print-page" v-loading="loading">
    <div class="print-toolbar">
      <div><strong>A4 打印预览</strong><span>确认排版后调用系统打印，并选择“另存为 PDF”</span></div>
      <div><el-button @click="back">返回报告</el-button><el-button type="primary" :disabled="!report" @click="printPdf">打开系统打印 / 保存 PDF</el-button></div>
    </div>
    <el-result v-if="error" icon="warning" title="暂时无法打印" :sub-title="error"><template #extra><el-button @click="back">返回报告</el-button></template></el-result>
    <main v-else-if="report" class="paper">
      <header class="cover">
        <span>AI SCIENTIST · SCIENTIFIC RESEARCH PLAN</span>
        <h1>{{ report.paperTitle || '科学假设与研究计划' }}</h1>
        <p>{{ question }}</p>
        <div class="cover-meta"><b>TASK-{{ taskId }}</b><b>十维科研终稿</b><b>QWEN MULTI-AGENT</b></div>
      </header>
      <section><label>EXECUTIVE ABSTRACT</label><h2>摘要</h2><p>{{ report.paperAbstract }}</p></section>
      <section><label>01 · PROBLEM STATEMENT</label><h2>待研究问题</h2><p>{{ report.problemStatement }}</p></section>
      <section><label>02 · SCIENTIFIC RATIONALE</label><h2>解决思路与科学依据</h2><p>{{ report.rationale }}</p></section>
      <section><label>03 · TECHNICAL DETAILS</label><h2>必要的技术手段</h2><ol><li v-for="item in report.technicalDetails" :key="item">{{ item }}</li></ol></section>
      <section><label>04 · DATASETS</label><h2>数据集与验证对象</h2><div class="columns"><div><h3>Source Domain</h3><ul><li v-for="item in report.datasets?.source" :key="item">{{ item }}</li></ul></div><div><h3>Target Domain</h3><ul><li v-for="item in report.datasets?.target" :key="item">{{ item }}</li></ul></div></div></section>
      <section><label>05 · PROPOSED TITLE</label><h2>论文拟题</h2><blockquote>{{ report.paperTitle }}</blockquote></section>
      <section><label>06 · PAPER ABSTRACT</label><h2>论文摘要</h2><p>{{ report.paperAbstract }}</p></section>
      <section><label>07 · METHODOLOGY</label><h2>方法论与实施路径</h2><ol><li v-for="item in report.methods" :key="item">{{ item }}</li></ol></section>
      <section><label>08 · EXPERIMENTAL PROTOCOL</label><h2>实验设计</h2><div class="columns"><div><h3>Baselines</h3><ul><li v-for="item in report.experiments?.baselines" :key="item">{{ item }}</li></ul></div><div><h3>Metrics</h3><ul><li v-for="item in report.experiments?.metrics" :key="item">{{ item }}</li></ul></div></div></section>
      <section><label>09 · EXPECTED RESULTS</label><h2>预期结果与验收标准</h2><p>{{ report.results }}</p></section>
      <section><label>10 · VERIFIED REFERENCES</label><h2>参考论文</h2><ol class="references"><li v-for="item in report.references" :key="item">{{ item }}</li></ol></section>
      <footer>AI SCIENTIST · 本报告为待验证研究计划，不构成已完成实验结论。</footer>
    </main>
  </div>
</template>

<style scoped>
.print-page{min-height:100%;padding-bottom:40px}.print-toolbar{position:sticky;z-index:20;top:-24px;display:flex;align-items:center;justify-content:space-between;margin:-24px -24px 24px;padding:14px 24px;border-bottom:1px solid #dce5ec;background:rgba(255,255,255,.95);backdrop-filter:blur(12px)}.print-toolbar>div:first-child{display:flex;flex-direction:column}.print-toolbar strong{color:#203c55}.print-toolbar span{margin-top:3px;color:#8596a5;font-size:10px}.paper{width:794px;margin:auto;color:#172f44;background:white;box-shadow:0 15px 50px rgba(25,50,75,.13)}.cover{position:relative;min-height:340px;padding:65px 58px;color:white;background:radial-gradient(circle at 82% 25%,rgba(39,192,255,.27),transparent 24%),linear-gradient(130deg,#06192d,#084773);overflow:hidden}.cover::after{content:'';position:absolute;right:-90px;bottom:-150px;width:380px;height:380px;border:1px solid rgba(107,214,255,.25);border-radius:50%;box-shadow:0 0 0 35px rgba(107,214,255,.04),0 0 0 70px rgba(107,214,255,.025)}.cover>span,section label{font-size:8px;font-weight:800;letter-spacing:2px}.cover>span{color:#5ed2ff}.cover h1{position:relative;z-index:1;max-width:640px;margin:48px 0 18px;font-size:31px;line-height:1.35}.cover p{position:relative;z-index:1;max-width:640px;color:#b9d5e7;line-height:1.7}.cover-meta{position:absolute;z-index:1;left:58px;right:58px;bottom:42px;display:flex;gap:20px;padding-top:15px;border-top:1px solid rgba(255,255,255,.18)}.cover-meta b{color:#8bbbd7;font-size:8px;letter-spacing:1px}.paper section{padding:34px 54px;border-bottom:1px solid #e5ebf0;break-inside:avoid}.paper section label{color:#188fd9}.paper section h2{margin:7px 0 17px;font-size:21px}.paper section p{white-space:pre-wrap;color:#40576b;font-size:12px;line-height:1.95}.paper li{margin:9px 0;color:#40576b;font-size:11px;line-height:1.75}.columns{display:grid;grid-template-columns:1fr 1fr;gap:20px}.columns>div{padding:16px;border-radius:8px;background:#f4f8fb}.columns h3{margin:0 0 10px;color:#2978ac;font-size:11px;text-transform:uppercase}.columns ul{margin:0;padding-left:18px}blockquote{margin:12px 0;padding:20px;border-left:4px solid #289fe7;color:#174d71;background:#edf8ff;font-size:17px;font-weight:700}.references{word-break:break-all}.paper footer{padding:20px 54px;color:#8394a2;background:#f5f8fa;font-size:8px;letter-spacing:.6px}
@media print{.print-toolbar,:global(.aside-menu),:global(.top-header){display:none!important}:global(.main-content){padding:0!important;background:#fff!important}:global(.layout-container){height:auto!important}.print-page{padding:0}.paper{width:100%;box-shadow:none}.cover{-webkit-print-color-adjust:exact;print-color-adjust:exact}.paper section{padding:28px 45px}}
@media(max-width:900px){.paper{width:100%}.columns{grid-template-columns:1fr}.print-toolbar{top:0;margin:0 0 18px}.print-toolbar span{display:none}}
</style>

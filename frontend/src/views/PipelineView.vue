<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTask, getTaskTrace, interveneTask } from '@/api/tasks'
import { subscribeTaskEvents, type TaskStreamSubscription } from '@/api/sse'
import type { AgentTraceRecord, TaskState, TaskStreamEvent } from '@/types/task'
import { getCurrentTaskId, rememberCurrentTask } from '@/utils/currentTask'

type StageStatus = 'pending' | 'running' | 'success' | 'failed'
type LogLevel = 'normal' | 'success' | 'warning' | 'error'

interface StageDefinition {
  key: string
  label: string
}

interface TerminalLog {
  id: number
  text: string
  level: LogLevel
}

const STAGES: StageDefinition[] = [
  { key: 'UNDERSTANDING', label: '问题理解 Agent' },
  { key: 'LITERATURE', label: '文献检索 Agent' },
  { key: 'KNOWLEDGE', label: '知识发现 Agent' },
  { key: 'HYPOTHESIS', label: '假设生成 Agent' },
  { key: 'EVALUATION', label: '科学评估 Agent' },
  { key: 'EXPERIMENT', label: '实验设计 Agent' },
  { key: 'DEBATE', label: '思辨辩论 Agent' },
  { key: 'REPORT', label: '报告生成 Agent' },
]

const route = useRoute()
const router = useRouter()
const dialogVisible = ref(false)
const reviewComment = ref('')
const loading = ref(false)
const submittingReview = ref(false)
const streamConnected = ref(false)
const pipelineFinished = ref(false)
const pipelineFailed = ref(false)
const taskState = ref<TaskState | null>(null)
const terminalLogs = ref<TerminalLog[]>([])
const stageStatus = reactive<Record<string, StageStatus>>(
  Object.fromEntries(STAGES.map((stage) => [stage.key, 'pending'])) as Record<string, StageStatus>,
)

let logSequence = 0
let subscription: TaskStreamSubscription | null = null
let pollingTimer: number | null = null
const receivedEvents = new Set<string>()

function parseTaskId(value: unknown): number | null {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

const taskId = ref<number | null>(parseTaskId(route.query.taskId) ?? getCurrentTaskId())
const hasTask = computed(() => taskId.value !== null)
const hypotheses = computed(() => taskState.value?.hypothesis?.hypotheses ?? [])
const awaitingReview = computed(() => Boolean(
  taskState.value?.hypothesis
  && !taskState.value.evaluation
  && !taskState.value.humanFeedback
  && !pipelineFailed.value,
))

function appendLog(text: string, level: LogLevel = 'normal') {
  terminalLogs.value.push({ id: ++logSequence, text, level })
  if (terminalLogs.value.length > 120) terminalLogs.value.shift()
}

function payloadOf(data: unknown): Record<string, unknown> {
  return data !== null && typeof data === 'object' ? (data as Record<string, unknown>) : {}
}

function stageLabel(stage: string): string {
  return STAGES.find((item) => item.key === stage)?.label ?? stage
}

function syncTrace(trace: AgentTraceRecord[]) {
  for (const record of trace) {
    if (record.status === 'SUCCESS') stageStatus[record.stage] = 'success'
    if (record.status === 'FAILED') {
      stageStatus[record.stage] = 'failed'
      pipelineFailed.value = true
    }
  }
}

async function refreshTask(showError = true) {
  if (taskId.value === null || loading.value) return
  loading.value = true
  try {
    const [state, trace] = await Promise.all([
      getTask(taskId.value),
      getTaskTrace(taskId.value),
    ])
    taskState.value = state
    syncTrace(trace)
    pipelineFinished.value = state.finalReport !== null
    if (state.hypothesis && !state.evaluation && !state.humanFeedback && !pipelineFailed.value) {
      dialogVisible.value = true
    }
  } catch (error) {
    if (showError) {
      ElMessage.error(error instanceof Error ? error.message : '读取任务状态失败')
    }
  } finally {
    loading.value = false
  }
}

function handleStreamEvent(event: TaskStreamEvent) {
  const eventKey = `${event.type}:${event.rawData}`
  if (receivedEvents.has(eventKey)) return
  receivedEvents.add(eventKey)

  const payload = payloadOf(event.data)
  const stage = typeof payload.stage === 'string' ? payload.stage : ''
  const agent = typeof payload.agent === 'string' ? payload.agent : stageLabel(stage)

  switch (event.type) {
    case 'agent.start':
      if (stage) stageStatus[stage] = 'running'
      appendLog(`[${agent}] 开始执行 ${stageLabel(stage)}...`)
      break
    case 'agent.thinking':
      appendLog(`[${agent}] 正在推理...`)
      break
    case 'agent.result':
      if (stage) stageStatus[stage] = 'success'
      appendLog(`[${agent}] ${stageLabel(stage)}执行完成`, 'success')
      void refreshTask(false)
      break
    case 'pipeline.pause':
      dialogVisible.value = true
      appendLog('[System] 管线已暂停，等待人类专家审阅候选假设', 'warning')
      void refreshTask(false)
      break
    case 'pipeline.resume':
      dialogVisible.value = false
      appendLog('[System] 已提交审阅意见，管线继续执行', 'success')
      break
    case 'pipeline.done':
      pipelineFinished.value = true
      appendLog('[System] 全部 Agent 已完成，研究报告已生成', 'success')
      stopRealtimeUpdates()
      void refreshTask(false)
      break
    case 'pipeline.error': {
      pipelineFailed.value = true
      const message = typeof payload.message === 'string' ? payload.message : '管线执行失败'
      appendLog(`[Error] ${message}`, 'error')
      stopRealtimeUpdates()
      void refreshTask(false)
      break
    }
    default:
      appendLog(`[System] 收到事件：${event.type}`)
  }
}

function stopRealtimeUpdates() {
  subscription?.close()
  subscription = null
  streamConnected.value = false
  if (pollingTimer !== null) {
    window.clearInterval(pollingTimer)
    pollingTimer = null
  }
}

function connectStream() {
  if (taskId.value === null) return
  subscription?.close()
  subscription = subscribeTaskEvents(taskId.value, {
    onOpen: () => {
      streamConnected.value = true
      appendLog('[System] 已连接 Agent 实时事件流', 'success')
    },
    onEvent: handleStreamEvent,
    onError: (error) => {
      streamConnected.value = false
      appendLog(`[System] 实时事件流暂时断开：${error.message}`, 'warning')
    },
  })
}

async function confirmAndProceed() {
  if (taskId.value === null || submittingReview.value) return
  submittingReview.value = true
  try {
    await interveneTask(taskId.value, {
      reviewComment: reviewComment.value.trim() || '通过',
      revisedHypotheses: [],
    })
    dialogVisible.value = false
    await refreshTask(false)
    ElMessage.success('审阅意见已提交，AI 管线继续执行')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交审阅意见失败')
  } finally {
    submittingReview.value = false
  }
}

function goHome() {
  void router.push('/home')
}

function viewReport() {
  if (taskId.value === null) return
  void router.push({ path: '/result', query: { taskId: String(taskId.value) } })
}

function statusText(status: StageStatus): string {
  return { pending: '等待中', running: '执行中', success: '已完成', failed: '失败' }[status]
}

function statusTagType(status: StageStatus): 'info' | 'warning' | 'success' | 'danger' {
  const tagTypes: Record<StageStatus, 'info' | 'warning' | 'success' | 'danger'> = {
    pending: 'info',
    running: 'warning',
    success: 'success',
    failed: 'danger',
  }
  return tagTypes[status]
}

function stageState(stageKey: string): StageStatus {
  return stageStatus[stageKey] ?? 'pending'
}

onMounted(() => {
  if (taskId.value === null) return
  rememberCurrentTask(taskId.value)
  appendLog(`[System] 正在加载科研任务 TASK-${taskId.value}...`)
  void refreshTask()
  connectStream()
  pollingTimer = window.setInterval(() => void refreshTask(false), 5000)
})

onBeforeUnmount(() => {
  stopRealtimeUpdates()
})
</script>

<template>
  <el-row :gutter="20" class="pipeline-container">
    <el-col :span="14">
      <el-card class="flow-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>多智能体系统 (Multi-Agent Systems) 工作流状态</span>
            <el-tag v-if="hasTask" :type="streamConnected ? 'success' : 'info'" size="small">
              {{ streamConnected ? '实时连接' : '状态轮询' }}
            </el-tag>
          </div>
        </template>

        <div v-if="!hasTask" class="flow-placeholder">
          <p>尚未选择科研任务</p>
          <el-button type="primary" @click="goHome">返回研究启动台</el-button>
        </div>

        <div v-else class="flow-placeholder agent-flow">
          <div class="task-meta">
            <strong>TASK-{{ taskId }}</strong>
            <span>{{ taskState?.question ?? '正在读取科研问题...' }}</span>
          </div>
          <div class="agent-grid">
            <div
              v-for="(stage, index) in STAGES"
              :key="stage.key"
              class="agent-node"
              :class="`is-${stageState(stage.key)}`"
            >
              <span class="stage-index">{{ index + 1 }}</span>
              <span class="stage-name">{{ stage.label }}</span>
              <el-tag :type="statusTagType(stageState(stage.key))" size="small">
                {{ statusText(stageState(stage.key)) }}
              </el-tag>
            </div>
          </div>
          <div class="flow-actions">
            <el-button :loading="loading" @click="refreshTask()">刷新状态</el-button>
            <el-button v-if="awaitingReview" type="warning" @click="dialogVisible = true">
              审阅候选假设
            </el-button>
            <el-button v-if="pipelineFinished" type="success" @click="viewReport">查看最终报告</el-button>
          </div>
        </div>
      </el-card>
    </el-col>

    <el-col :span="10">
      <el-card class="terminal-card" shadow="never">
        <template #header>
          <div class="card-header"><span>Agent 思维链日志 (Terminal)</span></div>
        </template>
        <div class="terminal-window">
          <div v-if="terminalLogs.length === 0" class="log-line">[System] 等待任务启动...</div>
          <div
            v-for="log in terminalLogs"
            :key="log.id"
            class="log-line"
            :class="`text-${log.level}`"
          >
            {{ log.text }}
          </div>
          <div v-if="hasTask && !pipelineFinished && !pipelineFailed" class="log-line">
            <span class="blinking-cursor">_</span>
          </div>
        </div>
      </el-card>
    </el-col>

    <el-dialog v-model="dialogVisible" title="🚨 人类专家介入请求：审阅初步假设" width="50%">
      <span>假设生成 Agent 已生成候选假设，请填写审阅意见后恢复管线。</span>
      <div v-if="hypotheses.length" class="hypothesis-summary">
        <p v-for="(item, index) in hypotheses" :key="index">
          <strong>候选 {{ index + 1 }}：</strong>{{ item.summary }}
        </p>
      </div>
      <el-input
        v-model="reviewComment"
        type="textarea"
        :rows="4"
        placeholder="可填写修改建议；直接确认则按原候选假设继续"
        style="margin-top: 15px"
      />
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">稍后处理</el-button>
          <el-button type="primary" :loading="submittingReview" @click="confirmAndProceed">
            确认意见，继续执行
          </el-button>
        </span>
      </template>
    </el-dialog>
  </el-row>
</template>

<style scoped>
.pipeline-container {
  height: 100%;
}
.flow-card, .terminal-card {
  height: calc(100vh - 120px);
  border-radius: 8px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.flow-placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #909399;
  background-color: #fafafa;
  border: 1px dashed #dcdfe6;
}
.agent-flow {
  box-sizing: border-box;
  justify-content: flex-start;
  align-items: stretch;
  padding: 18px;
  overflow: auto;
}
.task-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-bottom: 14px;
  color: #606266;
}
.task-meta span {
  font-size: 13px;
  line-height: 1.5;
}
.agent-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.agent-node {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  transition: border-color .2s, box-shadow .2s;
}
.agent-node.is-running { border-color: #e6a23c; box-shadow: 0 0 0 2px rgba(230, 162, 60, .12); }
.agent-node.is-success { border-color: #67c23a; }
.agent-node.is-failed { border-color: #f56c6c; }
.stage-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: #fff;
  background: #409eff;
  font-size: 12px;
}
.stage-name {
  flex: 1;
  color: #303133;
  font-size: 14px;
}
.flow-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 18px;
}
.terminal-window {
  box-sizing: border-box;
  background-color: #1e1e1e;
  color: #d4d4d4;
  height: 100%;
  padding: 16px;
  overflow: auto;
  font-family: 'Courier New', Courier, monospace;
  font-size: 14px;
  border-radius: 4px;
}
.log-line { margin-bottom: 8px; word-break: break-word; line-height: 1.5; }
.text-success { color: #67c23a; }
.text-warning { color: #e6a23c; }
.text-error { color: #f56c6c; }
.hypothesis-summary {
  max-height: 220px;
  margin-top: 14px;
  padding: 10px 14px;
  overflow: auto;
  color: #606266;
  background: #f5f7fa;
  border-radius: 6px;
}
.hypothesis-summary p { margin: 6px 0; line-height: 1.5; }
.blinking-cursor {
  animation: blink 1s step-end infinite;
}
@keyframes blink { 50% { opacity: 0; } }
</style>
